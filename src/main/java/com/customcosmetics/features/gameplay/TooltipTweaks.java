// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Athen ItemTweaks (Copyright 2025-2026 Starred). See NOTICE.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.items.ItemNameText;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TooltipTweaks {
    private static final Pattern CAKE = Pattern.compile("New Year Cake \\(Year (\\d+)\\)");
    private static final Pattern TAG = Pattern.compile("<(#?[0-9A-Fa-f]{6}|[a-z_]+)>");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.US).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("M/d/yy h:mm a", Locale.US);
    private static KeyMapping hexKey;
    private static boolean hexHeld;

    private TooltipTweaks() {}

    public static void init() {
        hexKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.tooltip_hex",
            InputConstants.UNKNOWN.getValue(), KeyMapping.Category.register(net.minecraft.resources.Identifier
                .fromNamespaceAndPath("customcosmetics", "tooltip_tweaks"))));
        ItemTooltipCallback.EVENT.register((stack, context, flags, lines) -> {
            if ((ConfigManager.tooltipHideGearScore || ConfigManager.tooltipHideVanillaEnchants
                    || ConfigManager.tooltipShowAge || ConfigManager.tooltipShowHex) && skyblock()) {
                updateTooltip(stack, lines, System.currentTimeMillis(), hexKey.isUnbound() || (hexHeld && Minecraft.getInstance().isWindowActive()));
            }
        });
    }

    public static void keyEvent(long window, int action, InputConstants.Key key) {
        if (hexKey != null && !hexKey.isUnbound() && hexKey.saveString().equals(key.getName())
                && Minecraft.getInstance().getWindow().handle() == window)
            hexHeld = action != InputConstants.RELEASE;
    }

    private static boolean skyblock() {
        var client = Minecraft.getInstance();
        return PresenceWorld.isHypixel(client) && PresenceWorld.isSkyblock(client);
    }

    private static CompoundTag attributes(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = ((LegacyPackItems.CustomDataView) (Object) data).customcosmetics$readOnlyTag();
        return tag.getCompound("ExtraAttributes").orElse(tag);
    }

    static void updateTooltip(ItemStack stack, List<Component> lines, long now, boolean showHex) {
        if (lines.isEmpty()) return;
        if (ConfigManager.tooltipHideGearScore || ConfigManager.tooltipHideVanillaEnchants) {
            lines.removeIf(line -> {
                String text = ChatFormatting.stripFormatting(line.getString());
                if (ConfigManager.tooltipHideGearScore && text.startsWith("Gear Score: ")) return true;
                if (!ConfigManager.tooltipHideVanillaEnchants || !(text.startsWith("Aqua Affinity") || text.startsWith("Depth Strider"))) return false;
                return line.getString().contains("§7") || line.visit((style, run) -> !run.isBlank() && style.getColor() != null
                    && style.getColor().getValue() == 0xAAAAAA ? Optional.of(true) : Optional.empty(), Style.EMPTY).isPresent();
            });
        }
        if (ConfigManager.tooltipShowAge) {
            var data = attributes(stack);
            long time = timestamp(data);
            if (time > 0 && time <= now) {
                String style = ConfigManager.tooltipAgeStyle.replace("#age", age(now - time))
                    .replace("#time", DATE.format(Instant.ofEpochMilli(time)));
                lines.add(Math.min(1, lines.size()), format(style));
            }
        }
        if (ConfigManager.tooltipShowHex && showHex) {
            var dye = stack.get(DataComponents.DYED_COLOR);
            if (dye != null) {
                int rgb = dye.rgb() & 0xFFFFFF;
                var line = format(ConfigManager.tooltipHexStyle.replace("#hex", ""))
                    .append(Component.literal(String.format(Locale.ROOT, "#%06X", rgb))
                        .withColor(ConfigManager.tooltipColorHex ? rgb : 0x555555));
                if (ConfigManager.tooltipColorBox) line.append(Component.literal("⬛").withColor(rgb));
                lines.add(Math.min(1, lines.size()), line);
            }
        }
    }

    private static long timestamp(CompoundTag data) {
        if (data == null) return 0;
        long time = data.getLongOr("timestamp", 0);
        if (time > 0) return time;
        String legacy = data.getStringOr("timestamp", "");
        if (legacy.isEmpty()) return 0;
        try {
            return LocalDateTime.parse(legacy, LEGACY_DATE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (java.time.format.DateTimeParseException ignored) {
            return 0;
        }
    }

    private static net.minecraft.network.chat.MutableComponent format(String input) {
        var tags = TAG.matcher(input);
        var legacy = new StringBuilder();
        while (tags.find()) {
            String name = tags.group(1);
            var color = com.customcosmetics.features.pack.TextColorOverrides.parse(name);
            if (color != null) tags.appendReplacement(legacy, String.format(Locale.ROOT, "&#%06X", color));
        }
        return ItemNameText.parse(tags.appendTail(legacy).toString()).copy();
    }

    private static String age(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return seconds / 60 + "m " + seconds % 60 + "s";
        if (seconds < 86400) return seconds / 3600 + "h " + seconds / 60 % 60 + "m";
        return seconds / 86400 + "d " + seconds / 3600 % 24 + "h";
    }

    public static void stars(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        if (!ConfigManager.tooltipItemStars || stack.isEmpty() || !skyblock()) return;
        var data = attributes(stack);
        if (data == null) return;
        int count = data.getIntOr("upgrade_level", data.getIntOr("dungeon_item_level", 0));
        if (count <= 0) return;
        String stars = Integer.toString(count);
        graphics.text(font, stars, x + 17 - font.width(stars), y + 18 - font.lineHeight, 0xFF000000 | ConfigManager.tooltipStarColor, true);
    }

    public static void cake(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (!ConfigManager.tooltipCakeYears || !stack.is(Items.CAKE) || !skyblock()) return;
        var year = CAKE.matcher(ChatFormatting.stripFormatting(stack.getHoverName().getString()));
        if (year.find()) graphics.text(Minecraft.getInstance().font, year.group(1), x, y + 8, 0xFF55FFFF, true);
    }
}
