// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.gui.BlockingPreview;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A GUI-only render state; never creates an entity or changes either hand's game item. */
@Mixin(EntityRenderDispatcher.class)
public abstract class BlockingPreviewMixin {
    @Inject(method="submit",at=@At("HEAD"),cancellable=true)
    private void customcosmetics$preview(EntityRenderState state,@Coerce Object camera,double x,double y,double z,
            PoseStack ignored,SubmitNodeCollector collector,CallbackInfo callback) {
        if(!(state instanceof BlockingPreview.State preview))return;
        BlockingPreview.beginProjection(preview.projection);
        var pose=new PoseStack();pose.mulPose(preview.transform);
        preview.item.submit(pose,collector,0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);
        callback.cancel();
    }
}
