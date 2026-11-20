// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.LegacyNametagRender263;
import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LegacyNametagOrder263Mixin {
    private LegacyNametagOrder263Mixin() {}

    @Mixin(SubmitNodeCollection.class)
    public abstract static class Collection implements LegacyNametagRender263.Collection {
        @Shadow @Final @Mutable private List<FeatureRenderPhase<?>> allPhases;
        @Unique private SimpleFeatureRenderPhase csb$foreground;

        @Inject(method = "submitNameTagPart", at = @At("HEAD"), cancellable = true)
        private void csb$deferForeground(TextFeatureRenderer.Submit submit, CallbackInfo ci) {
            if (!ConfigManager.nickTweaksLegacyNametagOrder || !RenderSystem.isRenderingLevel) return;
            if (csb$foreground == null) {
                csb$foreground = new SimpleFeatureRenderPhase();
                var phases = new ArrayList<>(allPhases);
                phases.add(csb$foreground);
                allPhases = List.copyOf(phases);
            }
            csb$foreground.submit(submit);
            ci.cancel();
        }

        @Override public SimpleFeatureRenderPhase csb$legacyNametagPhase() {
            return csb$foreground;
        }
    }

    @Mixin(FeatureRenderDispatcher.PreparedFrame.class)
    public abstract static class Frame implements LegacyNametagRender263.Frame {
        @Shadow private SubmitNodeStorage submitNodeStorage;
        @Shadow private FeatureFrameContext context;
        @Shadow @Final private Map<FeatureRenderPhase<?>, ?> groupsByPhase;

        @Shadow private void executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext frame, RenderPass pass) {
            throw new AssertionError();
        }

        @Inject(method = "hasAnySeeThrough", at = @At("HEAD"), cancellable = true)
        private void csb$runLatePassForStandaloneNametags(CallbackInfoReturnable<Boolean> cir) {
            if (csb$hasForeground()) cir.setReturnValue(true);
        }

        @Unique private boolean csb$hasForeground() {
            if (!ConfigManager.nickTweaksLegacyNametagOrder || submitNodeStorage == null) return false;
            for (var collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                var phase = ((LegacyNametagRender263.Collection) collection).csb$legacyNametagPhase();
                if (phase != null && groupsByPhase.containsKey(phase)) return true;
            }
            return false;
        }

        @Override public void csb$renderLegacyNametags(RenderTarget target) {
            if (!csb$hasForeground()) return;
            // The vanilla see-through pass has no depth attachment. Preserve depth testing for the foreground.
            var descriptor = RenderPassDescriptor.builder(() -> "CustomSkyBlock nametag foreground")
                .withColorAttachment(target.getColorTextureView())
                .withDepthAttachment(target.getDepthTextureView())
                .build();
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                RenderSystem.bindDefaultUniforms(pass);
                for (var collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                    var phase = ((LegacyNametagRender263.Collection) collection).csb$legacyNametagPhase();
                    if (phase != null) executePhase(phase, context, pass);
                }
            }
        }
    }

    @Mixin(LevelRenderer.class)
    public abstract static class World {
        @Inject(method = "executeSeeThrough", at = @At("TAIL"))
        private void csb$drawForegroundAfterSeeThrough(FeatureRenderDispatcher.PreparedFrame frame, RenderTarget target, CallbackInfo ci) {
            ((LegacyNametagRender263.Frame) frame).csb$renderLegacyNametags(target);
        }
    }
}
