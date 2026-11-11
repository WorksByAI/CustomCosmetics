// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
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
    @Unique private static final int CCOS_VANILLA_HURT = 0xB2FF0000;
    @Unique private int ccos$applied = CCOS_VANILLA_HURT;

    @Inject(method = "getTextureView", at = @At("HEAD"))
    private void ccos$updateHurtColor(CallbackInfoReturnable<?> callback) {
        int color = ConfigManager.hitColorEnabled && com.customcosmetics.sync.ExperimentalAccess.allowed()
            ? (Math.round(255 * (1 - ConfigManager.hitOpacity)) << 24) | ConfigManager.hitColor
            : CCOS_VANILLA_HURT;
        if (color == ccos$applied) return;
        var pixels = texture.getPixels();
        if (pixels == null) return;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) pixels.setPixel(x, y, color);
        }
        texture.upload();
        ccos$applied = color;
    }
}
