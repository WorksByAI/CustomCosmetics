// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.nickname.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

public final class RankDisplayMixin {
    @Mixin(PlayerTabOverlay.class)
    public static class Tab {
        @WrapMethod(method="getNameForDisplay")
        private Component customcosmetics$tabRank(PlayerInfo player,Operation<Component> original){
            var name=original.call(player);
            if(!RankAppearance.active())return name;
            return RankAppearance.tabName(name,PresenceWorld.isSkyblock(Minecraft.getInstance()));
        }
    }
    @Mixin(ChatComponent.class)
    public static class Chat {
        @WrapMethod(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V")
        private void customcosmetics$chatRank(GuiGraphicsExtractor graphics,Font font,int tick,int x,int y,
            ChatComponent.DisplayMode mode,boolean focused,Operation<Void> original){
            if(!RankAppearance.active()){original.call(graphics,font,tick,x,y,mode,focused);return;}
            try(var scope=RankDisplayContext.enter(RankText.Context.CHAT)){original.call(graphics,font,tick,x,y,mode,focused);}
        }
    }
}
