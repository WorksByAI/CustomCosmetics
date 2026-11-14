// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur.v261.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import com.customcosmetics.features.motionblur.v261.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void naturalMotionBlur$afterRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        ShaderManager.applyDeferredTemporalBlur();
        ShaderManager.clearFrameAllocator();
    }
}
