// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.features.items.ItemCosmetics;
import com.customcosmetics.features.items.HeadTexture;
import com.customcosmetics.gametest.RenderAssertions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ItemOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/** Records the resolved ID submitted to the real renderer, after production overrides. */
@Mixin(ItemModelResolver.class)
public class ItemAppearanceProbeMixin {
    @Mixin(value=net.minecraft.client.renderer.entity.player.AvatarRenderer.class,priority=400)
    public static class CatharsisState {
        @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("RETURN"))
        private void addRealCatharsisTexture(net.minecraft.world.entity.Avatar entity,net.minecraft.client.renderer.entity.state.AvatarRenderState state,float tick,CallbackInfo ci){
            if(com.customcosmetics.gametest.CatharsisArmorGameTest.track&&ItemCosmetics.uuid(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)).equals(com.customcosmetics.gametest.CatharsisArmorGameTest.UUID))
                com.customcosmetics.gametest.CatharsisArmorGameTest.injectTexture(state);
        }
    }
    @Mixin(net.minecraft.client.renderer.SubmitNodeCollection.class)
    public static class CatharsisSubmissions {
        @Inject(method="submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",at=@At("HEAD"),require=0,order=2000)
        private void recordRealDraw(net.minecraft.client.model.Model<?> model,Object state,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.rendertype.RenderType type,int light,int overlay,int color,@org.spongepowered.asm.mixin.injection.Coerce Object sprite,int outline,@org.spongepowered.asm.mixin.injection.Coerce Object crumbling,CallbackInfo ci){
            if(com.customcosmetics.gametest.CatharsisArmorGameTest.track&&state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid
                    &&ItemCosmetics.uuid(humanoid.chestEquipment).equals(com.customcosmetics.gametest.CatharsisArmorGameTest.UUID)&&type.toString().contains("armor"))
                com.customcosmetics.gametest.CatharsisArmorGameTest.record(state,color);
        }
    }
    @Mixin(net.minecraft.world.item.component.DyedItemColor.class)
    public static class AnimatedDye {
        @ModifyReturnValue(method="getOrDefault",at=@At("RETURN"),order=1500)
        private static int simulateAnimatedDye(int original,ItemStack stack,int fallback){
            return RenderAssertions.forceAnimatedArmorDye&&ItemCosmetics.uuid(stack).equals("12345678-1234-1234-1234-123456789abe")
                ?RenderAssertions.animatedArmorColor:original;
        }
    }
    @Mixin(net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer.class)
    public static class ArmorTint {
        @org.spongepowered.asm.mixin.injection.Group(name="armor_tint_probe",min=1)
        @ModifyArg(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),index=6,require=0)
        private int observeFinalTint(int color,@Local(argsOnly=true) ItemStack stack,@Local net.minecraft.client.resources.model.EquipmentClientInfo.Layer layer){
            if(RenderAssertions.forceAnimatedArmorDye&&layer.dyeable().isPresent()&&ItemCosmetics.uuid(stack).equals("12345678-1234-1234-1234-123456789abe"))
                RenderAssertions.observedArmorTints.add(color);
            return color;
        }
        @org.spongepowered.asm.mixin.injection.Group(name="armor_tint_probe",min=1)
        @ModifyArg(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"),index=6,require=0)
        private int observeModernFinalTint(int color,@Local(argsOnly=true) ItemStack stack,@Local net.minecraft.client.resources.model.EquipmentClientInfo.Layer layer){
            if(RenderAssertions.forceAnimatedArmorDye&&layer.dyeable().isPresent()&&ItemCosmetics.uuid(stack).equals("12345678-1234-1234-1234-123456789abe"))
                RenderAssertions.observedArmorTints.add(color);
            return color;
        }
    }
    @Inject(method="appendItemLayers",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/item/ItemModelResolver;getItemModel(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/item/ItemModel;"))
    private void customcosmetics$observeItemModel(ItemStackRenderState state,ItemStack item,ItemDisplayContext context,
        Level level,ItemOwner owner,int seed,CallbackInfo ci,@Local Identifier model){
        String uuid=ItemCosmetics.uuid(item);
        if(uuid.equals("12345678-1234-1234-1234-123456789abc")){
            RenderAssertions.customizedItemModel=model.toString();RenderAssertions.itemModelOverrides.incrementAndGet();
            var profile=item.get(net.minecraft.core.component.DataComponents.PROFILE);
            if(profile!=null){
                var properties=profile.partialProfile().properties().get("textures");
                if(!properties.isEmpty())RenderAssertions.customizedHeadTexture=HeadTexture.normalize(properties.iterator().next().value());
                RenderAssertions.headContexts.add(context.name());
            }
        }
        if(uuid.equals("12345678-1234-1234-1234-123456789abd"))RenderAssertions.untouchedItemModel=model.toString();
    }
}
