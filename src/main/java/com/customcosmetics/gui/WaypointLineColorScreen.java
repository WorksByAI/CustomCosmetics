// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.waypoint.Waypoints;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.gui.PinkUi.*;

/** Transactional ARGB picker for the ordered-route cursor line. */
final class WaypointLineColorScreen extends Screen {
    private final Screen parent;
    private int color, x, y, w, h;
    WaypointLineColorScreen(Screen parent) {
        super(Component.literal("Line Color"));
        this.parent = parent;
        color = ConfigManager.waypointLineColor;
    }
    @Override protected void init() {
        w = Math.min(500, width - 16); h = Math.min(250, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        int half = (w - 30) / 2, right = x + 18 + half;
        slider(x + 12, y + 42, half, "Red", 16);
        slider(right, y + 42, half, "Green", 8);
        slider(x + 12, y + 86, half, "Blue", 0);
        slider(right, y + 86, half, "Opacity", 24);
        addRenderableWidget(new Action(x + 12, y + h - 34, half, 22, "Cancel", false, this::onClose));
        addRenderableWidget(new Action(right, y + h - 34, half, 22, "Save", true, () -> {
            ConfigManager.waypointLineColor = color;
            Waypoints.invalidateVisuals(); ConfigManager.requestSave();
            onClose();
        }));
    }
    private void slider(int xx, int yy, int width, String label, int shift) {
        addRenderableWidget(new Slider(xx, yy, width, label, color >>> shift & 255, 0, 255, 1,
                value -> color = color & ~(255 << shift) | (int) value << shift));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        text(g, font, "Line Color", x + 12, y + 14, PINK, w - 24);
        rounded(g, x + 12, y + 133, w - 24, 22, 5, color);
        text(g, font, "#" + String.format(Locale.ROOT, "%08X", color), x + 18, y + 140, TEXT, w - 36);
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
