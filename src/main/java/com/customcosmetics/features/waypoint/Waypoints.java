// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import static com.customcosmetics.features.waypoint.WaypointData.*;

/** Command-only waypoint tool with bounded render work and asynchronous atomic persistence. */
public final class Waypoints {
    public static final int MAX_RENDERED = 256;
    private static final Marker[] EMPTY = new Marker[0];
    private static Snapshot data = Snapshot.empty();
    private static WaypointStore store;
    private static boolean initialized, dirtyScope = true;
    private static volatile String status = "";
    private static String scope = "";
    private static Object level;
    private static int tick, graceUntil;
    private static Marker[] markers = EMPTY, visible = EMPTY;
    private static int visibleCount;
    private static final Map<String, WaypointRoute> ROUTES = new HashMap<>();
    private static final AtomicReference<Snapshot> PENDING_SAVE = new AtomicReference<>();
    private static final AtomicBoolean SAVE_SCHEDULED = new AtomicBoolean();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CustomCosmetics-waypoint-save");
        thread.setDaemon(true);
        return thread;
    });
    private static final Comparator<Marker> DISTANCE = Comparator.comparingDouble(m -> m.distance);
    private Waypoints() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        store = new WaypointStore(FabricLoader.getInstance().getConfigDir().resolve("customcosmetics/waypoints.json"));
        try { data = store.load(); }
        catch (IOException failure) { status = failure.getMessage(); }
        WaypointWorld.init();
        ClientTickEvents.END_CLIENT_TICK.register(Waypoints::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(Waypoints::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WaypointWorld.reset(); scope = ""; level = null; ROUTES.clear(); markers = visible = EMPTY; visibleCount = 0; dirtyScope = true;
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WRITER.shutdown();
            try { WRITER.awaitTermination(3, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
    }
    public static Snapshot snapshot() { init(); return data; }
    public static String status() { return status; }
    public static boolean writable() { init(); return store.writable(); }
    public static String currentScope() { init(); return WaypointWorld.scope(Minecraft.getInstance()); }
    static void invalidateScope() { dirtyScope = true; }

    public static void replace(Snapshot value) {
        init();
        if (!store.writable()) throw new IllegalStateException("Waypoint file could not be loaded. Repair waypoints.json and restart.");
        data = Objects.requireNonNull(value);
        dirtyScope = true;
        if (!value.enabled() || value.groups().isEmpty()) { markers = visible = EMPTY; visibleCount = 0; }
        status = "Saving…";
        PENDING_SAVE.set(value);
        scheduleSave();
    }
    private static void scheduleSave() {
        if (SAVE_SCHEDULED.compareAndSet(false, true)) WRITER.execute(Waypoints::writePending);
    }
    private static void writePending() {
        try {
            Snapshot next;
            while ((next = PENDING_SAVE.getAndSet(null)) != null) {
                try { store.save(next); status = PENDING_SAVE.get() == null ? "" : "Saving…"; }
                catch (IOException failure) { status = "Save failed: " + failure.getMessage(); }
            }
        } finally {
            SAVE_SCHEDULED.set(false);
            if (PENDING_SAVE.get() != null) scheduleSave();
        }
    }
    public static void put(Group group) {
        var groups = new ArrayList<>(snapshot().groups());
        int index = -1;
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).id().equals(group.id())) { index = i; break; }
        if (index < 0) groups.add(group); else groups.set(index, group);
        replace(new Snapshot(data.enabled(), groups));
    }
    public static void removeGroup(String id) {
        var groups = new ArrayList<>(snapshot().groups());
        groups.removeIf(group -> group.id().equals(id));
        replace(new Snapshot(data.enabled(), groups));
        ROUTES.remove(id);
    }
    public static int importClipboard(String text) throws IOException {
        return importGroups(WaypointCodec.importGroups(text, currentScope()));
    }
    public static int importGroups(List<Group> imported) {
        var groups = new ArrayList<>(snapshot().groups());
        groups.addAll(imported);
        replace(new Snapshot(data.enabled(), groups));
        return imported.size();
    }
    public static boolean orderedAction(String action) {
        String here = currentScope();
        for (Group group : snapshot().groups()) if (data.enabled() && group.enabled() && group.ordered() &&
                group.scope().equals(here) && !group.points().isEmpty()) {
            ROUTES.computeIfAbsent(group.id(), ignored -> new WaypointRoute()).action(action, group.points().size());
            dirtyScope = true;
            return true;
        }
        return false;
    }

    private static void tick(Minecraft client) {
        // No location lookup, collection allocation, or IO while unused / disabled.
        if (!data.enabled() || data.groups().isEmpty()) return;
        tick++;
        if (client.level != level) {
            level = client.level; ROUTES.clear(); visibleCount = 0; dirtyScope = true; graceUntil = tick + 20;
        }
        if (client.level == null || client.player == null) { visibleCount = 0; return; }
        if (dirtyScope || tick % 20 == 0) {
            String nextScope = WaypointWorld.scope(client);
            if (!nextScope.equals(scope) || dirtyScope) { scope = nextScope; rebuild(); }
        }
        if (tick < graceUntil || markers.length == 0) { visibleCount = 0; return; }
        double x = client.player.getX(), y = client.player.getY(), z = client.player.getZ();
        for (Group group : data.groups()) if (group.enabled() && group.ordered() && group.scope().equals(scope))
            ROUTES.computeIfAbsent(group.id(), ignored -> new WaypointRoute()).tick(group.points(), x, y, z);
        if (tick % 5 != 0 && !dirtyScope) return;
        for (Marker marker : markers) {
            marker.distance = marker.point.distanceSquared(x, y, z);
            marker.relation = marker.route == null ? 2 : marker.route.relation(marker.index, marker.group.points().size());
            if (marker.route != null && marker.relation == 2) marker.distance = Double.POSITIVE_INFINITY;
        }
        Arrays.sort(markers, DISTANCE);
        visibleCount = 0;
        for (Marker marker : markers) {
            if (visibleCount == MAX_RENDERED || !Double.isFinite(marker.distance)) break;
            marker.updateLabel(client.font);
            visible[visibleCount++] = marker;
        }
    }
    private static void rebuild() {
        dirtyScope = false;
        var active = new ArrayList<Marker>();
        if (!scope.isEmpty()) for (Group group : data.groups()) if (group.enabled() && group.scope().equals(scope)) {
            WaypointRoute route = group.ordered() ? ROUTES.computeIfAbsent(group.id(), ignored -> new WaypointRoute()) : null;
            for (int i = 0; i < group.points().size(); i++) {
                Point point = group.points().get(i);
                if (point.enabled()) active.add(new Marker(group, point, i, route));
            }
        }
        markers = active.isEmpty() ? EMPTY : active.toArray(Marker[]::new);
        visible = active.isEmpty() ? EMPTY : new Marker[Math.min(MAX_RENDERED, markers.length)];
        visibleCount = 0;
        // The next tick refreshes distances and labels immediately after an edit.
        tick -= tick % 5;
    }
    private static void render(LevelRenderContext context) {
        if (!data.enabled() || visibleCount == 0) return;
        var camera = context.levelState().cameraRenderState;
        var queue = context.submitNodeCollector();
        float range = Math.max(8, Math.min(128, camera.depthFar * .5f));
        // Native submitCustomGeometry copies Pose; submitText copies its Matrix4f (26.1–26.3).
        // Reuse only the preparation stack; each queued draw still owns its transform snapshot.
        var pose = new PoseStack();
        var rotation = new Matrix4f().rotation(camera.orientation);
        for (int i = 0; i < visibleCount; i++) {
            Marker marker = visible[i];
            Point point = marker.point;
            double dx = point.x() + .5 - camera.pos.x, dy = point.y() + 1.4 - camera.pos.y, dz = point.z() + .5 - camera.pos.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double factor = marker.group.throughWalls() && length > range ? range / length : 1;
            pose.setIdentity();
            pose.translate(dx * factor, dy * factor, dz * factor);
            pose.mulPose(rotation);
            float scale = (float) (.025 * Math.max(1, (marker.group.throughWalls() ? Math.min(length, range) : length) / 12));
            pose.scale(scale, -scale, scale);
            int color = marker.color();
            var type = marker.group.throughWalls() ? PartyRenderPipeline.type() : RenderTypes.debugQuads();
            // A small billboard diamond has constant geometry even for distant markers.
            queue.submitCustomGeometry(pose, type, (matrix, vertices) -> {
                vertices.addVertex(matrix.pose(), 0, 3, 0).setColor(color);
                vertices.addVertex(matrix.pose(), 4, 7, 0).setColor(color);
                vertices.addVertex(matrix.pose(), 0, 11, 0).setColor(color);
                vertices.addVertex(matrix.pose(), -4, 7, 0).setColor(color);
            });
            queue.submitText(pose, -marker.width / 2f, -10, marker.label, false,
                    marker.group.throughWalls() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                    0xF000F0, color, 0x50000000, 0);
        }
    }
    private static final class Marker {
        final Group group;
        final Point point;
        final int index;
        final WaypointRoute route;
        double distance;
        int relation = 2, width;
        long labelDistance = -1;
        int labelRelation = 99;
        FormattedCharSequence label;
        Marker(Group group, Point point, int index, WaypointRoute route) {
            this.group = group; this.point = point; this.index = index; this.route = route;
        }
        void updateLabel(Font font) {
            long meters = Math.round(Math.sqrt(distance));
            if (meters == labelDistance && relation == labelRelation) return;
            String prefix = route == null ? "" : (index + 1) + ". " + switch (relation) {
                case -1 -> "[Previous] "; case 0 -> "[Current] "; case 1 -> "[Next] "; default -> "";
            };
            Component text = Component.literal(prefix + point.name() + " · " + meters + "m");
            label = text.getVisualOrderText(); width = font.width(text);
            labelDistance = meters; labelRelation = relation;
        }
        int color() {
            return 0xff000000 | (route == null ? point.color() : switch (relation) {
                case -1 -> 0xff7777; case 0 -> 0xffffff; case 1 -> 0x77ff99; default -> point.color();
            });
        }
    }
}
