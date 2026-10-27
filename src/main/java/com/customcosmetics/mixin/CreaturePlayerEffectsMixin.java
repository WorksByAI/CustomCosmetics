// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.creature.CreatureModels;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Preserve the native avatar used for 26.3 first-person hands and screen effects. */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.extract.LevelExtractor")
public abstract class CreaturePlayerEffectsMixin {
    @WrapOperation(method = "extractPlayerState", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState customcosmetics$nativeAvatar(@Coerce Object extractor, Entity player,
            float partialTick, Operation<EntityRenderState> original) {
        boolean previous = CreatureModels.extractingPlayerEffects;
        CreatureModels.extractingPlayerEffects = true;
        try {
            return original.call(extractor, player, partialTick);
        } finally {
            CreatureModels.extractingPlayerEffects = previous;
        }
    }
}
