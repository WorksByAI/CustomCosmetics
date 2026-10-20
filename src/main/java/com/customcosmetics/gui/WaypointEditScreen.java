// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.waypoint.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.features.waypoint.WaypointData.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Form edits are transactional: Cancel leaves the saved group unchanged. */
final class WaypointEditScreen extends Screen {
    private final Screen parent;
    private final Group original;
    private final int pointIndex;
    private final boolean groupMode, existing;
    private String name, scope, px = "0", py = "0", pz = "0", color = "55FF99", message = "";
    private boolean enabled = true, ordered, throughWalls = true, confirmDelete;
    private int orderIndex, x, y, w, h;
    private Field nameField, xField, yField, zField, colorField;

    WaypointEditScreen(Screen parent, Group group, int pointIndex) {
        this(parent, group, pointIndex, Waypoints.currentScope());
    }
    WaypointEditScreen(Screen parent, Group group, int pointIndex, String selectedScope) {
        super(Component.literal(pointIndex < 0 ? "Waypoint Group" : "Waypoint"));
        this.parent = parent; original = group; this.pointIndex = pointIndex; groupMode = pointIndex < 0;
        existing = group != null && (groupMode || pointIndex < group.points().size());
        name = groupMode ? group == null ? "New Group" : group.name() : "Waypoint " + (pointIndex + 1);
        scope = group == null ? selectedScope : group.scope();
        if (groupMode && group != null) { enabled = group.enabled(); ordered = group.ordered(); throughWalls = group.throughWalls(); }
        if (!groupMode) {
            orderIndex = pointIndex;
            if (existing) {
                Point point = group.points().get(pointIndex);
                name = point.name(); px = "" + point.x(); py = "" + point.y(); pz = "" + point.z();
                color = String.format(Locale.ROOT, "%06X", point.color()); enabled = point.enabled();
            } else takePosition();
        }
    }
    private void takePosition() {
        var player = Minecraft.getInstance().player;
        if (player == null) { message = "Join a world to use your current position."; return; }
        var pos = player.blockPosition();
        px = "" + pos.getX(); py = "" + pos.getY(); pz = "" + pos.getZ();
        if (xField != null) { xField.setValue(px); yField.setValue(py); zField.setValue(pz); }
    }
    @Override protected void init() {
        w = Math.min(500, width - 16); h = Math.min(318, height - 16); x = (width - w) / 2; y = (height - h) / 2;
        nameField = addRenderableWidget(new Field(font, x + 12, y + (compact() ? 39 : 45), w - 24, "Name", 20));
        nameField.setMaxLength(80); nameField.setValue(name); nameField.setResponder(value -> name = value);
        if (groupMode) buildGroup(); else buildPoint();
        int third = (w - 36) / 3;
        Action save = addRenderableWidget(new Action(x + 12, y + h - 34, third, 22, "Save", true, this::save));
        save.active = Waypoints.writable();
        addRenderableWidget(new Action(x + 18 + third, y + h - 34, third, 22, "Cancel", false, this::onClose));
        Action delete = addRenderableWidget(new Action(x + 24 + third * 2, y + h - 34, w - 36 - third * 2, 22,
                confirmDelete ? "Confirm Delete" : "Delete", false, this::delete));
        delete.active = existing && Waypoints.writable();
    }
    private void buildGroup() {
        int half = (w - 30) / 2;
        addRenderableWidget(new Action(x + 12, y + (compact() ? 66 : 76), half, 22, enabled ? "Group: Enabled" : "Group: Disabled", false,
                () -> { enabled = !enabled; rebuildWidgets(); }));
        addRenderableWidget(new Action(x + 18 + half, y + (compact() ? 66 : 76), half, 22, ordered ? "Ordered Route: On" : "Ordered Route: Off", false,
                () -> { ordered = !ordered; rebuildWidgets(); }));
        addRenderableWidget(new Action(x + 12, y + (compact() ? 92 : 104), w - 24, 22, throughWalls ? "Visible Through Walls: On" : "Visible Through Walls: Off", false,
                () -> { throughWalls = !throughWalls; rebuildWidgets(); }));
        addRenderableWidget(new Action(x + 12, y + (compact() ? 138 : 158), w - 24, 22, "Bind to Current World / Island", false, () -> {
            String current = Waypoints.currentScope();
            if (current.isBlank()) message = "Join a world or wait for Hypixel island data.";
            else { scope = current; message = "Scope updated. Save to apply."; }
        }));
    }
    private void buildPoint() {
        int third = (w - 36) / 3;
        xField = coordinate(x + 12, y + (compact() ? 76 : 92), third, "X", px, value -> px = value);
        yField = coordinate(x + 18 + third, y + (compact() ? 76 : 92), third, "Y", py, value -> py = value);
        zField = coordinate(x + 24 + third * 2, y + (compact() ? 76 : 92), w - 36 - third * 2, "Z", pz, value -> pz = value);
        int half = (w - 30) / 2;
        colorField = addRenderableWidget(new Field(font, x + 12, y + (compact() ? 112 : 139), half, "Color (RRGGBB)", 20));
        colorField.setMaxLength(7); colorField.setValue(color); colorField.setResponder(value -> color = value);
        addRenderableWidget(new Action(x + 18 + half, y + (compact() ? 112 : 139), half, 22, "Use Current Position", false, this::takePosition));
        addRenderableWidget(new Action(x + 12, y + (compact() ? 138 : 171), w - 24, 22, enabled ? "Waypoint: Enabled" : "Waypoint: Disabled", false,
                () -> { enabled = !enabled; rebuildWidgets(); }));
        if (original.ordered()) {
            Action up = addRenderableWidget(new Action(x + 12, y + (compact() ? 164 : 202), half, 22, "Earlier #" + (orderIndex + 1), false,
                    () -> { orderIndex--; rebuildWidgets(); }));
            up.active = orderIndex > 0;
            Action down = addRenderableWidget(new Action(x + 18 + half, y + (compact() ? 164 : 202), half, 22, "Move Later", false,
                    () -> { orderIndex++; rebuildWidgets(); }));
            down.active = orderIndex < original.points().size() - (existing ? 1 : 0);
        }
    }
    private boolean compact() { return h < 300; }
    private Field coordinate(int xx, int yy, int ww, String label, String value, java.util.function.Consumer<String> change) {
        Field field = addRenderableWidget(new Field(font, xx, yy, ww, label, 20));
        field.setMaxLength(12); field.setValue(value); field.setResponder(change); return field;
    }
    private void save() {
        try {
            if (groupMode) {
                if (scope.isBlank()) throw new IllegalArgumentException("Bind this group to a world / island first.");
                Group source = original == null ? Group.create(name, scope) : original;
                Waypoints.put(new Group(source.id(), name, scope, enabled, ordered, throughWalls, source.points()));
            } else {
                String rgb = color.startsWith("#") ? color.substring(1) : color;
                if (!rgb.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color must contain six hexadecimal digits, e.g. 55FF99.");
                Point point = new Point(name, Integer.parseInt(px.strip()), Integer.parseInt(py.strip()), Integer.parseInt(pz.strip()),
                        Integer.parseInt(rgb, 16), enabled);
                var points = new ArrayList<>(original.points());
                if (existing) points.remove(pointIndex);
                points.add(Math.clamp(orderIndex, 0, points.size()), point);
                Waypoints.put(original.points(points));
            }
            onClose();
        } catch (NumberFormatException bad) { message = "Coordinates must be whole numbers."; }
        catch (RuntimeException bad) { message = bad.getMessage(); }
    }
    private void delete() {
        if (!confirmDelete) {
            confirmDelete = true; message = groupMode ? "Delete this group and all its waypoints? Click again." : "Delete this waypoint? Click again.";
            rebuildWidgets(); return;
        }
        try {
            if (groupMode) Waypoints.removeGroup(original.id());
            else { var points = new ArrayList<>(original.points()); points.remove(pointIndex); Waypoints.put(original.points(points)); }
            onClose();
        } catch (RuntimeException bad) { message = bad.getMessage(); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        boolean headerMessage = compact() && original != null && original.ordered() && !groupMode && !message.isEmpty();
        text(g, font, headerMessage ? message : (existing ? "Edit " : "New ") + getTitle().getString(), x + 12, y + 13, PINK, w - 24);
        text(g, font, "Name", x + 12, y + (compact() ? 27 : 32), MUTED, w - 24);
        if (groupMode) {
            text(g, font, "Scope: " + (scope.isBlank() ? "Not set" : scope), x + 12, y + (compact() ? 122 : 139), TEXT, w - 24);
            if (!compact()) {
            text(g, font, "Groups appear only in their bound world or island.", x + 12, y + 194, MUTED, w - 24);
            text(g, font, "SkyBlock waypoints follow their island across server instances.", x + 12, y + 208, MUTED, w - 24);
            text(g, font, "Ordered: previous (red), current (white), next (green).", x + 12, y + 222, MUTED, w - 24);
            text(g, font, "Reach the next point within 1.5 blocks to advance.", x + 12, y + 236, MUTED, w - 24);
            }
        } else {
            int third = (w - 36) / 3;
            text(g, font, "X", x + 12, y + (compact() ? 64 : 78), MUTED, third);
            text(g, font, "Y", x + 18 + third, y + (compact() ? 64 : 78), MUTED, third);
            text(g, font, "Z", x + 24 + third * 2, y + (compact() ? 64 : 78), MUTED, third);
            text(g, font, "Color · RRGGBB", x + 12, y + (compact() ? 100 : 125), MUTED, w - 24);
            if (!compact()) text(g, font, original.ordered() ? "Route position: " + (orderIndex + 1) : "Group: " + original.name(),
                    x + 12, y + 236, MUTED, w - 24);
        }
        if (!(compact() && original != null && original.ordered() && !groupMode))
            text(g, font, message, x + 12, y + h - 51, PINK, w - 24);
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
