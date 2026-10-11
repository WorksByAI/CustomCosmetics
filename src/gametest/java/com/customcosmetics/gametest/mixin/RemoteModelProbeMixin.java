// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.gametest.RenderAssertions;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, priority = 1100)
public class RemoteModelProbeMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void customcosmeticstest$remotePose(AvatarRenderState state, CallbackInfo ci) {
        if (state.id != -1001 && state.id != -1002) return;
        PlayerModel model = (PlayerModel)(Object)this;
        var arm = state.id == -1001 ? model.rightArm : model.leftArm;
        var sleeve = state.id == -1001 ? model.rightSleeve : model.leftSleeve;
        if (Math.abs(sleeve.xRot) > .0001f || Math.abs(sleeve.yRot) > .0001f || Math.abs(sleeve.zRot) > .0001f)
            RenderAssertions.remoteSleeveMismatch.incrementAndGet();
        if (RenderAssertions.remoteSeen.add(state.id)) System.out.println("REMOTE_MODEL " + state.id
            + " headScale=" + model.head.xScale + " arm=" + arm.xRot + "," + arm.yRot);
        if (model.head.xScale == 2 && arm.xRot < -.6 && arm.yRot == 0) RenderAssertions.remoteModels.incrementAndGet();
    }
}
