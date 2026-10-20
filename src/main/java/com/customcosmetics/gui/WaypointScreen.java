// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.waypoint.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.features.waypoint.WaypointData.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Standalone manager reached through /customcosmetics waypoint or /ccos waypoint. */
public final class WaypointScreen extends Screen {
    private final Screen parent;
    private String selected = "", message = "", here = "", island = "";
    private boolean followCurrent = true;
    private int screenTicks;
    private int x, y, w, h, left, right, detail, rows, groupOffset, pointOffset;
    private List<Group> allGroups = List.of(), groups = List.of();
    private Group group;
    public WaypointScreen(Screen parent) { super(Component.literal("Waypoints")); this.parent = parent; }

    @Override protected void init() {
        w = Math.min(760, width - 16); h = Math.min(430, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        left = Math.clamp((w - 36) / 3, 110, 190); right = x + left + 24; detail = x + w - 12 - right;
        rows = Math.max(1, (h - 205) / 27);
        allGroups = Waypoints.snapshot().groups();
        here = Waypoints.currentScope();
        if (island.isBlank()) island = here.isBlank() ? "hypixel:unknown" : here;
        groups = allGroups.stream().filter(value -> value.scope().equals(island)).toList();
        group = groups.stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null);
        if (group == null && !groups.isEmpty()) { group = groups.getFirst(); selected = group.id(); }
        groupOffset = Math.clamp(groupOffset, 0, Math.max(0, groups.size() - rows));
        pointOffset = Math.clamp(pointOffset, 0, Math.max(0, group == null ? 0 : group.points().size() - rows));
        addRenderableWidget(new Action(x + w - 111, y + 10, 99, 22,
                Waypoints.snapshot().enabled() ? "Markers: On" : "Markers: Off", false,
                () -> perform(() -> Waypoints.replace(new Snapshot(!Waypoints.snapshot().enabled(), allGroups)))));
        addRenderableWidget(new Action(x + 12, y + 37, 25, 22, "<", false, () -> changeIsland(-1)));
        addRenderableWidget(new Action(x + 42, y + 37, Math.min(250, w - 96), 22,
                "Island: " + SkyblockIslands.name(island), false, () -> changeIsland(1)));
        addRenderableWidget(new Action(x + Math.min(297, w - 49), y + 37, 25, 22, ">", false, () -> changeIsland(1)));
        for (int i = 0; i < rows; i++) {
            int groupIndex = groupOffset + i, pointIndex = pointOffset + i;
            if (groupIndex < groups.size()) {
                Group item = groups.get(groupIndex);
                addRenderableWidget(new Action(x + 12, y + 72 + i * 27, left, 24,
                        (item.enabled() ? "" : "○ ") + item.name(), false, () -> {
                    selected = item.id(); pointOffset = 0; message = ""; rebuildWidgets();
                }).selected(() -> item.id().equals(selected)));
            }
            if (group != null && pointIndex < group.points().size()) {
                Point point = group.points().get(pointIndex);
                int at = pointIndex;
                addRenderableWidget(new Action(right, y + 72 + i * 27, detail - 43, 24,
                        (group.ordered() ? (at + 1) + ". " : "") + point.name() + " · " + point.x() + ", " + point.y() + ", " + point.z(),
                        false, () -> ClientUiAccess.setScreen(minecraft, new WaypointEditScreen(this, group, at))));
                addRenderableWidget(new Action(right + detail - 39, y + 72 + i * 27, 39, 24,
                        point.enabled() ? "On" : "Off", false, () -> perform(() -> {
                    var points = new ArrayList<>(group.points()); points.set(at, point.enabled(!point.enabled()));
                    Waypoints.put(group.points(points));
                })));
            }
        }
        addRenderableWidget(new Action(x + 12, y + h - 126, left, 22, "New Group", false,
                () -> ClientUiAccess.setScreen(minecraft, new WaypointEditScreen(this, null, -1, island))));
        int half = (detail - 6) / 2;
        Action editGroup = addRenderableWidget(new Action(right, y + h - 126, half, 22, "Edit Group", false,
                () -> ClientUiAccess.setScreen(minecraft, new WaypointEditScreen(this, group, -1))));
        Action addPoint = addRenderableWidget(new Action(right + half + 6, y + h - 126, detail - half - 6, 22, "Add Waypoint", true,
                () -> ClientUiAccess.setScreen(minecraft, new WaypointEditScreen(this, group, group.points().size()))));
        editGroup.active = group != null; addPoint.active = group != null;
        addRenderableWidget(new Action(x + 12, y + h - 98, w - 24, 22, "Import Skyblocker waypoints.json", false,
                () -> ClientUiAccess.setScreen(minecraft, new WaypointImportScreen(this))));
        int third = (w - 36) / 3;
        addRenderableWidget(new Action(x + 12, y + h - 70, third, 22, "Import Clipboard", false, () -> perform(() -> {
            int count = Waypoints.importClipboard(minecraft.keyboardHandler.getClipboard());
            message = "Imported " + count + " group(s). Other islands stay hidden.";
        })));
        Action export = addRenderableWidget(new Action(x + 18 + third, y + h - 70, third, 22, "Export Group", false, () -> perform(() -> {
            minecraft.keyboardHandler.setClipboard(WaypointCodec.exportGroups(List.of(group)));
            message = "Skyblocker V1 share data copied to clipboard.";
        })));
        export.active = group != null;
        addRenderableWidget(new Action(x + 24 + third * 2, y + h - 70, w - 36 - third * 2, 22, "Back", false, this::onClose));
    }
    private void changeIsland(int direction) {
        followCurrent = false;
        island = SkyblockIslands.nextScope(island, direction);
        selected = ""; groupOffset = pointOffset = 0; message = "";
        rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();
        if (++screenTicks % 20 != 0) return;
        String current = Waypoints.currentScope();
        if (!current.equals(here)) {
            here = current;
            if (followCurrent && !current.isBlank()) {
                island = current; selected = ""; groupOffset = pointOffset = 0;
            }
            rebuildWidgets();
        }
    }
    private void perform(Work work) {
        try { work.run(); }
        catch (Exception failure) { message = failure.getMessage() == null ? "Waypoint operation failed." : failure.getMessage(); }
        rebuildWidgets();
    }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (my >= y + 69 && my < y + h - 133 && mx >= x + 12 && mx < x + w - 12) {
            if (mx < right) groupOffset = Math.clamp(groupOffset - (int) Math.signum(vertical) * 3, 0, Math.max(0, groups.size() - rows));
            else if (group != null) pointOffset = Math.clamp(pointOffset - (int) Math.signum(vertical) * 3, 0, Math.max(0, group.points().size() - rows));
            rebuildWidgets(); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        text(g, font, "Waypoints", x + 12, y + 16, PINK, w - 140);
        if (w >= 600) text(g, font, "Current: " + (here.isBlank() ? "Waiting for Hypixel SkyBlock" : SkyblockIslands.name(here)),
                x + 334, y + 43, MUTED, w - 346);
        text(g, font, "Groups on this island · " + groups.size(), x + 12, y + 58, TEXT, left);
        text(g, font, group == null ? "Create a group to begin" : group.name() + " · " + group.points().size() +
                (group.scope().equals(here) ? " · Here" : " · Other area"), right, y + 58, TEXT, detail);
        if (groups.isEmpty()) text(g, font, "No groups yet", x + 16, y + 81, MUTED, left - 8);
        else if (group != null && group.points().isEmpty()) text(g, font, "Add your current position or enter coordinates.", right, y + 81, MUTED, detail);
        if (group != null) for (int i = 0; i < rows && pointOffset + i < group.points().size(); i++)
            g.fill(right + 2, y + 78 + i * 27, right + 4, y + 90 + i * 27, 0xff000000 | group.points().get(pointOffset + i).color());
        String feedback = !Waypoints.status().isEmpty() ? Waypoints.status() : message;
        text(g, font, feedback.isEmpty() ? "Scroll either list · Click a waypoint to edit · Nearest 256 markers are drawn" : feedback,
                x + 12, y + h - 41, feedback.isEmpty() ? MUTED : PINK, w - 24);
        text(g, font, "Ordered routes: /ccos waypoint ordered next | previous | reset", x + 12, y + h - 25, DIM, w - 24);
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
