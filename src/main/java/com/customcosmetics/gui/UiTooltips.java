// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Cached by the native Tooltip; Unicode wrapping runs once, never for every hover frame. */
public final class UiTooltips {
    private UiTooltips(){}
    public static List<FormattedCharSequence> split(Minecraft client,Component message){
        var screen=com.customcosmetics.compatibility.ClientUiAccess.screen(client);
        int width=Math.max(16,Math.min(320,screen.width-24));
        var font=UiFonts.get(client.font);
        String text=message.getString();
        if(!message.getSiblings().isEmpty()||text.codePoints().noneMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))return font.split(message,width);
        var style=message.getStyle();
        return UiParagraphWrap.lines(text,width,line->font.width(Component.literal(line).withStyle(style))).stream()
            .map(line->Component.literal(line).withStyle(style).getVisualOrderText()).toList();
    }
}
