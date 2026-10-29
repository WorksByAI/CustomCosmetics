// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.gametest.Release201GameTest;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntityRenderer.class)
public class Drop201ProbeMixin {
    @Inject(method="submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V",at=@At("HEAD"))
    private static void observe(PoseStack pose,SubmitNodeCollector collector,int light,ItemClusterRenderState state,RandomSource random,AABB box,CallbackInfo callback){
        if(!Release201GameTest.trackDrops)return;
        var scale=pose.last().pose().getScale(new org.joml.Vector3f());Release201GameTest.dropRatio=Math.min(scale.x,Math.min(scale.y,scale.z))/Math.max(scale.x,Math.max(scale.y,scale.z));Release201GameTest.dropDraws++;
    }
}
