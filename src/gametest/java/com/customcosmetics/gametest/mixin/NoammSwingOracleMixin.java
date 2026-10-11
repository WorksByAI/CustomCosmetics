// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.gametest.NoammSwingGameTest;
import com.customcosmetics.gametest.NoammSwingReferencePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NA's original hook is restricted to an unspawned reference player; it never modifies the real player. */
@Mixin(LivingEntity.class)
public class NoammSwingOracleMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void customcosmeticstest$referenceDuration(CallbackInfoReturnable<Integer> ci) {
        if ((Object)this instanceof NoammSwingReferencePlayer reference) ci.setReturnValue(reference.referenceDuration);
    }
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void customcosmeticstest$observedSwing(InteractionHand hand, boolean force, CallbackInfo ci) {
        NoammSwingGameTest.observe(this, hand);
    }
    @Inject(method = "updateSwingTime", at = @At("HEAD"))
    private void customcosmeticstest$referenceTick(CallbackInfo ci) { NoammSwingGameTest.advance(this); }
    @Inject(method = "updateSwingTime", at = @At("TAIL"))
    private void customcosmeticstest$compareNativeSwing(CallbackInfo ci) { NoammSwingGameTest.compare(this); }
}
