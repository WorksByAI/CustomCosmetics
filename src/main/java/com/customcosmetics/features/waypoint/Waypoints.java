// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
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
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.customcosmetics.features.waypoint.WaypointData.*;

/** Command-only waypoint tool with bounded render work and asynchronous atomic persistence. */
public final class Waypoints {
    public static final int MAX_RENDERED = 256;
    private static final Marker[] EMPTY = new Marker[0];
    private static Snapshot data = Snapshot.empty();
    private static WaypointStore store;
    private static boolean initialized, dirtyScope = true, selectionDirty = true;
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
    public static void invalidateVisuals() { selectionDirty = true; }

    public static void replace(Snapshot value) {
        init();
        if (!store.writable()) throw new IllegalStateException("Waypoint file could not be loaded. Repair waypoints.json and restart.");
        data = Objects.requireNonNull(value);
        dirtyScope = selectionDirty = true;
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
        requireUniqueCoordinates(groups);
        replace(new Snapshot(data.enabled(), groups));
    }
    public static void clearAll() {
        replace(new Snapshot(data.enabled(), List.of()));
        ROUTES.clear();
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
        return importGroupsDetailed(imported).groups();
    }
    public static WaypointData.ImportResult importGroupsDetailed(List<Group> imported) {
        var result = WaypointData.mergeImport(snapshot(), imported);
        if (result.groups() > 0) replace(result.snapshot());
        return result;
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
        // Island detection stays available for the manager even before the first group is created.
        WaypointWorld.tick(client);
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
            ROUTES.computeIfAbsent(group.id(), ignored -> new WaypointRoute()).tick(group.points(), x, y, z,
                    ConfigManager.waypointActivationRadius, ConfigManager.waypointAllowSkipping, ConfigManager.waypointAllowBackwards);
        if (tick % 5 != 0 && !selectionDirty) return;
        selectionDirty = false;
        int limit = Math.min(ConfigManager.waypointMaxRendered, visible.length);
        double maxDistance = ConfigManager.waypointMaxDistance;
        double maxDistanceSquared = maxDistance <= 0 ? Double.POSITIVE_INFINITY : maxDistance * maxDistance;
        int count = 0;
        for (Marker marker : markers) {
            marker.distance = marker.point.distanceSquared(x, y, z);
            marker.relation = marker.route == null ? 2 : marker.route.relation(marker.index, marker.group.points().size());
            if (marker.route != null && marker.relation == 2 || marker.distance > maxDistanceSquared) continue;
            if (count < limit) { visible[count] = marker; siftUp(count++); }
            else if (count > 0 && marker.distance < visible[0].distance) { visible[0] = marker; siftDown(count); }
        }
        Arrays.sort(visible, 0, count, DISTANCE);
        if (ConfigManager.waypointShowLabels) for (int i = 0; i < count; i++)
            visible[i].updateLabel(client.font, ConfigManager.waypointShowDistance);
        visibleCount = count;
    }
    private static void siftUp(int index) {
        Marker value = visible[index];
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (visible[parent].distance >= value.distance) break;
            visible[index] = visible[parent]; index = parent;
        }
        visible[index] = value;
    }
    private static void siftDown(int count) {
        Marker value = visible[0]; int index = 0;
        while ((index * 2 + 1) < count) {
            int child = index * 2 + 1;
            if (child + 1 < count && visible[child + 1].distance > visible[child].distance) child++;
            if (visible[child].distance <= value.distance) break;
            visible[index] = visible[child]; index = child;
        }
        visible[index] = value;
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
        selectionDirty = true;
    }
    private static void render(LevelRenderContext context) {
        if (!data.enabled() || visibleCount == 0) return;
        var camera = context.levelState().cameraRenderState;
        var queue = context.submitNodeCollector();
        var pose = new PoseStack();
        var rotation = new Matrix4f().rotation(camera.orientation);
        var forward = new Vector3f(0, 0, -1).rotate(camera.orientation);
        float size = (float) ConfigManager.waypointMarkerSize;
        for (int i = 0; i < visibleCount; i++) {
            Marker marker = visible[i];
            Point point = marker.point;
            double dx = point.x() + .5 - camera.pos.x, dy = point.y() + 1.4 - camera.pos.y, dz = point.z() + .5 - camera.pos.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 8 && dx * forward.x + dy * forward.y + dz * forward.z < -length * .15) continue;
            int color = marker.color();
            if (marker.route != null && marker.relation == 1 && ConfigManager.waypointRenderLine) {
                int lineColor = ConfigManager.waypointLineColor;
                float tx = (float) (point.x() + .5 - camera.pos.x);
                float ty = (float) (point.y() + .5 - camera.pos.y);
                float tz = (float) (point.z() + .5 - camera.pos.z);
                float nx = tx - forward.x, ny = ty - forward.y, nz = tz - forward.z;
                float inv = (float) (1 / Math.max(.001, Math.sqrt(nx * nx + ny * ny + nz * nz)));
                nx *= inv; ny *= inv; nz *= inv;
                pose.setIdentity();
                final float normalX = nx, normalY = ny, normalZ = nz;
                queue.submitCustomGeometry(pose, RenderTypes.linesTranslucent(), (matrix, vertices) -> {
                    vertices.addVertex(matrix.pose(), forward.x, forward.y, forward.z).setColor(lineColor)
                            .setNormal(normalX, normalY, normalZ).setLineWidth((float) ConfigManager.waypointLineWidth);
                    vertices.addVertex(matrix.pose(), tx, ty, tz).setColor(lineColor)
                            .setNormal(normalX, normalY, normalZ).setLineWidth((float) ConfigManager.waypointLineWidth);
                });
            }
            var type = marker.group.throughWalls() ? PartyRenderPipeline.type() : RenderTypes.debugQuads();
            pose.setIdentity();
            pose.translate(point.x() - camera.pos.x, point.y() - camera.pos.y, point.z() - camera.pos.z);
            String style = marker.group.style();
            int alpha = Math.clamp((int) Math.round(point.alpha() * ConfigManager.waypointOpacity * 510), 0, 255);
            int fill = (alpha << 24) | (color & 0xffffff);
            float low = .5f - size * .5f, high = .5f + size * .5f;
            queue.submitCustomGeometry(pose, type, (matrix, vertices) -> {
                if (!style.equals("outline"))
                    WaypointGeometry.box(vertices, matrix.pose(), low, 0, low, high, size, high, fill);
                if (style.equals("outline") || style.equals("outlined_highlight") || style.equals("outlined_waypoint"))
                    WaypointGeometry.outline(vertices, matrix.pose(), low, 0, low, high, size, high,
                            (float) Math.max(.012 * size, Math.min(.06, length * .0015)), color);
            });
            if (style.equals("waypoint") || style.equals("outlined_waypoint")) {
                double horizontalDistance = Math.hypot(dx, dz);
                float radius = (float) Math.max(1, horizontalDistance / 96);
                BeaconRenderer.submitBeaconBeam(pose, queue, BeaconRenderer.BEAM_LOCATION, 1,
                        (context.levelState().gameTime % 40) + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true),
                        0, 2048, color, .2f * radius, .25f * radius);
                // At extreme range a block-sized beacon base collapses to a pixel. Keep
                // a small, depth-correct square at the actual marker position.
                if (length > 256) {
                    pose.setIdentity();
                    pose.translate(dx, dy, dz);
                    pose.mulPose(rotation);
                    float scale = (float) (length * .012);
                    pose.scale(scale, -scale, scale);
                    queue.submitCustomGeometry(pose, type, (matrix, vertices) -> {
                        vertices.addVertex(matrix.pose(), -.5f, -.5f, 0).setColor(color);
                        vertices.addVertex(matrix.pose(), .5f, -.5f, 0).setColor(color);
                        vertices.addVertex(matrix.pose(), .5f, .5f, 0).setColor(color);
                        vertices.addVertex(matrix.pose(), -.5f, .5f, 0).setColor(color);
                    });
                }
            }
            if (!ConfigManager.waypointShowLabels) continue;
            pose.setIdentity();
            pose.translate(dx, dy, dz);
            pose.mulPose(rotation);
            float scale = (float) (.025 * Math.max(1, length / 10));
            pose.scale(scale, -scale, scale);
            queue.submitText(pose, -marker.width / 2f, -13, marker.label, false,
                    marker.group.throughWalls() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                    0xF000F0, 0xffffffff, 0x50000000, 0);
            if (marker.route != null)
                queue.submitText(pose, -marker.indexWidth / 2f, 0, marker.indexLabel, false,
                        Font.DisplayMode.SEE_THROUGH, 0xF000F0, 0xffffffff, 0x50000000, 0);
        }
    }
    private static final class Marker {
        final Group group;
        final Point point;
        final int index;
        final WaypointRoute route;
        double distance;
        int relation = 2, width, indexWidth;
        long labelDistance = -1;
        boolean labelShowDistance;
        FormattedCharSequence label, indexLabel;
        Marker(Group group, Point point, int index, WaypointRoute route) {
            this.group = group; this.point = point; this.index = index; this.route = route;
            if (route != null) indexLabel = Component.literal(Integer.toString(index + 1)).getVisualOrderText();
        }
        void updateLabel(Font font, boolean showDistance) {
            long meters = showDistance ? Math.round(Math.sqrt(distance)) : -1;
            if (meters == labelDistance && showDistance == labelShowDistance) return;
            Component text = Component.literal(point.name() + (showDistance ? " · " + meters + "m" : ""));
            label = text.getVisualOrderText(); width = font.width(text);
            if (indexLabel != null && indexWidth == 0) indexWidth = font.width(indexLabel);
            labelDistance = meters; labelShowDistance = showDistance;
        }
        int color() {
            return 0xff000000 | (route == null ? point.color() : switch (relation) {
                case -1 -> 0xff7777; case 0 -> 0xffffff; case 1 -> 0x77ff99; default -> point.color();
            });
        }
    }
}
