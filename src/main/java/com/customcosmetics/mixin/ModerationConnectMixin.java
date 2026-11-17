// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.sync.ModModeration;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ConnectScreen.class)
public abstract class ModerationConnectMixin {
    @Shadow @Final private Screen parent;

    @WrapOperation(method = "startConnecting", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/ConnectScreen;connect(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;Lnet/minecraft/client/multiplayer/TransferState;)V"))
    private static void csb$checkServerRestriction(ConnectScreen screen, Minecraft client, ServerAddress address,
            ServerData server, TransferState transfer, Operation<Void> original) {
        ModModeration.beforeConnect(client, screen, ((ModerationConnectMixin) (Object) screen).parent, address,
            () -> original.call(screen, client, address, server, transfer));
    }
}
