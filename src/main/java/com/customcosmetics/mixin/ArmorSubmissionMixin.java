// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.items.ArmorSubmissionTint;
import com.customcosmetics.features.items.ArmorTrace;
import com.customcosmetics.features.items.ArmorAppearance;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class ArmorSubmissionMixin {
    @Mixin(EquipmentLayerRenderer.class)
    public static class Bind {
        @Inject(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",at=@At("HEAD"))
        private void customcosmetics$bindArmorTint(net.minecraft.client.resources.model.EquipmentClientInfo.LayerType layer,net.minecraft.resources.ResourceKey<?> asset,Model<?> model,Object state,ItemStack stack,com.mojang.blaze3d.vertex.PoseStack pose,SubmitNodeCollector collector,int light,net.minecraft.resources.Identifier texture,int outline,int order,CallbackInfo ci){
            ArmorSubmissionTint.bind(model,state,stack);
            if(ArmorTrace.active())ArmorTrace.bind(model,state,stack,collector,ArmorAppearance.color(stack,0));
        }
    }
    @Mixin(SubmitNodeCollection.class)
    public static class Legacy {
        @Inject(method="submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",at=@At("RETURN"))
        private void customcosmetics$traceSubmission(Model<?> model,Object state,com.mojang.blaze3d.vertex.PoseStack pose,RenderType type,int light,int overlay,int color,@org.spongepowered.asm.mixin.injection.Coerce Object mapping,int outline,@org.spongepowered.asm.mixin.injection.Coerce Object crumbling,CallbackInfo ci){
            if(ArmorTrace.active())ArmorTrace.finalSubmit(model,state,type,color);
        }
        @ModifyVariable(method="submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",at=@At("HEAD"),argsOnly=true,ordinal=2)
        private int customcosmetics$armorTint(int color,@Local(argsOnly=true)Model<?> model,@Local(argsOnly=true)Object state,@Local(argsOnly=true)RenderType type){
            return ArmorSubmissionTint.color(model,state,type,color);
        }
    }
    @Mixin(SubmitNodeCollection.class)
    public static class Modern {
        @Inject(method="submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",at=@At("RETURN"))
        private void customcosmetics$traceSubmission(Model<?> model,Object state,com.mojang.blaze3d.vertex.PoseStack pose,RenderType type,int light,int overlay,int color,@org.spongepowered.asm.mixin.injection.Coerce Object mapping,int outline,CallbackInfo ci){
            if(ArmorTrace.active())ArmorTrace.finalSubmit(model,state,type,color);
        }
        @ModifyVariable(method="submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",at=@At("HEAD"),argsOnly=true,ordinal=2)
        private int customcosmetics$armorTint(int color,@Local(argsOnly=true)Model<?> model,@Local(argsOnly=true)Object state,@Local(argsOnly=true)RenderType type){
            return ArmorSubmissionTint.color(model,state,type,color);
        }
    }
}
