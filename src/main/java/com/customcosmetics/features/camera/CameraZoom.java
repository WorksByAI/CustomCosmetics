// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.camera;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.config.ConfigManager;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class CameraZoom {
    private static final double MAX_ZOOM = Math.log(50);
    private static KeyMapping key;
    private static boolean active, wasPressed;
    private static double scrollScale, current, startZoom, targetZoom;
    private static double sensitivity = 1;
    private static long transitionStart, durationNanos;

    private CameraZoom() {}

    public static void init() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("zoomify")) return;
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.zoom", ClientInput.C, CameraKeys.CATEGORY));
    }

    public static float fov(float vanilla) {
        var client = Minecraft.getInstance();
        if (key == null || !ConfigManager.zoomEnabled || client.level == null) {
            active = wasPressed = false;
            durationNanos = 0;
            current = targetZoom = 0;
            sensitivity = 1;
            return vanilla;
        }
        boolean down = key.isDown();
        boolean controls = ClientUiAccess.screen(client) == null;
        boolean next = controls && (ConfigManager.zoomToggle ? active ^ (down && !wasPressed) : down);
        wasPressed = down;
        if (next && !active) scrollScale = 0;
        active = next;

        double target = active ? Math.log(ConfigManager.zoomFactor) + scrollScale : 0;
        if (target != targetZoom || durationNanos != 0) {
            long now = System.nanoTime();
            if (target != targetZoom) {
                startZoom = zoomAt(now);
                targetZoom = target;
                transitionStart = now;
                double seconds = target > startZoom ? ConfigManager.zoomInSeconds : ConfigManager.zoomOutSeconds;
                durationNanos = (long) (seconds * 1_000_000_000);
            }
            if (!ConfigManager.zoomAnimate || now - transitionStart >= durationNanos) durationNanos = 0;
            double zoom = zoomAt(now);
            if (current != zoom) {
                current = zoom;
                sensitivity = Math.exp(-zoom);
            }
        }
        return scaleFov(vanilla);
    }

    private static double zoomAt(long now) {
        if (durationNanos == 0) return targetZoom;
        double progress = Math.clamp((double) (now - transitionStart) / durationNanos, 0, 1);
        return Mth.lerp(progress * progress * (3 - 2 * progress), startZoom, targetZoom);
    }

    public static float scaleFov(float vanilla) {
        if (current == 0 || !ConfigManager.zoomEnabled) return vanilla;
        return (float) Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(vanilla) / 2) * sensitivity()));
    }

    public static double sensitivity() {
        return ConfigManager.zoomEnabled ? sensitivity : 1;
    }

    public static boolean scroll(Minecraft client, double delta) {
        if (!active || !ConfigManager.zoomEnabled || !ConfigManager.zoomScroll || delta == 0
                || client.level == null || ClientUiAccess.screen(client) != null) return false;
        double base = Math.log(ConfigManager.zoomFactor);
        double minOffset = ConfigManager.zoomScrollBelowInitial ? -base : 0;
        scrollScale = Math.clamp(scrollScale + delta * 0.20, minOffset, MAX_ZOOM - base);
        return true;
    }
}
