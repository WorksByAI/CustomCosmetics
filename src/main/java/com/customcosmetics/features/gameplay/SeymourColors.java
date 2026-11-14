// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.gui.UiText;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class SeymourColors {
    private record Lab(double lightness, double a, double b) {
        double distanceSquared(Lab other) {
            double dl = lightness - other.lightness, da = a - other.a, db = b - other.b;
            return dl * dl + da * da + db * db;
        }
    }

    private record Color(String name, Lab lab, boolean fade) {}
    private record Key(int rgb, boolean includeFades) {}
    public record Match(String name, double delta) {}

    private static final LinkedHashMap<Key, Match> CACHE = new LinkedHashMap<>(128, .75f, true);
    private static List<Color> colors;

    private SeymourColors() {}

    public static void init() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("seymouranalyzer")) return;
        ItemTooltipCallback.EVENT.register((stack, context, flags, lines) -> {
            if (!ConfigManager.seymourEnabled || !PresenceWorld.isSkyblock(Minecraft.getInstance())) return;
            var equipment = stack.get(DataComponents.EQUIPPABLE);
            if (equipment == null || equipment.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
            int rgb = color(stack);
            if (rgb < 0) return;
            var nearest = match(rgb, ConfigManager.seymourFadeDyes);
            if (nearest == null) return;
            lines.add(UiText.component("Dye Color: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.ROOT, "#%06X", rgb)).withColor(rgb)));
            lines.add(UiText.component("Closest Dye: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(nearest.name() + String.format(Locale.ROOT, " (ΔE %.2f)", nearest.delta())).withStyle(ChatFormatting.WHITE)));
        });
    }

    private static int color(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var parts = data.copyTag().getStringOr("color", "").split(":", -1);
            if (parts.length == 3) {
                try {
                    int r = Integer.parseInt(parts[0]), g = Integer.parseInt(parts[1]), b = Integer.parseInt(parts[2]);
                    if (r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255) return r << 16 | g << 8 | b;
                } catch (NumberFormatException ignored) { }
            }
        }
        var dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed == null ? -1 : dyed.rgb() & 0xFFFFFF;
    }

    public static Match match(int rgb, boolean includeFades) {
        var key = new Key(rgb, includeFades);
        var cached = CACHE.get(key);
        if (cached != null) return cached;
        if (colors == null) colors = readColors();
        var sample = lab(rgb);
        Color best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var color : colors) {
            if (color.fade() && !includeFades) continue;
            double candidate = sample.distanceSquared(color.lab());
            if (candidate < distance) {
                best = color;
                distance = candidate;
            }
        }
        if (best == null) return null;
        var result = new Match(best.name(), Math.sqrt(distance));
        if (CACHE.size() >= 256) CACHE.pollFirstEntry();
        CACHE.put(key, result);
        return result;
    }

    private static List<Color> readColors() {
        try (var input = SeymourColors.class.getResourceAsStream("/assets/customcosmetics/gameplay/seymour-colors.json")) {
            if (input == null) return List.of();
            var table = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var all = new ArrayList<Color>();
            for (var category : List.of("TARGET_COLORS", "FADE_DYES")) {
                for (var entry : table.getAsJsonObject(category).entrySet()) {
                    all.add(new Color(entry.getKey(), lab(Integer.parseInt(entry.getValue().getAsString(), 16)), category.equals("FADE_DYES")));
                }
            }
            return List.copyOf(all);
        } catch (java.io.IOException | RuntimeException invalid) {
            com.customcosmetics.client.CustomCosmeticsClient.LOGGER.warn("Could not load Seymour Analyzer colors", invalid);
            return List.of();
        }
    }

    private static Lab lab(int rgb) {
        double r = linear(rgb >>> 16 & 255), g = linear(rgb >>> 8 & 255), b = linear(rgb & 255);
        double x = labAxis((.4124564 * r + .3575761 * g + .1804375 * b) / .95047);
        double y = labAxis(.2126729 * r + .7151522 * g + .0721750 * b);
        double z = labAxis((.0193339 * r + .1191920 * g + .9503041 * b) / 1.08883);
        return new Lab(116 * y - 16, 500 * (x - y), 200 * (y - z));
    }

    private static double linear(int channel) {
        double s = channel / 255.0;
        return s > .04045 ? Math.pow((s + .055) / 1.055, 2.4) : s / 12.92;
    }

    private static double labAxis(double value) {
        return value > .008856 ? Math.cbrt(value) : 7.787 * value + 16.0 / 116;
    }
}
