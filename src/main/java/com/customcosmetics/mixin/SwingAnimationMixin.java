// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.animations.ViewModelAnimations;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-only swing notification, including swings too fast to remain visible at the next tick. */
public final class SwingAnimationMixin {
    @Mixin(LivingEntity.class)
    public static class Legacy {
        @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
        private void customcosmetics$observeSwing(InteractionHand hand, boolean force, CallbackInfo ci) {
            ViewModelAnimations.observeSwing(this, hand);
        }
        @Inject(method = "updateSwingTime", at = @At("HEAD"))
        private void customcosmetics$advanceSwing(CallbackInfo ci) {
            ViewModelAnimations.advanceSwing(this);
        }
    }
    @Mixin(LivingEntity.class)
    public static class Modern {
        @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"))
        private void customcosmetics$observeSwing(InteractionHand hand, SwingAnimation animation, boolean force,
                CallbackInfoReturnable<Boolean> ci) {
            ViewModelAnimations.observeSwing(this, hand);
        }
        // 26.3 moved the native SwingState.tick call to the start of baseTick.
        @Inject(method = "baseTick", at = @At("HEAD"))
        private void customcosmetics$advanceSwing(CallbackInfo ci) {
            ViewModelAnimations.advanceSwing(this);
        }
    }
}
