// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.camera.CameraZoom;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

final class CameraZoomMixin {
    @Mixin(Camera.class)
    abstract static class View {
        @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
        private void csb$zoomView(float partialTick, CallbackInfoReturnable<Float> cir) {
            float vanilla = cir.getReturnValueF();
            float zoom = CameraZoom.fov(vanilla);
            if (zoom != vanilla) cir.setReturnValue(zoom);
        }

        @Inject(method = "calculateHudFov", at = @At("RETURN"), cancellable = true)
        private void csb$zoomHands(float partialTick, CallbackInfoReturnable<Float> cir) {
            if (ConfigManager.zoomEnabled && ConfigManager.zoomHands)
                cir.setReturnValue(CameraZoom.scaleFov(cir.getReturnValueF()));
        }
    }

    @Mixin(MouseHandler.class)
    abstract static class Input {
        @Shadow @Final private Minecraft minecraft;
        @Shadow private double accumulatedDX;
        @Shadow private double accumulatedDY;

        @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
        private void csb$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
            if (CameraZoom.scroll(minecraft, vertical)) ci.cancel();
        }

        @Inject(method = "turnPlayer", at = @At("HEAD"))
        private void csb$zoomSensitivity(double time, CallbackInfo ci) {
            if (!ConfigManager.zoomEnabled || !ConfigManager.zoomSensitivity) return;
            double scale = CameraZoom.sensitivity();
            accumulatedDX *= scale;
            accumulatedDY *= scale;
        }
    }
}
