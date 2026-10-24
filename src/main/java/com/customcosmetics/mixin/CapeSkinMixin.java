// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.features.cape.CapeCosmetics;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public abstract class CapeSkinMixin {
    @ModifyReturnValue(method="getSkin",at=@At("RETURN"))
    private PlayerSkin customcosmetics$cape(PlayerSkin original){
        var player=(AbstractClientPlayer)(Object)this;
        return com.customcosmetics.features.skins.LocalSkins.skin(player.getUUID(),CapeCosmetics.skin(player.getUUID(),original));
    }
}
