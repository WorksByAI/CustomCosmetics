// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.NametagSampling263;
import com.customcosmetics.config.ConfigManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class NametagAntialiasing263Mixin {
    private NametagAntialiasing263Mixin() {}

    @Mixin(SubmitNodeCollection.class)
    public static class Submission {
        @ModifyVariable(method = "nameTag", at = @At("HEAD"), argsOnly = true)
        private static FormattedCharSequence csb$markWorldNametag(FormattedCharSequence text) {
            return ConfigManager.nickTweaksNametagAntialiasing && RenderSystem.isRenderingLevel
                ? new NametagSampling263.Sequence(text) : text;
        }
    }

    @Mixin(TextFeatureRenderer.class)
    public static class TextRenderer {
        @WrapOperation(method = "renderText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font$PreparedText;visit(Lnet/minecraft/client/gui/Font$GlyphVisitor;)V"))
        private static void csb$sampleNametag(Font.PreparedText prepared, Font.GlyphVisitor visitor,
                                             Operation<Void> original, @Local(argsOnly = true) TextFeatureRenderer.Content.Text text) {
            if (!(text.string() instanceof NametagSampling263.Sequence)) {
                original.call(prepared, visitor);
                return;
            }
            var glyphs = (NametagSampling263.Glyphs) visitor;
            glyphs.csb$sampleNametagLinearly(true);
            try { original.call(prepared, visitor); }
            finally { glyphs.csb$sampleNametagLinearly(false); }
        }
    }

    @Mixin(targets = "net.minecraft.client.renderer.feature.TextFeatureRenderer$GlyphRenderer")
    public static class GlyphRenderer implements NametagSampling263.Glyphs {
        @Unique private boolean csb$linear;

        @Override public void csb$sampleNametagLinearly(boolean enabled) { csb$linear = enabled; }

        @ModifyExpressionValue(method = "acceptRenderable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/TextRenderable;renderType(Lnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
        private RenderType csb$selectSampler(RenderType original) {
            return csb$linear ? ((NametagSampling263.Type) original).csb$linearNametagType() : original;
        }
    }

    @Mixin(targets = "net.minecraft.client.renderer.ShaderManager$Configs")
    public static class TextShader {
        @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
        private void csb$samplePixelEdges(Identifier id, ShaderType stage, CallbackInfoReturnable<String> result) {
            if (stage != ShaderType.FRAGMENT || !id.equals(NametagSampling263.SHARP_TEXT)) return;
            var source = ((ShaderSource) this).getShader(NametagSampling263.VANILLA_TEXT, stage);
            if (source == null || !source.contains("void main()")) {
                result.setReturnValue(source);
                return;
            }
            result.setReturnValue(source.replace("texture(Sampler0, texCoord0)",
                "texture(Sampler0, csb_nametagUv(texCoord0))").replace("void main()", """
                vec2 csb_nametagUv(vec2 uv) {
                    vec2 atlas = vec2(textureSize(Sampler0, 0));
                    vec2 texel = uv * atlas - 0.5;
                    vec2 radius = clamp(fwidth(texel) * 0.33, vec2(0.035), vec2(0.5));
                    vec2 edge = smoothstep(0.5 - radius, 0.5 + radius, fract(texel));
                    return (floor(texel) + edge + 0.5) / atlas;
                }

                void main()"""));
        }
    }

    @Mixin(RenderType.class)
    public static class DrawType implements NametagSampling263.Type {
        @Unique private static final String CSB_NAMETAG = "csb_smooth_nametag/";
        @Shadow @Final private String name;
        @Shadow @Final private RenderSetup state;
        @Unique private RenderType csb$linearType;

        @Shadow static RenderType create(String name, RenderSetup setup) { throw new AssertionError(); }

        // Keep filtered and ordinary glyphs in separate batches, even if they share an atlas.
        @Override public RenderType csb$linearNametagType() {
            if (csb$linearType == null) csb$linearType = create(CSB_NAMETAG + name, state);
            return csb$linearType;
        }

        @ModifyReturnValue(method = "prepare", at = @At("RETURN"))
        private PreparedRenderType csb$bindLinearSampler(PreparedRenderType original) {
            if (!name.startsWith(CSB_NAMETAG)) return original;
            var textures = original.textures();
            for (int i = 0; i < textures.size(); i++) {
                var binding = textures.get(i);
                if (!binding.name().equals("Sampler0")) continue;
                var pipeline = NametagSampling263.sharpen(original.pipeline());
                var oit = NametagSampling263.sharpen(original.oitPipelineSet());
                if (pipeline == original.pipeline() && oit == original.oitPipelineSet()) return original;
                var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                var updated = new ArrayList<>(textures);
                updated.set(i, new PreparedRenderType.Texture(binding.name(), binding.textureView(), sampler));
                return new PreparedRenderType(original.name(), pipeline, oit,
                    original.dynamicTransforms(), original.scissorState(), updated);
            }
            return original;
        }
    }
}
