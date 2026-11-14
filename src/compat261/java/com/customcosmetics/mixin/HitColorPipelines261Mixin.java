// SPDX-License-Identifier: MIT
// Overlay pipeline support adapted from HitColor X, Copyright (c) 2026 Contt.
package com.customcosmetics.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;

import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderPipelines.class)
public final class HitColorPipelines261Mixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withLocation(Ljava/lang/String;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"))
    private static Builder select(Builder builder, String path, Operation<Builder> original,
                                  @Share("hitColorArmor") LocalRef<Builder> armor, @Share("hitColorItems") LocalRef<Builder> items) {
        switch (path) {
            case "pipeline/armor_cutout_no_cull", "pipeline/armor_decal_cutout_no_cull", "pipeline/armor_translucent" -> armor.set(builder);
            case "pipeline/item_cutout", "pipeline/item_translucent" -> items.set(builder);
            default -> {}
        }
        return original.call(builder, path);
    }

    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withShaderDefine(Ljava/lang/String;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"))
    private static Builder skipNoOverlay(Builder builder, String define, Operation<Builder> original,
                                         @Share("hitColorArmor") LocalRef<Builder> armor) {
        return builder == armor.get() && define.equals("NO_OVERLAY") ? builder : original.call(builder, define);
    }

    @ModifyReceiver(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
    private static Builder overlay(Builder builder, @Share("hitColorArmor") LocalRef<Builder> armor, @Share("hitColorItems") LocalRef<Builder> items) {
        if (builder == armor.get()) return builder.withSampler("Sampler1");
        if (builder == items.get()) {
            var shader = net.minecraft.resources.Identifier.fromNamespaceAndPath("customcosmetics", "core/hit_color_item");
            return builder.withVertexShader(shader).withFragmentShader(shader).withSampler("Sampler1");
        }
        return builder;
    }
}
