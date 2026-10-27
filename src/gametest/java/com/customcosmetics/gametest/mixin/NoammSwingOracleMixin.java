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
    @Inject(method = {"getCurrentSwingDuration()I", "getModifiedSwingDuration(Lnet/minecraft/world/item/component/SwingAnimation;)I"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void customcosmeticstest$referenceDuration(CallbackInfoReturnable<Integer> ci) {
        if ((Object)this instanceof NoammSwingReferencePlayer reference) ci.setReturnValue(reference.referenceDuration);
    }
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), require = 0)
    private void customcosmeticstest$observedSwing(InteractionHand hand, boolean force, CallbackInfo ci) {
        NoammSwingGameTest.observe(this, hand);
    }
    @Inject(method = "updateSwingTime", at = @At("HEAD"), require = 0)
    private void customcosmeticstest$referenceTick(CallbackInfo ci) { NoammSwingGameTest.advance(this); }
    @Inject(method = "updateSwingTime", at = @At("TAIL"), require = 0)
    private void customcosmeticstest$compareNativeSwing(CallbackInfo ci) { NoammSwingGameTest.compare(this); }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), require = 0)
    private void customcosmeticstest$observedModernSwing(InteractionHand hand, net.minecraft.world.item.component.SwingAnimation animation,
            boolean force, CallbackInfoReturnable<Boolean> ci) { NoammSwingGameTest.observe(this, hand); }

    @Inject(method = "baseTick", at = @At("HEAD"), require = 0)
    private void customcosmeticstest$modernReferenceTick(CallbackInfo ci) { NoammSwingGameTest.advanceModern(this); }

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity$SwingState;tick()V", shift = At.Shift.AFTER), require = 0)
    private void customcosmeticstest$compareModernSwing(CallbackInfo ci) { NoammSwingGameTest.compare(this); }
}
