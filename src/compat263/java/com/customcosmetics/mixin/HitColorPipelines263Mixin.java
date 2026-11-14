// SPDX-License-Identifier: MIT
// Overlay pipeline support adapted from HitColor X, Copyright (c) 2026 Contt.
package com.customcosmetics.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Builder;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderPipelines.class)
public final class HitColorPipelines263Mixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withLocation(Ljava/lang/String;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private static Builder select(Builder builder, String path, Operation<Builder> original,
                                  @Share("hitColorArmor") LocalRef<Builder> armor) {
        switch (path) {
            case "pipeline/armor_cutout_no_cull", "pipeline/armor_decal_cutout_no_cull", "pipeline/armor_cutout_no_cull_glint" -> armor.set(builder);

            default -> {}
        }
        return original.call(builder, path);
    }

    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withShaderDefine(Ljava/lang/String;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private static Builder skipNoOverlay(Builder builder, String define, Operation<Builder> original,
                                         @Share("hitColorArmor") LocalRef<Builder> armor) {
        return builder == armor.get() && define.equals("NO_OVERLAY") ? builder : original.call(builder, define);
    }

    @ModifyReceiver(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;build()Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"))
    private static Builder overlay(Builder builder, @Share("hitColorArmor") LocalRef<Builder> armor) {
        if (builder == armor.get()) return builder.withBindGroupLayout(BindGroupLayouts.SAMPLER1);

        return builder;
    }
}
