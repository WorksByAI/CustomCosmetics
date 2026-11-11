// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.gui.UiFonts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

public final class LocalFontSamplingMixin {
    @Mixin(FontTexture.class)
    public static class World {
        @ModifyExpressionValue(method = "<init>", at = @At(value = "FIELD",
            target = "Lcom/mojang/blaze3d/textures/FilterMode;NEAREST:Lcom/mojang/blaze3d/textures/FilterMode;"))
        private FilterMode customcosmetics$filter(FilterMode original, Supplier<String> name) {
            return UiFonts.isLocalAtlas(name.get()) ? FilterMode.LINEAR : original;
        }
    }

    @Mixin(GlyphRenderState.class)
    public static class Gui {
        @Shadow @Final private TextRenderable renderable;

        // The GUI uses its own sampler rather than the FontTexture's sampler.
        @ModifyExpressionValue(method = "textureSetup", at = @At(value = "FIELD",
            target = "Lcom/mojang/blaze3d/textures/FilterMode;NEAREST:Lcom/mojang/blaze3d/textures/FilterMode;"))
        private FilterMode customcosmetics$filter(FilterMode original) {
            if (!UiFonts.hasLocalAtlas()) return original;
            var texture = renderable.textureView();
            return texture != null && UiFonts.isLocalAtlas(texture.texture().getLabel()) ? FilterMode.LINEAR : original;
        }
    }
}
