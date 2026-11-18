// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.sync.ModModeration;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

public final class ModerationConnectMixin {
    @Mixin(ConnectScreen.class)
    public static class ScreenHook implements ModModeration.LoginAccess {
        @Shadow @Final private Screen parent;
        @Shadow private volatile boolean aborted;
        @Unique private Supplier<ModModeration.BanScreen> csb$loginCheck = () -> null;

        @Inject(method = "connect", at = @At("HEAD"))
        private void csb$startLoginCheck(Minecraft client, ServerAddress address, ServerData server,
                TransferState transfer, CallbackInfo callback) {
            csb$loginCheck = ModModeration.startLoginCheck(client, address);
        }

        @Override
        public ModModeration.BanScreen csb$awaitRestriction() {
            return aborted ? null : csb$loginCheck.get();
        }

        @Override
        public void csb$showRestriction(Minecraft client, ModModeration.BanScreen restriction) {
            client.execute(() -> {
                if (!aborted && ClientUiAccess.screen(client) == (Object) this)
                    ClientUiAccess.setScreen(client, restriction.create(parent));
            });
        }
    }

    @Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
    public static class Connector {
        @Shadow @Final ConnectScreen this$0;

        @Inject(method = "run", at = @At(value = "INVOKE",
            target = "Lio/netty/channel/ChannelFuture;syncUninterruptibly()Lio/netty/channel/ChannelFuture;",
            shift = At.Shift.AFTER), cancellable = true)
        private void csb$checkBeforeLogin(CallbackInfo callback, @Local Connection connection) {
            var screen = (ModModeration.LoginAccess) (Object) this$0;
            var restriction = screen.csb$awaitRestriction();
            if (restriction == null) return;
            connection.disconnect(restriction.body());
            screen.csb$showRestriction(Minecraft.getInstance(), restriction);
            callback.cancel();
        }
    }
}
