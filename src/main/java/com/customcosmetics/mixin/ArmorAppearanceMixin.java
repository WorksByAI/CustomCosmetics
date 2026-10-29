// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.items.ArmorAppearance;
import com.customcosmetics.features.items.HeadSkins;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

public final class ArmorAppearanceMixin {
    @Mixin(LivingEntityRenderer.class)
    public static class HeadSkin {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
        private void customcosmetics$headSkin(net.minecraft.world.entity.LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
            HeadSkins.worn(entity, state);
        }
    }
    @Mixin(EntityRenderDispatcher.class)
    public interface Assets extends ArmorAppearance.AssetAccess {
        @Override @Accessor("equipmentAssets") EquipmentAssetManager customcosmetics$equipmentAssets();
    }

    @Mixin(HumanoidMobRenderer.class)
    public static class HeadEquipment {
        @ModifyReturnValue(method="getEquipmentIfRenderable",at=@At("RETURN"))
        private static ItemStack customcosmetics$knownArmorIdentity(ItemStack rendered,net.minecraft.world.entity.LivingEntity entity,EquipmentSlot slot){
            return com.customcosmetics.features.items.ArmorItemIdentity.render(entity,slot,rendered);
        }
        @ModifyExpressionValue(method = "getEquipmentIfRenderable", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
        private static ItemStack customcosmetics$headArmorProxy(ItemStack original, @Local(argsOnly = true) EquipmentSlot slot) {
            return ArmorAppearance.headProxy(original, slot);
        }
    }

    @Mixin(CustomHeadLayer.class)
    public static class HideOriginalHead {
        @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
                at = @At("HEAD"), cancellable = true)
        private void customcosmetics$hideHeadForArmor(PoseStack pose, SubmitNodeCollector collector, int light,
                LivingEntityRenderState state, float yaw, float pitch, CallbackInfo ci) {
            if (state instanceof HumanoidRenderState humanoid && ArmorAppearance.hidesHead(humanoid.headEquipment, state.wornHeadType != null))
                ci.cancel();
        }
    }

    @Mixin(EquipmentLayerRenderer.class)
    public static class Model {
        // Reapply at the actual layer tint: animation mods may replace the global dye lookup result.
        // Non-dyeable detail layers must keep their original colors.
        @ModifyExpressionValue(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;getColorForLayer(Lnet/minecraft/client/resources/model/EquipmentClientInfo$Layer;I)I"))
        private int customcosmetics$finalArmorColor(int original, @Local(argsOnly = true) ItemStack stack,
                @Local EquipmentClientInfo.Layer layer,@Local(argsOnly=true)Object state) {
            int selected=layer.dyeable().isPresent() ? ArmorAppearance.color(stack, original) : original;
            if(com.customcosmetics.features.items.ArmorTrace.active())com.customcosmetics.features.items.ArmorTrace.layer(state,stack,original,selected,layer.dyeable().isPresent());
            return selected;
        }
        @ModifyVariable(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
                at = @At("HEAD"), argsOnly = true)
        private ResourceKey<EquipmentAsset> customcosmetics$armorAsset(ResourceKey<EquipmentAsset> original,
                @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) EquipmentClientInfo.LayerType layer) {
            return ArmorAppearance.model(stack, original, layer);
        }
    }

    @Mixin(DyedItemColor.class)
    public static class Color {
        @ModifyReturnValue(method = "getOrDefault", at = @At("RETURN"))
        private static int customcosmetics$armorTint(int original, ItemStack stack, int defaultColor) {
            return ArmorAppearance.color(stack, original);
        }
    }
}
