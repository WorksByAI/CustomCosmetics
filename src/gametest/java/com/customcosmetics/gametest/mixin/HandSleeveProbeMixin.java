// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.gametest.RenderAssertions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inspect the actual first-person hand submission after vanilla resets the parent arm. */
@Mixin(AvatarRenderer.class)
public class HandSleeveProbeMixin {
    @Inject(method = "renderHand", at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"),
        @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/UvMapping;)V")})
    private void checkSleeve(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin,
            ModelPart arm, boolean visibleSleeve, CallbackInfo ci) {
        if (!RenderAssertions.trackHandSleeves) return;
        var model = (net.minecraft.client.model.player.PlayerModel)((AvatarRenderer)(Object)this).getModel();
        var sleeve = arm == model.rightArm ? model.rightSleeve : model.leftSleeve;
        if (Math.abs(sleeve.xRot) > .0001f || Math.abs(sleeve.yRot) > .0001f || Math.abs(sleeve.zRot) > .0001f)
            RenderAssertions.firstPersonSleeveMismatch.incrementAndGet();
        RenderAssertions.firstPersonHandChecks.incrementAndGet();
    }
}
