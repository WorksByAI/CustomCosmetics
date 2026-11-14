// SPDX-License-Identifier: MIT
// Adapted from HitColor X, Copyright (c) 2026 Contt.
package com.customcosmetics.features.hitcolor;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HitColors {
    private static final boolean EXTERNAL = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("contts-hitcolorx");
    public enum Layer {
        ARMOR(8), ITEM(9), CAPE(11), TRIM(12), BODY(0);
        private final int row;
        Layer(int row) { this.row = row; }
        public boolean selected() {
            return switch (this) {
                case BODY -> ConfigManager.hitColorBody;
                case ARMOR -> ConfigManager.hitColorArmor;
                case TRIM -> ConfigManager.hitColorTrims;
                case ITEM -> ConfigManager.hitColorItems;
                case CAPE -> ConfigManager.hitColorCape;
            };
        }
        public void selected(boolean value) {
            switch (this) {
                case BODY -> ConfigManager.hitColorBody = value;
                case ARMOR -> ConfigManager.hitColorArmor = value;
                case TRIM -> ConfigManager.hitColorTrims = value;
                case ITEM -> ConfigManager.hitColorItems = value;
                case CAPE -> ConfigManager.hitColorCape = value;
            }
        }
    }

    public static final int VANILLA_HURT = 0xB2FF0000;
    public static final int CLEAR = 0xFF000000;
    public static final Map<String, Integer> PRESETS;
    static {
        var presets = new LinkedHashMap<String, Integer>();
        presets.put("red", 0xFF0000);
        presets.put("crimson", 0xDC143C);
        presets.put("darkred", 0x8B0000);
        presets.put("coral", 0xFF7F50);
        presets.put("orange", 0xFFA500);
        presets.put("gold", 0xFFD700);
        presets.put("yellow", 0xFFFF00);
        presets.put("lime", 0x32CD32);
        presets.put("green", 0x00FF00);
        presets.put("lightgreen", 0x90EE90);
        presets.put("darkgreen", 0x006400);
        presets.put("teal", 0x008080);
        presets.put("turquoise", 0x40E0D0);
        presets.put("cyan", 0x00FFFF);
        presets.put("lightblue", 0xADD8E6);
        presets.put("blue", 0x0000FF);
        presets.put("darkblue", 0x00008B);
        presets.put("navy", 0x000080);
        presets.put("lavender", 0xE6E6FA);
        presets.put("purple", 0x800080);
        presets.put("darkpurple", 0x4B0082);
        presets.put("magenta", 0xFF00FF);
        presets.put("hotpink", 0xFF1493);
        presets.put("pink", 0xFF69B4);
        presets.put("silver", 0xC0C0C0);
        presets.put("white", 0xFFFFFF);
        presets.put("black", 0x000000);
        PRESETS = Collections.unmodifiableMap(presets);
    }

    private static int chroma;
    private static long chromaAt;
    private static int chromaSpeed;

    private HitColors() {}

    public static boolean active() {
        return ConfigManager.hitColorEnabled && !EXTERNAL;
    }

    public static int currentRgb() {
        if (!ConfigManager.hitColorChroma) return ConfigManager.hitColor;
        long now = System.nanoTime();
        int speed = ConfigManager.hitColorChromaSpeed;
        if (now - chromaAt >= 16_000_000L || chromaSpeed != speed) {
            double cycle = (now / 50_000_000_000.0) * speed;
            chroma = Mth.hsvToRgb((float) (cycle - Math.floor(cycle)), 1, 1) & 0xFFFFFF;
            chromaAt = now;
            chromaSpeed = speed;
        }
        return chroma;
    }

    public static int rgbWithOverlayAlpha() {
        return Math.round(255 * (1 - ConfigManager.hitOpacity)) << 24 | currentRgb();
    }

    public static int route(int original, Object subject, Layer layer) {
        if (!layer.selected() || !(subject instanceof LivingEntityRenderState living) || !living.hasRedOverlay || !active())
            return original;
        return OverlayTexture.pack(0, layer.row);
    }

    public static int firstPerson(int original) {
        if (!ConfigManager.hitColorItems || !active()) return original;
        var player = Minecraft.getInstance().player;
        return player != null && (player.hurtTime > 0 || player.deathTime > 0)
            ? OverlayTexture.pack(0, Layer.ITEM.row) : original;
    }

    public static void reset() {
        ConfigManager.hitColorEnabled = false;
        ConfigManager.hitColor = 0xFF0000;
        ConfigManager.hitOpacity = .30f;
        ConfigManager.hitColorChroma = false;
        ConfigManager.hitColorChromaSpeed = 50;
        for (var layer : Layer.values()) layer.selected(layer == Layer.BODY);
        ConfigManager.requestSave();
    }
}
