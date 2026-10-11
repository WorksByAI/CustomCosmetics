// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.features.animations.ViewModelAnimations;
import com.customcosmetics.gametest.RenderAssertions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewModelAnimations.class)
public class AnimationProbeMixin {
    @Inject(method = "model", at = @At("TAIL"))
    private static void model(PoseStack pose, int side, CallbackInfo ci) { RenderAssertions.models.incrementAndGet(); }
    @Inject(method = "swing", at = @At("RETURN"))
    private static void swing(float vanilla, float partial, InteractionHand hand, ItemStack stack, CallbackInfoReturnable<Float> ci) {
        if (hand == InteractionHand.MAIN_HAND) RenderAssertions.swing = ci.getReturnValue();
        com.customcosmetics.gametest.NoammSwingGameTest.compareRendered(ci.getReturnValue(), partial, hand);
    }
    @Inject(method = "equip", at = @At("RETURN"))
    private static void equip(float vanilla, CallbackInfoReturnable<Float> ci) { RenderAssertions.equip = ci.getReturnValue(); }
    @Inject(method = "handMovement", at = @At("RETURN"))
    private static void movement(CallbackInfoReturnable<Boolean> ci) { RenderAssertions.movement = ci.getReturnValue(); }
}
