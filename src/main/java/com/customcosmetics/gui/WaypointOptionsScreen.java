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

/** Skyblocker-style waypoint controls, with the render budget on a separate page. */
final class WaypointOptionsScreen extends ThemedScreen {
    private final Screen parent;
    private boolean performance;
    private String message = "";
    private int x, y, w, h;
    WaypointOptionsScreen(Screen parent) { super(Component.literal("Waypoint Options")); this.parent = parent; }

    @Override protected void init() {
        w = Math.min(620, width - 16); h = Math.min(320, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        int half = (w - 30) / 2, right = x + 18 + half;
        if (performance) {
            addRenderableWidget(new Slider(x + 12, y + 38, half, "Marker Size",
                    ConfigManager.waypointMarkerSize, .5, 2, .1, value -> {
                ConfigManager.waypointMarkerSize = value; changed();
            }));
            addRenderableWidget(new Slider(right, y + 38, half, "Opacity",
                    ConfigManager.waypointOpacity, .1, .85, .05, value -> {
                ConfigManager.waypointOpacity = value; changed();
            }));
            addRenderableWidget(new Slider(x + 12, y + 82, half, "Max Visible",
                    ConfigManager.waypointMaxRendered, 16, 256, 16, value -> {
                ConfigManager.waypointMaxRendered = (int) value; changed();
            }));
            addRenderableWidget(new Slider(right, y + 82, half, "Max Distance (0 = unlimited)",
                    ConfigManager.waypointMaxDistance, 0, 2048, 64, value -> {
                ConfigManager.waypointMaxDistance = (int) value; changed();
            }));
            addRenderableWidget(new Action(x + 12, y + 126, half, 22,
                    "Distance Text: " + onOff(ConfigManager.waypointShowDistance), false, () -> {
                ConfigManager.waypointShowDistance = !ConfigManager.waypointShowDistance; changed(); rebuildWidgets();
            }));
            addRenderableWidget(new Action(right, y + 126, half, 22, "Reset Display Defaults", false, () -> {
                ConfigManager.waypointMarkerSize = 1; ConfigManager.waypointOpacity = .5;
                ConfigManager.waypointMaxRendered = 64; ConfigManager.waypointMaxDistance = 0;
                ConfigManager.waypointShowDistance = false; changed(); rebuildWidgets();
            }));
            addRenderableWidget(new Action(x + 12, y + h - 62, w - 24, 22, "Waypoint Options", false, () -> {
                performance = false; message = ""; rebuildWidgets();
            }));
        } else {
            addRenderableWidget(new Action(x + 12, y + 38, half, 22,
                    "Render Line: " + onOff(ConfigManager.waypointRenderLine), false, () -> {
                ConfigManager.waypointRenderLine = !ConfigManager.waypointRenderLine; changed(); rebuildWidgets();
            }));
            addRenderableWidget(new Action(right, y + 38, half, 22,
                    "Allow Skipping: " + onOff(ConfigManager.waypointAllowSkipping), false, () -> {
                ConfigManager.waypointAllowSkipping = !ConfigManager.waypointAllowSkipping; changed(); rebuildWidgets();
            }));
            addRenderableWidget(new Action(x + 12, y + 66, half, 22,
                    (w < 400 ? "Go Backwards: " : "Allow Going Backwards: ") + onOff(ConfigManager.waypointAllowBackwards), false, () -> {
                ConfigManager.waypointAllowBackwards = !ConfigManager.waypointAllowBackwards; changed(); rebuildWidgets();
            }));
            addRenderableWidget(new Action(right, y + 66, half, 22,
                    "Names: " + onOff(ConfigManager.waypointShowLabels), false, () -> {
                ConfigManager.waypointShowLabels = !ConfigManager.waypointShowLabels; changed(); rebuildWidgets();
            }));
            addRenderableWidget(new Slider(x + 12, y + 98, half, "Line Width",
                    ConfigManager.waypointLineWidth, 1, 15, .5, value -> {
                ConfigManager.waypointLineWidth = value; changed();
            }));
            addRenderableWidget(new Slider(right, y + 98, half, "Activation Radius",
                    ConfigManager.waypointActivationRadius, 1, 10, .5, value -> {
                ConfigManager.waypointActivationRadius = value; changed();
            }));
            Field lineColor = addRenderableWidget(new Field(font, x + 12, y + 138, half, "Line Color ARGB", 20));
            lineColor.setMaxLength(8);
            lineColor.setValue(String.format(Locale.ROOT, "%08X", ConfigManager.waypointLineColor));
            lineColor.setResponder(value -> {
                if (!value.matches("[0-9a-fA-F]{8}")) { message = "Enter 8 hex digits: AARRGGBB"; return; }
                ConfigManager.waypointLineColor = (int) Long.parseLong(value, 16);
                message = ""; changed();
            });
            addRenderableWidget(new Action(right, y + 138, half, 22, "Pick Line Color", false,
                    () -> ClientUiAccess.setScreen(minecraft, new WaypointLineColorScreen(this))));
            addRenderableWidget(new Action(x + 12, y + h - 62, w - 24, 22, "Performance & Extras", false, () -> {
                performance = true; message = ""; rebuildWidgets();
            }));
        }
        addRenderableWidget(new Action(x + 12, y + h - 32, w - 24, 22, "Done", true, this::onClose));
    }
    private static String onOff(boolean value) { return value ? "On" : "Off"; }
    private static void changed() { Waypoints.invalidateVisuals(); ConfigManager.requestSave(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        text(g, font, performance ? "Waypoint Performance & Extras" : "Waypoint Options", x + 12, y + 14, PINK, w - 24);
        if (!performance) {
            uiText(g, font, "Line Color (AARRGGBB)", x + 12, y + 125, MUTED, w - 24);
            if (h >= 270) text(g, font, message.isEmpty() ? "Choose the block and beacon style in each group's editor." : message,
                    x + 12, y + 190, message.isEmpty() ? MUTED : PINK, w - 24);
        } else if (h >= 270)
            uiText(g, font, "The visible-marker limit and distance cap keep rendering work bounded.",
                    x + 12, y + 190, MUTED, w - 24);
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
