// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.features.cape.CapeCosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class CapeSkinMixin {
    @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
    private void customcosmetics$cape(CallbackInfoReturnable<PlayerSkin> callback){
        var player=(AbstractClientPlayer)(Object)this;
        callback.setReturnValue(CapeCosmetics.skin(player.getUUID(),callback.getReturnValue()));
    }
}
