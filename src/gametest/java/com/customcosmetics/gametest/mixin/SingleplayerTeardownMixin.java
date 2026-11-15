// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
abstract class SingleplayerTeardownMixin {
    @WrapWithCondition(method = "halt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private boolean customSkyBlock$kickPublishedWorldGuests(IntegratedServer server, Runnable kickGuests) {
        // An unpublished test world has no LAN guests. Waiting for this task blocks Fabric's tick barrier.
        return server.isPublished();
    }
}
