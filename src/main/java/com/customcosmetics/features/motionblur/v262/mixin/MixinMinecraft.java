// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur.v262.mixin;

import net.minecraft.client.Minecraft;
import com.customcosmetics.features.motionblur.v262.recording.RecordingShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

// Capture the final client framebuffer near the end of the client frame
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "runTick", at = @At("TAIL"), require = 0)
    private void nmb$afterRunTick(boolean advanceGameTime, CallbackInfo ci) {
        RecordingShaderManager.captureFinalFrameAndPresent();
    }

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), require = 0)
    private void nmb$beforeReloadResourcePacks(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        RecordingShaderManager.beginResourceReload();
    }

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), require = 0)
    private void nmb$afterReloadResourcePacks(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        CompletableFuture<Void> reloadFuture = cir.getReturnValue();
        if (reloadFuture != null) {
            reloadFuture.whenComplete((ignored, throwable) -> Minecraft.getInstance().execute(RecordingShaderManager::endResourceReload));
        } else {
            RecordingShaderManager.endResourceReload();
        }
    }
}
