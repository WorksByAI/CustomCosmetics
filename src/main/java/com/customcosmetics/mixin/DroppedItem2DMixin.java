/* Adapted from Baity DroppedItem2DMixin, Copyright (C) raueyhs / shyeuar.
 * Upstream commit: 131c7a4413a82ab016bc90def8df305bc7e58769 (2026-10-29).
 * Modifications Copyright (C) 2026 CustomCosmetics contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSES/Baity-LGPL-3.0.txt, LICENSES/GPL-3.0.txt and NOTICE. */
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.FeatureModules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-only billboard. No item scans, stack copies, packet changes or background tasks. */
@Mixin(ItemEntityRenderer.class)
public abstract class DroppedItem2DMixin {
    @Shadow @Final private RandomSource random;
    @Unique private final org.joml.Matrix4f customcosmetics$dropRotation=new org.joml.Matrix4f();
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"),cancellable=true)
    private void customcosmetics$flatDrop(ItemEntityRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo callback){
        if(!ConfigManager.droppedItems2dEnabled||!FeatureModules.enabled(FeatureModules.Feature.DROP_2D)||state.item.isEmpty())return;
        var bounds=state.item.getModelBoundingBox();
        pose.pushPose();
        try{
            pose.translate(0,(float)(Math.sin(state.ageInTicks/10f+state.bobOffset)*.1+.1-bounds.minY+.0625),0);
            // Matrix overload is shared by 26.1-26.3; the quaternion overload was renamed in 26.3.
            pose.mulPose(customcosmetics$dropRotation.rotationY((float)Math.toRadians(180-camera.yRot)).rotateX((float)Math.toRadians(-camera.xRot)));
            pose.scale(1,1,.01f);
            ItemEntityRenderer.submitMultipleFromCount(pose,collector,state.lightCoords,state,random,bounds);
        }finally{pose.popPose();}
        callback.cancel();
    }
}
