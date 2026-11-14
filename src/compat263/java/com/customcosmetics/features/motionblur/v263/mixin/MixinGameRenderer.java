// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur.v263.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.GameRenderer;
import com.customcosmetics.features.motionblur.v263.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"
            )
    )
    private void naturalMotionBlur$captureWorldProjection(CallbackInfo ci, @Local(name = "projectionMatrix") Matrix4f projectionMatrix) {
        ShaderManager.captureWorldProjection(projectionMatrix);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void naturalMotionBlur$afterRenderLevel(CallbackInfo ci) {
        ShaderManager.applyDeferredTemporalBlur();
        ShaderManager.clearFrameAllocator();
    }
}
