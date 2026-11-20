// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.GuiSprites;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public class RoundedGuiSamplingMixin {
    @ModifyExpressionValue(method = "innerBlit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/AbstractTexture;getSampler()Lcom/mojang/blaze3d/textures/GpuSampler;"))
    private GpuSampler csb$sampleShapeCoverage(GpuSampler original) {
        return GuiSprites.drawingRoundedMask() ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR) : original;
    }
}
