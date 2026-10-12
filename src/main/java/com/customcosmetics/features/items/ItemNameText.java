// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ItemNameText {
    private ItemNameText() {}
    /** Formatting is display-only; no click events, commands or markup are evaluated. */
    public static Component parse(String raw) {
        MutableComponent result = Component.empty(); Style style = Style.EMPTY.withItalic(false);
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < raw.length()) {
                ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(raw.charAt(i + 1)));
                Integer hex = null;
                if (raw.charAt(i + 1) == '#' && i + 7 < raw.length() && raw.substring(i + 2,i + 8).matches("[a-fA-F0-9]{6}"))
                    hex = Integer.parseInt(raw.substring(i + 2,i + 8),16);
                if (format != null || hex != null) {
                    if (!segment.isEmpty()) { result.append(Component.literal(segment.toString()).setStyle(style)); segment.setLength(0); }
                    if (hex != null) { style = Style.EMPTY.withItalic(false).withColor(hex); i += 7; }
                    else {
                        if (format == ChatFormatting.RESET) style = Style.EMPTY.withItalic(false);
                        else if (Character.digit(raw.charAt(i + 1), 16) >= 0) style = Style.EMPTY.withItalic(false).applyFormat(format);
                        else style = style.applyFormat(format);
                        i++;
                    }
                    continue;
                }
            }
            segment.append(c);
        }
        if (!segment.isEmpty()) result.append(Component.literal(segment.toString()).setStyle(style));
        return result;
    }
}
