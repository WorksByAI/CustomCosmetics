// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.features.blocking.BlockingPose;
import com.customcosmetics.gametest.RenderAssertions;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockingPose.class)
public class PoseProbeMixin {
    @ModifyVariable(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;IFF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static float customcosmeticstest$samplePhase(float swing) {
        return Float.isFinite(RenderAssertions.forcedBlockingSwing) ? RenderAssertions.forcedBlockingSwing : swing;
    }
    @Unique private static final ThreadLocal<org.joml.Matrix4f> customcosmeticstest$before = new ThreadLocal<>();
    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;IFF)V", at = @At("HEAD"))
    private static void customcosmeticstest$before(PoseStack pose, int side, float swing, float equip, CallbackInfo ci) {
        if (RenderAssertions.trackBlockingDepth) customcosmeticstest$before.set(new org.joml.Matrix4f(pose.last().pose()));
    }
    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;IFF)V", at = @At("TAIL"))
    private static void customcosmeticstest$rendered(PoseStack pose, int side, float swing, float equip, CallbackInfo ci) {
        RenderAssertions.calls.incrementAndGet();
        RenderAssertions.blockingSwing = swing;
        RenderAssertions.renderedBlockingStyle = com.customcosmetics.features.blocking.BlockingStyle
            .parse(com.customcosmetics.config.ConfigManager.swordBlockingStyle).value();
        RenderAssertions.blockingEquip = equip;
        RenderAssertions.blockingY = pose.last().pose().m31();
        var before = customcosmeticstest$before.get(); customcosmeticstest$before.remove();
        if (RenderAssertions.trackBlockingDepth && before != null) {
            float depth = before.invert().mul(pose.last().pose()).m32();
            RenderAssertions.minBlockingDepth = Math.min(RenderAssertions.minBlockingDepth, depth);
            RenderAssertions.maxBlockingDepth = Math.max(RenderAssertions.maxBlockingDepth, depth);
            RenderAssertions.depthCalls.incrementAndGet();
        }
    }
}
