// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.creature.CreatureModels;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class CreatureModelMixin {
    @Inject(method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
        at = @At("RETURN"), cancellable = true)
    private void customcosmetics$creatureModel(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> result) {
        if (entity instanceof AbstractClientPlayer player && result.getReturnValue() instanceof AvatarRenderState original) {
            result.setReturnValue(CreatureModels.replace(player, original, partialTick));
        }
    }
}
