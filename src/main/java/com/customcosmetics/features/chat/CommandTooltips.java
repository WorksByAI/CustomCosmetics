// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.chat;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;

/** Preview a click's actual command without modifying the stored message or executing it. */
public final class CommandTooltips {
    public static final String PREFIX="Click to execute command: ";
    private CommandTooltips(){}
    public static Style enrich(Style style){
        if(!ConfigManager.commandTooltipsEnabled||style==null||!(style.getClickEvent() instanceof ClickEvent.RunCommand run))return style;
        var hover=style.getHoverEvent();if(hover!=null&&!(hover instanceof HoverEvent.ShowText))return style;
        var previous=hover instanceof HoverEvent.ShowText text?text.value():null;
        // Cooperate with standalone tooltip mods or servers already showing the same real command.
        if(previous!=null&&previous.getString().lines().anyMatch(line->line.equals(PREFIX+run.command())))return style;
        var preview=Component.literal(PREFIX).withStyle(ChatFormatting.GRAY)
            .append(Component.literal(run.command()).withStyle(ChatFormatting.YELLOW));
        return style.withHoverEvent(new HoverEvent.ShowText(previous==null?preview:previous.copy().append("\n").append(preview)));
    }
}
