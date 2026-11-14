// SPDX-License-Identifier: MIT
// Adapted from HitColor X, Copyright (c) 2026 Contt.
package com.customcosmetics.mixin;

import com.customcosmetics.features.hitcolor.HitColors;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

public final class HitColorLayersMixin {
    private static final String EQUIPMENT = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";
    private static final String NO_OVERLAY = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I";
    private static final String CAPE_SUBMIT = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V";
    private static final String SUBMIT_MODEL = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III";
    private static final String SUBMIT_ITEM = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    @Mixin(EquipmentLayerRenderer.class)
    public static class Equipment {
        @ModifyExpressionValue(method = EQUIPMENT, slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;getColorForLayer(Lnet/minecraft/client/resources/model/EquipmentClientInfo$Layer;I)I")),
            at = @At(value = "FIELD", target = NO_OVERLAY, ordinal = 0))
        private int armor(int original, @Local(argsOnly = true, ordinal = 0) Object state) {
            return HitColors.route(original, state, HitColors.Layer.ARMOR);
        }

        @ModifyExpressionValue(method = EQUIPMENT, slice = @Slice(from = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;trimSpriteLookup:Ljava/util/function/Function;")),
            at = @At(value = "FIELD", target = NO_OVERLAY, ordinal = 0), require = 0)
        private int legacyTrim(int original, @Local(argsOnly = true, ordinal = 0) Object state) {
            return HitColors.route(original, state, HitColors.Layer.TRIM);
        }

        @ModifyExpressionValue(method = EQUIPMENT, slice = @Slice(from = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;trimTextureLookup:Ljava/util/function/Function;")),
            at = @At(value = "FIELD", target = NO_OVERLAY, ordinal = 0), require = 0)
        private int modernTrim(int original, @Local(argsOnly = true, ordinal = 0) Object state) {
            return HitColors.route(original, state, HitColors.Layer.TRIM);
        }
    }

    @Mixin(CapeLayer.class)
    public static class Cape {
        @ModifyArg(method = CAPE_SUBMIT, at = @At(value = "INVOKE", target = SUBMIT_MODEL
            + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"), index = 5, require = 0)
        private int legacy(int original, @Local(argsOnly = true) AvatarRenderState state) {
            return HitColors.route(original, state, HitColors.Layer.CAPE);
        }

        @ModifyArg(method = CAPE_SUBMIT, at = @At(value = "INVOKE", target = SUBMIT_MODEL + ")V"), index = 5, require = 0)
        private int modern(int original, @Local(argsOnly = true) AvatarRenderState state) {
            return HitColors.route(original, state, HitColors.Layer.CAPE);
        }
    }

    @Mixin(ItemInHandLayer.class)
    public static class HeldItem {
        @ModifyArg(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT_ITEM), index = 3)
        private int item(int original, @Local(argsOnly = true) ArmedEntityRenderState state) {
            return HitColors.route(original, state, HitColors.Layer.ITEM);
        }
    }

    @Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
    public static class FirstPersonLegacy {
        @ModifyArg(method = "renderItem", at = @At(value = "INVOKE", target = SUBMIT_ITEM), index = 3)
        private int item(int original) { return HitColors.firstPerson(original); }
    }

    @Mixin(targets = "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer")
    public static class FirstPersonModern {
        @ModifyArg(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT_ITEM), index = 3)
        private int item(int original) { return HitColors.firstPerson(original); }
    }
}
