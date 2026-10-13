// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.gametest.PlayerSizeGameTest;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the final real model matrix after all production scale hooks have run. */
@Mixin(AvatarRenderer.class)
public class PlayerSizeProbeMixin {
    @WrapMethod(method="scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V")
    private void customcosmetics$observeSize(AvatarRenderState state,PoseStack pose,Operation<Void> original){
        original.call(state,pose);var scale=pose.last().pose().getScale(new org.joml.Vector3f());
        PlayerSizeGameTest.observed.put(state.id,new float[]{scale.x,scale.y,scale.z});
    }
}
