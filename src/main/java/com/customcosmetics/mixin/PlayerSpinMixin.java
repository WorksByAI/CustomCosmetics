// Adapted from Athen PlayerSpin (BSD-3-Clause).
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerSpinMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void rotate(Avatar entity, AvatarRenderState avatar, float partial, CallbackInfo callback) {
        if (!(ConfigManager.spinHeadEnabled || ConfigManager.spinBodyEnabled)
            || !(entity instanceof LocalPlayer player)) return;
        float time = (player.tickCount + partial) / 20f;
        if (ConfigManager.spinBodyEnabled) avatar.bodyRot = Mth.wrapDegrees(time * ConfigManager.spinBodySpeed * 360f);
        if (ConfigManager.spinHeadEnabled) avatar.yRot = Mth.wrapDegrees(time * ConfigManager.spinHeadSpeed * 360f) - avatar.bodyRot;
    }
}
