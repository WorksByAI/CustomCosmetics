// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

/** Local preferences resolve to the same display data sent to other clients. */
public record SkyBlockLevel(int value, List<Integer> colors) {
    public static final int MAX_DIGITS = 5, MAX_LEVEL = 99_999;
    public static final List<ChatFormatting> TIERS = List.of(
        ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.YELLOW, ChatFormatting.GREEN,
        ChatFormatting.DARK_GREEN, ChatFormatting.AQUA, ChatFormatting.DARK_AQUA, ChatFormatting.BLUE,
        ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD,
        ChatFormatting.RED, ChatFormatting.DARK_RED
    );

    public SkyBlockLevel {
        colors = List.copyOf(colors);
    }

    public static int tierColor(int level) {
        return TextColor.fromLegacyFormat(TIERS.get(Math.clamp(level / 40, 0, TIERS.size() - 1))).getValue();
    }

    public static SkyBlockLevel local() {
        return ConfigManager.skyBlockLevelEnabled ? configured() : null;
    }

    public static SkyBlockLevel configured() {
        return resolve(ConfigManager.skyBlockLevelValue, ConfigManager.skyBlockLevelColorMode,
            ConfigManager.skyBlockLevelColor, ConfigManager.skyBlockLevelDigitColors);
    }

    static SkyBlockLevel resolve(int level, String mode, int customColor, List<Integer> digits) {
        int fallback = tierColor(level);
        ChatFormatting preset = preset(mode);
        var colors = new ArrayList<Integer>(MAX_DIGITS);
        int length = Integer.toString(level).length();
        for (int i = 0; i < length; i++) {
            int color = switch (mode) {
                case "custom" -> customColor;
                case "digits" -> i < digits.size() ? digits.get(i) : -1;
                default -> preset == null ? fallback : TextColor.fromLegacyFormat(preset).getValue();
            };
            colors.add(color < 0 ? fallback : color);
        }
        return new SkyBlockLevel(level, colors);
    }

    public static void normalizeConfig() {
        ConfigManager.skyBlockLevelValue = Math.clamp(ConfigManager.skyBlockLevelValue, 0, MAX_LEVEL);
        ConfigManager.skyBlockLevelColorMode = normalizeMode(ConfigManager.skyBlockLevelColorMode);
        ConfigManager.skyBlockLevelColor &= 0xFFFFFF;
        ConfigManager.skyBlockLevelDigitColors = normalizeDigits(ConfigManager.skyBlockLevelDigitColors);
        ConfigManager.skyBlockLevelColorPresets = SkyBlockLevelColors.normalizePresets(ConfigManager.skyBlockLevelColorPresets);
    }

    static String normalizeMode(String mode) {
        return mode != null && (List.of("auto", "digits", "custom").contains(mode) || preset(mode) != null) ? mode : "auto";
    }

    static List<Integer> normalizeDigits(List<Integer> digits) {
        if (digits == null || digits.isEmpty()) return List.of();
        var valid = new ArrayList<Integer>(MAX_DIGITS);
        for (int i = 0; i < Math.min(digits.size(), MAX_DIGITS); i++) {
            Integer color = digits.get(i);
            valid.add(color != null && color >= 0 && color <= 0xFFFFFF ? color : -1);
        }
        return List.copyOf(valid);
    }

    private static ChatFormatting preset(String name) {
        for (var tier : TIERS) if (tier.name().equalsIgnoreCase(name)) return tier;
        return null;
    }

    public static void encode(JsonObject features, SkyBlockLevel display) {
        if (display == null) return;
        var level = new JsonObject();
        level.addProperty("value", display.value());
        var colors = new JsonArray();
        display.colors().forEach(colors::add);
        level.add("colors", colors);
        features.add("skyBlockLevel", level);
    }

    public static SkyBlockLevel decode(JsonElement element) {
        if (!(element instanceof JsonObject object) || !(object.get("colors") instanceof JsonArray entries)) return null;
        int level = integer(object.get("value"), MAX_LEVEL);
        if (level < 0 || entries.size() != Integer.toString(level).length()) return null;
        var colors = new ArrayList<Integer>(entries.size());
        for (JsonElement entry : entries) {
            int color = integer(entry, 0xFFFFFF);
            if (color < 0) return null;
            colors.add(color);
        }
        return new SkyBlockLevel(level, colors);
    }

    private static int integer(JsonElement element, int max) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return -1;
        double value = element.getAsDouble();
        return value >= 0 && value <= max && value == Math.rint(value) ? (int) value : -1;
    }
}
