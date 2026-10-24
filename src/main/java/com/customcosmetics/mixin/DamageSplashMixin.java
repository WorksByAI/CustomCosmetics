// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.damage.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class DamageSplashMixin {
    @Mixin(ClientPacketListener.class)
    public static class Metadata {
        @Inject(method="handleSetEntityData", at=@At("TAIL"))
        private void customcosmetics$damageMetadata(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
            if (!DamageSplash.active()) return;
            var client = Minecraft.getInstance();
            if (client.level != null && client.level.getEntity(packet.id()) instanceof ArmorStand stand) DamageSplash.capture(stand);
        }
    }
    @Mixin(EntityRenderState.class)
    public static class State implements DamageRenderState {
        @Unique private boolean customcosmetics$damage;
        public boolean customcosmetics$damageName() { return customcosmetics$damage; }
        public void customcosmetics$damageName(boolean value) { customcosmetics$damage = value; }
    }
    @Mixin(EntityRenderer.class)
    public static class Renderer {
        @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at=@At("TAIL"))
        private void customcosmetics$damageLabel(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
            ((DamageRenderState) state).customcosmetics$damageName(entity instanceof ArmorStand stand && DamageSplash.capture(stand));
        }
        @Inject(method="submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at=@At("HEAD"), cancellable=true)
        private void customcosmetics$hideReplacedLabel(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int line, CallbackInfo ci) {
            if (DamageSplash.active() && ((DamageRenderState) state).customcosmetics$damageName()) ci.cancel();
        }
    }
}
