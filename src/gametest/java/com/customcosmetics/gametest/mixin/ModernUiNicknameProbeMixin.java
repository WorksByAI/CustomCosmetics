// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.compatibility.ModernUiNicknameText;
import com.customcosmetics.gametest.ModernUiNicknameGameTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.customcosmetics.features.nickname.GradientGlyph;
public final class ModernUiNicknameProbeMixin {
    @Mixin(ModernUiNicknameText.Palette.class)
    public static class Palette {
        @ModifyArg(method="wrap",at=@At(value="INVOKE",target="Lcom/customcosmetics/features/nickname/GradientGlyph$GradientVertices;<init>(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/customcosmetics/features/nickname/GradientGlyph$QuadSelector;J)V"),index=2)
        private long customcosmetics$clock(long actual){return ModernUiNicknameGameTest.time;}
        @Inject(method="wrap",at=@At("HEAD"))
        private void customcosmetics$guiPass(CallbackInfoReturnable<GradientGlyph.GradientVertices> ci){ModernUiNicknameGameTest.passes.incrementAndGet();}
    }
    @Mixin(ModernUiNicknameText.Buffers.class)
    public static class Buffers {
        @Inject(method="<init>",at=@At("TAIL"))
        private void customcosmetics$worldPass(CallbackInfo ci){ModernUiNicknameGameTest.worldPasses.incrementAndGet();}
    }
}
