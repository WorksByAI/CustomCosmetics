// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.compatibility.ChamsRenderAccess;
import com.customcosmetics.features.party.SharingChams;
import com.customcosmetics.render.EntityIdentity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
public final class ChamsMixin {
    /** 26.1/26.2 resolve destinations per material, including generated enchantment glints. */
    @Pseudo @Mixin(targets="net.minecraft.client.renderer.rendertype.OutputTarget")
    public static class Targets {
        @Inject(method="getRenderTarget",at=@At("HEAD"),cancellable=true)
        private void customcosmetics$appearanceTarget(CallbackInfoReturnable<com.mojang.blaze3d.pipeline.RenderTarget> cir){
            var target=ChamsRenderAccess.replayTarget();if(target!=null)cir.setReturnValue((com.mojang.blaze3d.pipeline.RenderTarget)target);
        }
    }
    @Mixin(LivingEntityRenderer.class)
    public static class Players {
        @ModifyVariable(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"),argsOnly=true)
        private SubmitNodeCollector customcosmetics$captureAppearance(SubmitNodeCollector original,LivingEntityRenderState state){
            return state instanceof AvatarRenderState&&!state.isInvisible&&SharingChams.applies(((EntityIdentity)state).customcosmetics$getEntityId())
                ?ChamsRenderAccess.capture(original):original;
        }
    }
    @Mixin(LevelRenderer.class)
    public static class World {
        @Inject(method={"render","renderLevel"},at=@At("HEAD"))
        private void customcosmetics$beginChams(CallbackInfo ci){ChamsRenderAccess.begin();}
        @Inject(method={"render","renderLevel"},at=@At("TAIL"))
        private void customcosmetics$finishChams(CallbackInfo ci){ChamsRenderAccess.finish((LevelRenderer)(Object)this);}
        @Inject(method="close",at=@At("HEAD"))
        private void customcosmetics$closeChams(CallbackInfo ci){ChamsRenderAccess.close();}
    }
}
