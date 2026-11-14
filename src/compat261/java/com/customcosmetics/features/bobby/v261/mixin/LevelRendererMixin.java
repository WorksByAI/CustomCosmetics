// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v261.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.customcosmetics.features.bobby.v261.ext.GameRendererExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow public abstract double getLastViewDistance();

    @Shadow @Final private Minecraft minecraft;

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private GpuBufferSlice clampMaxValue(GpuBufferSlice fogBuffer) {
        if (getLastViewDistance() >= 32) {
            fogBuffer = ((GameRendererExt) minecraft.gameRenderer).bobby_getSkyFogRenderer().getBuffer(FogRenderer.FogMode.WORLD);
        }
        return fogBuffer;
    }
}
