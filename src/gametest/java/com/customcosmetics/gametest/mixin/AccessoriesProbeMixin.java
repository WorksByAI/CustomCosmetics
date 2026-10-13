// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.accessories.AccessoryLayer;
import com.customcosmetics.gametest.AccessoriesGameTest;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AccessoryLayer.class)
public class AccessoriesProbeMixin {
    private static int parts;
    @Inject(method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",at=@At("HEAD"))
    private void begin(PoseStack pose,SubmitNodeCollector queue,int light,AvatarRenderState state,float yaw,float pitch,CallbackInfo ci){parts=0;}
    @Inject(method="part",at=@At("TAIL"))
    private static void observe(SubmitNodeCollector queue,PoseStack pose,ModelPart model,int light,int color,boolean glow,net.minecraft.resources.Identifier texture,CallbackInfo ci){
        if(!model.getAllParts().stream().anyMatch(p->!p.isEmpty()))throw new AssertionError("Empty accessory mesh was submitted");
        var expected=AccessoriesGameTest.expectedTextures.get(model);if(expected!=null&&!texture.toString().equals(expected))throw new AssertionError("Detailed and pixel material atlases were swapped");
        if(!pose.last().pose().isFinite())throw new AssertionError("Invalid accessory transform");parts++;
    }
    @Inject(method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",at=@At("RETURN"))
    private void finish(PoseStack pose,SubmitNodeCollector queue,int light,AvatarRenderState state,float yaw,float pitch,CallbackInfo ci){AccessoriesGameTest.observed.put(state.id,parts);}
}
