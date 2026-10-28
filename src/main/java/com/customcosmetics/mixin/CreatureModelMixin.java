// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.creature.CreatureModels;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class CreatureModelMixin {
    @ModifyReturnValue(method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
        at = @At("RETURN"))
    private EntityRenderState customcosmetics$creatureModel(EntityRenderState result, Entity entity, float partialTick) {
        if (!CreatureModels.extractingPlayerEffects && entity instanceof AbstractClientPlayer player && result instanceof AvatarRenderState original) {
            if(!com.customcosmetics.client.ModErrors.active(com.customcosmetics.client.ModErrors.Key.CREATURE))return original;
            try{return CreatureModels.replace(player, original, partialTick);}
            catch(RuntimeException|LinkageError|AssertionError failure){com.customcosmetics.client.ModErrors.report(com.customcosmetics.client.ModErrors.Key.CREATURE,failure);return original;}
        }
        return result;
    }
}
