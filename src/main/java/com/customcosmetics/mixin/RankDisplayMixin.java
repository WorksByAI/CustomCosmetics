// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.nickname.*;
import com.customcosmetics.features.skins.LocalSkins;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class RankDisplayMixin {
    @Mixin(PlayerTabOverlay.class)
    public static class Tab {
        @WrapMethod(method="getNameForDisplay")
        private Component customcosmetics$tabRank(PlayerInfo player,Operation<Component> original){
            var name=SkyBlockLevelAppearance.tab(original.call(player),player.getProfile().name());
            if(!RankAppearance.active())return name;
            return RankAppearance.tabName(name,PresenceWorld.isSkyblock(Minecraft.getInstance()));
        }

        @WrapOperation(method="extractRenderState", at=@At(value="INVOKE",
            target="Lnet/minecraft/client/multiplayer/PlayerInfo;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"))
        private PlayerSkin customcosmetics$tabSkin(PlayerInfo player, Operation<PlayerSkin> original) {
            return LocalSkins.tabSkin(player, original.call(player));
        }
    }
    @Mixin(PlayerInfo.class)
    public static class RosterSkin {
        @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
        private void customcosmetics$maskRosterSkin(CallbackInfoReturnable<PlayerSkin> callback) {
            var original = callback.getReturnValue();
            var masked = LocalSkins.rosterSkin((PlayerInfo) (Object) this, original);
            if (original != masked) callback.setReturnValue(masked);
        }
    }

    @Mixin(ChatComponent.class)
    public static class Chat {
        @WrapMethod(method="addMessageToDisplayQueue")
        private void customcosmetics$chatLevel(GuiMessage message, Operation<Void> original) {
            var text = message.content();
            var rendered = message.source() == GuiMessageSource.SYSTEM_CLIENT ? text : SkyBlockLevelAppearance.chat(text);
            if (rendered == text) original.call(message);
            else original.call(new GuiMessage(message.addedTime(), rendered, message.signature(), message.source(), message.tag()));
        }

        @WrapMethod(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V")
        private void customcosmetics$chatRank(GuiGraphicsExtractor graphics,Font font,int tick,int x,int y,
            ChatComponent.DisplayMode mode,boolean focused,Operation<Void> original){
            if(!RankAppearance.active()){original.call(graphics,font,tick,x,y,mode,focused);return;}
            try(var scope=RankDisplayContext.enter(RankText.Context.CHAT)){original.call(graphics,font,tick,x,y,mode,focused);}
        }
    }
}
