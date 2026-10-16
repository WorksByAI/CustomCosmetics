/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.mixin;

import com.customcosmetics.features.smolpeople.SmolPeopleNametag;
import com.customcosmetics.render.EntityIdentity;
import com.customcosmetics.utils.NickRenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class NametagMixin {
    @Mixin(EntityRenderState.class)
    public static class IdentityMixin implements EntityIdentity {
        @Unique private int customcosmetics$entityId = -1;
        @Override public int customcosmetics$getEntityId() { return customcosmetics$entityId; }
        @Override public void customcosmetics$setEntityId(int id) { customcosmetics$entityId = id; }
    }

    @Mixin(EntityRenderer.class)
    public static class EntityMixin {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
        private void customcosmetics$identify(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
            ((EntityIdentity) state).customcosmetics$setEntityId(entity.getId());
            if(entity instanceof net.minecraft.client.player.AbstractClientPlayer&&state.nameTag!=null)
                com.customcosmetics.features.party.SharingNametag.remember(net.minecraft.client.Minecraft.getInstance(),entity.getUUID(),state.nameTag);
        }

        @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At("HEAD"))
        private void customcosmetics$offsetMirror(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                                 CameraRenderState camera, int line, CallbackInfo ci) {
            float offset = SmolPeopleNametag.getMirrorNametagArmorStandOffset(((EntityIdentity) state).customcosmetics$getEntityId());
            if (offset != 0) pose.translate(0, offset, 0);
        }
    }

    @Mixin(SubmitNodeCollection.class)
    public static class TextMixin {
        @ModifyVariable(method = {
            "submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            "submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
        },
            at = @At("HEAD"), argsOnly = true)
        private Component customcosmetics$layoutNickname(Component original) {
            if (original == null) return null;
            var processed = NickRenderUtils.handleFormattedText(original);
            return processed instanceof Component component ? component : original;
        }
    }
}
