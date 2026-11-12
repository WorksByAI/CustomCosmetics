// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.LocalGradientPipelines;
import com.customcosmetics.gui.UiFonts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

public final class LocalFontSampling263Mixin {
    @Mixin(FontTexture.class)
    public static abstract class World extends AbstractTexture {
        @Inject(method = "<init>", at = @At("TAIL"))
        private void customcosmetics$filter(Supplier<String> name, GlyphRenderTypes types, boolean colored, CallbackInfo ci) {
            if (UiFonts.registerLocalAtlas(name, this, getTextureView())) {
                sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            }
        }
    }

    @Mixin(GlyphRenderState.class)
    public static class Gui {
        @Shadow @Final private TextRenderable renderable;

        @ModifyReturnValue(method = "pipeline", at = @At("RETURN"))
        private RenderPipeline customcosmetics$gradient(RenderPipeline original) {
            if (LocalGradientPipelines.GRADIENT.equals(original.getShaders().get(ShaderType.FRAGMENT))
                    && UiFonts.localAtlas(renderable.textureView()) != null) {
                return (RenderPipeline) LocalGradientPipelines.grayscale(renderable, original);
            }
            return original;
        }

        // The GUI uses its own sampler rather than the FontTexture's sampler.
        @ModifyExpressionValue(method = "textureSetup", at = @At(value = "FIELD",
            target = "Lcom/mojang/renderpearl/api/textures/FilterMode;NEAREST:Lcom/mojang/renderpearl/api/textures/FilterMode;"))
        private FilterMode customcosmetics$filter(FilterMode original) {
            return UiFonts.localAtlas(renderable.textureView()) == null ? original : FilterMode.LINEAR;
        }
    }
}
