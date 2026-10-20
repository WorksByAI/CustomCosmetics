// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.waypoint.Waypoints;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.gui.PinkUi.*;

/** SkyBlock waypoint visuals and render-work budget, independent of other module settings. */
final class WaypointOptionsScreen extends Screen {
    private final Screen parent;
    private int x, y, w, h;
    WaypointOptionsScreen(Screen parent) { super(Component.literal("Waypoint Display Options")); this.parent = parent; }

    @Override protected void init() {
        w = Math.min(620, width - 16); h = Math.min(320, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        int half = (w - 30) / 2;
        addRenderableWidget(new Action(x + 12, y + 38, half, 22,
                "Names: " + (ConfigManager.waypointShowLabels ? "On" : "Off"), false, () -> {
            ConfigManager.waypointShowLabels = !ConfigManager.waypointShowLabels; changed(); rebuildWidgets();
        }));
        addRenderableWidget(new Action(x + 18 + half, y + 38, half, 22,
                "Distance Text: " + (ConfigManager.waypointShowDistance ? "On" : "Off"), false, () -> {
            ConfigManager.waypointShowDistance = !ConfigManager.waypointShowDistance; changed(); rebuildWidgets();
        }));
        addRenderableWidget(new Slider(x + 12, y + 70, half, "Marker Size",
                ConfigManager.waypointMarkerSize, .5, 2, .1, value -> {
            ConfigManager.waypointMarkerSize = value; changed();
        }));
        addRenderableWidget(new Slider(x + 18 + half, y + 70, half, "Opacity",
                ConfigManager.waypointOpacity, .1, .85, .05, value -> {
            ConfigManager.waypointOpacity = value; changed();
        }));
        addRenderableWidget(new Slider(x + 12, y + 106, half, "Max Visible",
                ConfigManager.waypointMaxRendered, 16, 256, 16, value -> {
            ConfigManager.waypointMaxRendered = (int) value; changed();
        }));
        addRenderableWidget(new Slider(x + 18 + half, y + 106, half, "Max Distance (0 = unlimited)",
                ConfigManager.waypointMaxDistance, 0, 2048, 64, value -> {
            ConfigManager.waypointMaxDistance = (int) value; changed();
        }));
        addRenderableWidget(new Slider(x + 12, y + 142, half, "Route Activation Radius",
                ConfigManager.waypointActivationRadius, 1, 10, .5, value -> {
            ConfigManager.waypointActivationRadius = value; changed();
        }));
        addRenderableWidget(new Action(x + 18 + half, y + 145, half, 22, "Reset Display Defaults", false, () -> {
            ConfigManager.waypointShowLabels = ConfigManager.waypointShowDistance = true;
            ConfigManager.waypointMarkerSize = 1; ConfigManager.waypointOpacity = .5;
            ConfigManager.waypointMaxRendered = 64; ConfigManager.waypointMaxDistance = 0;
            ConfigManager.waypointActivationRadius = 1.5;
            changed(); rebuildWidgets();
        }));
        addRenderableWidget(new Action(x + 12, y + h - 32, w - 24, 22, "Done", true, this::onClose));
    }
    private static void changed() { Waypoints.invalidateVisuals(); ConfigManager.requestSave(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        text(g, font, "Waypoint Display Options", x + 12, y + 14, PINK, w - 24);
        if (h >= 245) {
            text(g, font, "Choose each group's beacon, highlight, outline or diamond style in Edit Group.",
                    x + 12, y + 189, MUTED, w - 24);
            text(g, font, "Fewer visible markers and shorter range reduce per-frame rendering work.",
                    x + 12, y + 205, MUTED, w - 24);
        }
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
