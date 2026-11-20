// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

import java.util.EnumMap;
import java.util.IdentityHashMap;

public final class NametagSampling263 {
    public static final Identifier VANILLA_TEXT = Identifier.withDefaultNamespace("core/text");
    public static final Identifier SHARP_TEXT = Identifier.fromNamespaceAndPath("customcosmetics", "core/sharp_nametag");
    private static final IdentityHashMap<RenderPipeline, RenderPipeline> PIPELINES = new IdentityHashMap<>();
    private static final IdentityHashMap<OitPipelineSet, OitPipelineSet> OIT_PIPELINES = new IdentityHashMap<>();

    private NametagSampling263() {}

    public static RenderPipeline sharpen(RenderPipeline pipeline) {
        if (pipeline == null || !VANILLA_TEXT.equals(pipeline.getShaders().get(ShaderType.FRAGMENT))) return pipeline;
        return PIPELINES.computeIfAbsent(pipeline, SharpPipeline::new);
    }

    public static OitPipelineSet sharpen(OitPipelineSet pipelines) {
        if (pipelines == null) return null;
        return OIT_PIPELINES.computeIfAbsent(pipelines, set -> {
            var depth = sharpen(set.depthBoundsPipeline());
            var transmittance = sharpen(set.transmittancePipeline());
            var accumulate = sharpen(set.accumulatePipeline());
            return depth == set.depthBoundsPipeline() && transmittance == set.transmittancePipeline()
                && accumulate == set.accumulatePipeline() ? set : new OitPipelineSet(depth, transmittance, accumulate);
        });
    }

    private static final class SharpPipeline extends RenderPipeline {
        private SharpPipeline(RenderPipeline original) {
            super(Identifier.fromNamespaceAndPath("customcosmetics",
                "nametag/" + original.getLocation().getNamespace() + "/" + original.getLocation().getPath()),
                shaders(original), original.getShaderDefines(), original.getBindGroupLayouts(),
                original.getColorTargetStates().toArray(ColorTargetState[]::new), original.getDepthStencilState(),
                original.getPolygonMode(), original.isCull(), original.getVertexFormatBindings().toArray(VertexFormat[]::new),
                original.getPrimitiveTopology(), original.pushConstantSize(), original.getSortKey());
        }

        private static EnumMap<ShaderType, Identifier> shaders(RenderPipeline original) {
            var shaders = new EnumMap<>(original.getShaders());
            shaders.put(ShaderType.FRAGMENT, SHARP_TEXT);
            return shaders;
        }
    }

    public record Sequence(FormattedCharSequence original) implements FormattedCharSequence {
        @Override public boolean accept(FormattedCharSink sink) { return original.accept(sink); }
    }

    public interface Glyphs {
        void csb$sampleNametagLinearly(boolean enabled);
    }

    public interface Type {
        RenderType csb$linearNametagType();
    }
}
