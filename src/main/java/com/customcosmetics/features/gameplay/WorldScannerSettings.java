// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Nebulune WorldScanner, Copyright (c) 2025 Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import java.util.EnumMap;

public final class WorldScannerSettings {
    private WorldScannerSettings() {}

    public enum Target {
        FAIRY_GROTTO("Fairy Grotto", 0xFF55FF), SAPPHIRE("Sapphire Crystal", 0x55FFFF), AMBER("Amber Crystal", 0xFFAA00),
        AMETHYST("Amethyst Crystal", 0xAA00AA), JADE("Jade Crystal", 0x00AA00), TOPAZ("Topaz Crystal", 0xFFFF55),
        CORLEONE("Corleone", 0x55FF55), GOLDEN_DRAGON("Golden Dragon", 0xFFFFFF), KEY_GUARDIAN("Key Guardian", 0xAA00AA),
        XALX("Xalx", 0x506E00), PETE("Pete", 0x6E2A00), ODAWA("Odawa", 0xAAAAAA), WORM_FISHING("Worm Fishing", 0xFF5555);

        public final String label;
        private final Options defaults;
        Target(String label, int color) {
            this.label = label;
            defaults = new Options(true, 2, color, false, true, 1, .5, true);
        }

        static Target of(String key) {
            return switch (key) {
                case "KING", "QUEEN" -> AMBER;
                case "DIVAN" -> JADE;
                case "CITY" -> SAPPHIRE;
                case "TEMPLE" -> AMETHYST;
                case "BAL" -> TOPAZ;
                case "CORLEONE_DOCK", "CORLEONE_HOLE" -> CORLEONE;
                case "KEY_GUARDIAN_SPIRAL", "KEY_GUARDIAN_TOWER" -> KEY_GUARDIAN;
                default -> valueOf(key);
            };
        }

        public Options options() {
            var saved = ConfigManager.scannerOptions.get(this);
            return saved == null ? defaults : saved;
        }

        public void set(Options options) {
            var saved = new EnumMap<>(ConfigManager.scannerOptions);
            saved.put(this, options);
            ConfigManager.scannerOptions = saved;
        }
    }

    public record Options(boolean enabled, int style, int color, boolean tracer, boolean name, double scale, double background, boolean chat) {
        public Options {
            style = Math.clamp(style, 0, 2);
            color &= 0xFFFFFF;
            scale = Double.isFinite(scale) ? Math.clamp(scale, 0, 1) : 1;
            background = Double.isFinite(background) ? Math.clamp(background, 0, 1) : .5;
        }

        public Options enabled(boolean value) { return new Options(value, style, color, tracer, name, scale, background, chat); }
        public Options style(int value) { return new Options(enabled, value, color, tracer, name, scale, background, chat); }
        public Options color(int value) { return new Options(enabled, style, value, tracer, name, scale, background, chat); }
        public Options tracer(boolean value) { return new Options(enabled, style, color, value, name, scale, background, chat); }
        public Options name(boolean value) { return new Options(enabled, style, color, tracer, value, scale, background, chat); }
        public Options scale(double value) { return new Options(enabled, style, color, tracer, name, value, background, chat); }
        public Options background(double value) { return new Options(enabled, style, color, tracer, name, scale, value, chat); }
        public Options chat(boolean value) { return new Options(enabled, style, color, tracer, name, scale, background, value); }
    }
}
