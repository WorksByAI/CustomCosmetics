// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ItemNameText {
    private static final java.util.Map<String, Component> CACHE = new java.util.LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Component> entry) { return size() > 1024; }
    };
    private ItemNameText() {}
    /** Client-thread display cache; formatted names do not depend on the frame or world. */
    public static Component cached(String raw) { return CACHE.computeIfAbsent(raw, ItemNameText::parse); }
    /** Formatting is display-only; no click events, commands or markup are evaluated. */
    public static Component parse(String raw) {
        return parse(raw, Style.EMPTY.withItalic(false));
    }

    public static Component parse(String raw, Style initial) {
        MutableComponent result = Component.empty();
        Style style = initial;
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < raw.length();) {
            int length = codeLength(raw, i);
            if (length == 0) {
                segment.append(raw.charAt(i++));
                continue;
            }
            if (!segment.isEmpty()) {
                result.append(Component.literal(segment.toString()).setStyle(style));
                segment.setLength(0);
            }
            if (length == 8) style = initial.withColor(Integer.parseInt(raw, i + 2, i + 8, 16));
            else {
                var format = ChatFormatting.getByCode(Character.toLowerCase(raw.charAt(i + 1)));
                if (format == ChatFormatting.RESET) style = initial;
                else style = (Character.digit(raw.charAt(i + 1), 16) >= 0 ? initial : style).applyFormat(format);
            }
            i += length;
        }
        if (!segment.isEmpty()) result.append(Component.literal(segment.toString()).setStyle(style));
        return result;
    }

    public static String limitVisible(String raw, int limit) {
        int end = 0, visible = 0;
        for (int i = 0; i < raw.length() && visible < limit;) {
            int length = codeLength(raw, i);
            if (length > 0) i += length;
            else {
                i += Character.charCount(raw.codePointAt(i));
                end = i;
                visible++;
            }
        }
        return raw.substring(0, end);
    }

    private static int codeLength(String raw, int start) {
        if (start + 1 >= raw.length() || (raw.charAt(start) != '&' && raw.charAt(start) != '§')) return 0;
        char code = raw.charAt(start + 1);
        if (ChatFormatting.getByCode(Character.toLowerCase(code)) != null) return 2;
        if (code != '#' || start + 8 > raw.length()) return 0;
        for (int i = start + 2; i < start + 8; i++) {
            if ("0123456789abcdefABCDEF".indexOf(raw.charAt(i)) < 0) return 0;
        }
        return 8;
    }
}
