// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.features.nickname.GradientGlyph;
import com.customcosmetics.gametest.RenderAssertions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GradientGlyph.class)
public class NicknameGradientProbeMixin {
    @ModifyArg(method="render",at=@At(value="INVOKE",target="Lcom/customcosmetics/features/nickname/GradientGlyph;acquireVertices(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/customcosmetics/features/nickname/NicknameGlyphEffect;FFFIJ)Lcom/customcosmetics/features/nickname/GradientGlyph$GradientVertices;"),index=6)
    private long customcosmetics$testClock(long now) { return RenderAssertions.nicknameTime < 0 ? now : RenderAssertions.nicknameTime; }
    @Inject(method="render",at=@At("HEAD"))
    private void customcosmetics$countGlyphs(CallbackInfo ci) { RenderAssertions.nicknameGlyphs.incrementAndGet(); }
}
