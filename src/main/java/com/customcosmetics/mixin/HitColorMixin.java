// SPDX-License-Identifier: MIT
// Adapted from HitColor X, Copyright (c) 2026 Contt.
package com.customcosmetics.mixin;

import com.customcosmetics.features.hitcolor.HitColors;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OverlayTexture.class)
public abstract class HitColorMixin {
    @Shadow @Final private DynamicTexture texture;
    @Unique private int ccos$body = HitColors.VANILLA_HURT;
    @Unique private int ccos$parts;

    @Inject(method = "getTextureView", at = @At("HEAD"))
    private void ccos$updateHurtColor(CallbackInfoReturnable<?> callback) {
        boolean active = HitColors.active();
        int color = active ? HitColors.rgbWithOverlayAlpha() : HitColors.CLEAR;
        int body = active ? HitColors.Layer.BODY.selected() ? color : HitColors.CLEAR : HitColors.VANILLA_HURT;
        if (body == ccos$body && (!active || color == ccos$parts)) return;
        var pixels = texture.getPixels();
        if (pixels == null) return;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) pixels.setPixel(x, y, body);
        }
        // Leave the vanilla white overlay row (10) untouched.
        pixels.setPixel(0, 8, color);
        pixels.setPixel(0, 9, color);
        pixels.setPixel(0, 11, color);
        pixels.setPixel(0, 12, color);
        ccos$parts = color;
        texture.upload();
        ccos$body = body;
    }
}
