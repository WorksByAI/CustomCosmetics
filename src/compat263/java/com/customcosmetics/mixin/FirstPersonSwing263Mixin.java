// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.animations.ViewModelAnimations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity.SwingDescription;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonSwing263Mixin {
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;currentSwing:Lnet/minecraft/world/entity/LivingEntity$SwingDescription;"))
    private SwingDescription csb$retainVisualSwingType(AvatarRenderState avatar, Operation<SwingDescription> original,
            @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack) {
        var vanilla = original.call(avatar);
        var visualAnimation = ViewModelAnimations.activeSwingAnimation(hand, stack);
        if (visualAnimation == null || vanilla != null && vanilla.hand() == hand && vanilla.animation().equals(visualAnimation)) {
            return vanilla;
        }
        // The renderer still needs the saved swing type after the native snapshot expires.
        return new SwingDescription(hand, visualAnimation, visualAnimation.duration());
    }
}
