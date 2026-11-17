// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.sync.ModModeration;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ModerationDisconnectMixin {
    @WrapOperation(method = "createDisconnectScreen", at = @At(value = "NEW",
        target = "(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/DisconnectionDetails;)Lnet/minecraft/client/gui/screens/DisconnectedScreen;"))
    private DisconnectedScreen csb$styledDisconnect(Screen parent, Component title, DisconnectionDetails details,
            Operation<DisconnectedScreen> original) {
        var ban = ModModeration.takeBanScreen(details.reason());
        return ban == null ? original.call(parent, title, details) : ban.create(parent);
    }
}
