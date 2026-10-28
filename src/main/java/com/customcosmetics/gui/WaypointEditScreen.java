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
final class WaypointEditScreen extends ThemedScreen {
    private final Screen parent;
    private final Group original;
    private final int pointIndex;
    private final boolean groupMode, existing;
    private String name, scope, style = "waypoint", px = "0", py = "0", pz = "0", color = "55FF99", message = "";
    private double alpha = .5;
    private boolean enabled = true, ordered, throughWalls = true, confirmDelete;
    private long deleteConfirmUntil;
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
        if (groupMode && group != null) { enabled = group.enabled(); ordered = group.ordered(); throughWalls = group.throughWalls(); style = group.style(); }
        if (!groupMode) {
            orderIndex = pointIndex;
            if (existing) {
                Point point = group.points().get(pointIndex);
                name = point.name(); px = "" + point.x(); py = "" + point.y(); pz = "" + point.z();
                color = String.format(Locale.ROOT, "%02X%06X", Math.round(point.alpha() * 255), point.color());
                alpha = point.alpha(); enabled = point.enabled();
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
        int half = (w - 30) / 2;
        addRenderableWidget(new Action(x + 12, y + h - 34, half, 22, "Cancel", false, this::onClose));
        Action save = addRenderableWidget(new Action(x + 18 + half, y + h - 34, w - 30 - half, 22, "Save", true, this::save));
        save.active = Waypoints.writable();
        if (existing) {
            Action delete = addRenderableWidget(new Action(x + w - 120, y + 11, 108, 18,
                    confirmDelete ? "Confirm Delete" : "Delete", false, this::delete));
            delete.active = Waypoints.writable();
        }
    }
    private void buildGroup() {
        int half = (w - 30) / 2;
        addRenderableWidget(new Action(x + 12, y + (compact() ? 66 : 76), half, 22, enabled ? "Group: Enabled" : "Group: Disabled", false,
                () -> { enabled = !enabled; rebuildWidgets(); }));
        addRenderableWidget(new Action(x + 18 + half, y + (compact() ? 66 : 76), half, 22, ordered ? "Ordered Route: On" : "Ordered Route: Off", false,
                () -> { ordered = !ordered; rebuildWidgets(); }));
        addRenderableWidget(new Action(x + 12, y + (compact() ? 92 : 104), w - 24, 22, throughWalls ? "Visible Through Walls: On" : "Visible Through Walls: Off", false,
                () -> { throughWalls = !throughWalls; rebuildWidgets(); }));
        addRenderableWidget(new Action(x + 12, y + (compact() ? 118 : 132), w - 24, 22, "Style: " + styleName(style), false, () -> {
            style = STYLES.get((STYLES.indexOf(style) + 1) % STYLES.size()); rebuildWidgets();
        }));
        addRenderableWidget(new Action(x + 12, y + (compact() ? 144 : 158), w - 24, 22, "Bind to Current SkyBlock Island", false, () -> {
            String current = Waypoints.currentScope();
            if (current.isBlank()) message = "Join Hypixel SkyBlock or wait for island data.";
            else { scope = current; message = "Scope updated. Save to apply."; }
        }));
    }
    private void buildPoint() {
        int third = (w - 36) / 3;
        xField = coordinate(x + 12, y + (compact() ? 76 : 92), third, "X", px, value -> px = value);
        yField = coordinate(x + 18 + third, y + (compact() ? 76 : 92), third, "Y", py, value -> py = value);
        zField = coordinate(x + 24 + third * 2, y + (compact() ? 76 : 92), w - 36 - third * 2, "Z", pz, value -> pz = value);
        int half = (w - 30) / 2;
        colorField = addRenderableWidget(new Field(font, x + 12, y + (compact() ? 112 : 139), half, "Color (ARGB)", 20));
        colorField.setMaxLength(9); colorField.setValue(color); colorField.setResponder(value -> color = value);
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
                if (scope.isBlank()) throw new IllegalArgumentException("Select a SkyBlock island first.");
                Group source = original == null ? Group.create(name, scope) : original;
                Waypoints.put(new Group(source.id(), name, scope, enabled, ordered, throughWalls, source.points(), style));
            } else {
                String rgb = color.startsWith("#") ? color.substring(1) : color;
                if (!rgb.matches("(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))
                    throw new IllegalArgumentException("Color needs RRGGBB or AARRGGBB hex digits.");
                if (rgb.length() == 8) alpha = Integer.parseInt(rgb.substring(0, 2), 16) / 255d;
                String rgbOnly = rgb.substring(rgb.length() - 6);
                Point point = new Point(name, Integer.parseInt(px.strip()), Integer.parseInt(py.strip()), Integer.parseInt(pz.strip()),
                        Integer.parseInt(rgbOnly, 16), enabled, alpha);
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
        long now = System.currentTimeMillis();
        if (!confirmDelete || now >= deleteConfirmUntil) {
            confirmDelete = true; deleteConfirmUntil = now + 10_000;
            message = groupMode ? "Delete this group and all its waypoints? Click again." : "Delete this waypoint? Click again.";
            rebuildWidgets(); return;
        }
        try {
            if (groupMode) Waypoints.removeGroup(original.id());
            else { var points = new ArrayList<>(original.points()); points.remove(pointIndex); Waypoints.put(original.points(points)); }
            onClose();
        } catch (RuntimeException bad) { message = bad.getMessage(); }
    }
    @Override public void tick() {
        super.tick();
        if (confirmDelete && System.currentTimeMillis() >= deleteConfirmUntil) {
            confirmDelete = false; message = ""; rebuildWidgets();
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        boolean headerMessage = compact() && original != null && original.ordered() && !groupMode && !message.isEmpty();
        text(g, font, headerMessage ? message : UiText.tr(existing ? "Edit " : "New ") + getTitle().getString(), x + 12, y + 13, PINK,
                existing ? w - 140 : w - 24);
        uiText(g, font, "Name", x + 12, y + (compact() ? 27 : 32), MUTED, w - 24);
        if (groupMode) {
            if (!compact() || message.isEmpty())
                uiText(g, font, "Scope: " + (scope.isBlank() ? "Not set" : scope), x + 12, y + (compact() ? 173 : 190), TEXT, w - 24);
            if (!compact()) {
            uiText(g, font, "Groups follow their SkyBlock island across server instances.", x + 12, y + 208, MUTED, w - 24);
            uiText(g, font, "Ordered: previous (red), current (white), next (green).", x + 12, y + 222, MUTED, w - 24);
            uiText(g, font, "Activation radius is adjustable under Display Options.", x + 12, y + 236, MUTED, w - 24);
            }
        } else {
            int third = (w - 36) / 3;
            uiText(g, font, "X", x + 12, y + (compact() ? 64 : 78), MUTED, third);
            uiText(g, font, "Y", x + 18 + third, y + (compact() ? 64 : 78), MUTED, third);
            uiText(g, font, "Z", x + 24 + third * 2, y + (compact() ? 64 : 78), MUTED, third);
            uiText(g, font, "Color · AARRGGBB (or RRGGBB)", x + 12, y + (compact() ? 100 : 125), MUTED, w - 24);
            if (!compact()) uiText(g, font, original.ordered() ? "Route position: " + (orderIndex + 1) : "Group: " + original.name(),
                    x + 12, y + 236, MUTED, w - 24);
        }
        if (!(compact() && original != null && original.ordered() && !groupMode))
            uiText(g, font, message, x + 12, y + h - 51, PINK, w - 24);
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
