// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.ConfigManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Local color presets apply to the current level, independent of whether the override is enabled. */
public record SkyBlockLevelColors(String mode, int color, List<Integer> digits) {
    public SkyBlockLevelColors {
        mode = SkyBlockLevel.normalizeMode(mode);
        color &= 0xFFFFFF;
        digits = SkyBlockLevel.normalizeDigits(digits);
    }

    public static SkyBlockLevelColors capture() {
        return new SkyBlockLevelColors(ConfigManager.skyBlockLevelColorMode,
            ConfigManager.skyBlockLevelColor, ConfigManager.skyBlockLevelDigitColors);
    }

    public void apply() {
        ConfigManager.skyBlockLevelColorMode = mode;
        ConfigManager.skyBlockLevelColor = color;
        ConfigManager.skyBlockLevelDigitColors = digits;
    }

    public SkyBlockLevel preview(int value) {
        return SkyBlockLevel.resolve(value, mode, color, digits);
    }

    public static void savePreset(String rawName) {
        String name = NicknamePresetStore.name(rawName);
        var next = new TreeMap<String, SkyBlockLevelColors>(String.CASE_INSENSITIVE_ORDER);
        next.putAll(ConfigManager.skyBlockLevelColorPresets);
        if (!next.containsKey(name) && next.size() >= NicknamePresetStore.MAX_PRESETS)
            throw new IllegalArgumentException("You can save up to 64 presets.");
        next.put(name, capture());
        ConfigManager.skyBlockLevelColorPresets = Collections.unmodifiableMap(next);
    }

    public static void removePreset(String name) {
        var next = new TreeMap<String, SkyBlockLevelColors>(String.CASE_INSENSITIVE_ORDER);
        next.putAll(ConfigManager.skyBlockLevelColorPresets);
        next.remove(name);
        ConfigManager.skyBlockLevelColorPresets = Collections.unmodifiableMap(next);
    }

    static Map<String, SkyBlockLevelColors> normalizePresets(Map<String, SkyBlockLevelColors> presets) {
        if (presets == null || presets.isEmpty()) return Map.of();
        var next = new TreeMap<String, SkyBlockLevelColors>(String.CASE_INSENSITIVE_ORDER);
        for (var entry : presets.entrySet()) {
            if (next.size() == NicknamePresetStore.MAX_PRESETS) break;
            if (entry.getValue() == null) continue;
            try { next.putIfAbsent(NicknamePresetStore.name(entry.getKey()), entry.getValue()); }
            catch (IllegalArgumentException invalid) { /* Retain the other presets. */ }
        }
        return Collections.unmodifiableMap(next);
    }
}
