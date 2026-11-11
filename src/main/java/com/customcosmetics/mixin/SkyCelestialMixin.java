// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.sky.Skyboxify;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyCelestialMixin {
    @Inject(method = {"renderSun", "renderMoon"}, at = @At("HEAD"), cancellable = true)
    private void ccos$sunMoon(CallbackInfo callback) {
        if (!Skyboxify.sunMoon() && Skyboxify.active(Minecraft.getInstance().level)) callback.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void ccos$stars(CallbackInfo callback) {
        if (!Skyboxify.stars()) callback.cancel();
    }
}
