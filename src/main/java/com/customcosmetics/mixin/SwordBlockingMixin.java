/*
 * Render-hook approach adapted from TeamMidnightDust/SwordBlocking (MIT).
 * Copyright (c) 2020 MidnightDust
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Reworked 2026-10-11 for visual-only sword poses and independent cosmetic synchronization.
 * See LICENSE, NOTICE and LICENSES/SwordBlocking-MIT.txt.
 */
package com.customcosmetics.mixin;

import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.blocking.BlockingPose;
import com.customcosmetics.features.blocking.SwordBlocking;
import com.customcosmetics.features.animations.ViewModelAnimations;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exclusively renderer hooks: no item-use changes, fake shields, game packets or combat mechanics. */
public final class SwordBlockingMixin {
    @Pseudo
    @Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
    public static abstract class FirstPerson {
        @Shadow public abstract void renderItem(net.minecraft.world.entity.LivingEntity player, ItemStack stack,
            ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector, int light);

        @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"), cancellable = true)
        private void customcosmetics$blockPose(AbstractClientPlayer player, float partialTick, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
            SubmitNodeCollector collector, int light, CallbackInfo ci) {
            BlockHand blocking = SwordBlocking.hand(player);
            if (blocking == BlockHand.NONE || stack.isEmpty()
                || (blocking == BlockHand.MAIN) != (hand == InteractionHand.MAIN_HAND)) return;
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.pushPose();
            ViewModelAnimations.position(pose, side);
            swing = ViewModelAnimations.swing(swing, partialTick, hand, stack);
            BlockingPose.apply(pose, side, swing, ViewModelAnimations.equip(equip));
            ViewModelAnimations.model(pose, side);
            renderItem(player, stack, arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, pose, collector, light);
            pose.popPose();
            ci.cancel();
        }

        @ModifyVariable(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"), ordinal = 2, argsOnly = true)
        private float customcosmetics$visualSwing(float swing, @Local(argsOnly = true, ordinal = 0) float partial,
                @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack) {
            return ViewModelAnimations.swing(swing, partial, hand, stack);
        }
        @ModifyVariable(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"), ordinal = 3, argsOnly = true)
        private float customcosmetics$visualEquip(float equip) { return ViewModelAnimations.equip(equip); }

        @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
        private void customcosmetics$viewPosition(AbstractClientPlayer player, float partial, float pitch,
                InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
                SubmitNodeCollector collector, int light, CallbackInfo ci) {
            if (!stack.isEmpty()) ViewModelAnimations.position(pose, ViewModelAnimations.side(hand));
        }
        @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
        private void customcosmetics$viewModel(AbstractClientPlayer player, float partial, float pitch,
                InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
                SubmitNodeCollector collector, int light, CallbackInfo ci) {
            if (!stack.isEmpty()) ViewModelAnimations.model(pose, ViewModelAnimations.side(hand));
        }
        @WrapOperation(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
        private void customcosmetics$swingAxes(PoseStack pose, float x, float y, float z, Operation<Void> original) {
            ViewModelAnimations.swingTranslation(pose, x, y, z);
        }
        @WrapWithCondition(method = {"submitHandsWithItems", "renderHandsWithItems"}, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
        private boolean customcosmetics$handMovement(PoseStack pose, org.joml.Quaternionfc rotation) {
            return ViewModelAnimations.handMovement();
        }
        @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
        private void customcosmetics$instantEquip(ItemStack current, ItemStack next, CallbackInfoReturnable<Boolean> ci) {
            if (ViewModelAnimations.enabled() && com.customcosmetics.config.ConfigManager.animationDisableEquip) ci.setReturnValue(true);
        }
    }

    @Pseudo
    @Mixin(targets = "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer")
    public static abstract class FirstPersonModern {

        @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
        private void customcosmetics$blockPose(@Coerce Object playerState, @Coerce Object handsState,
            float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
            PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
            BlockHand blocking = SwordBlocking.localHand();
            if (blocking == BlockHand.NONE || stack.isEmpty()
                || (blocking == BlockHand.MAIN) != (hand == InteractionHand.MAIN_HAND)) return;
            var avatar = com.customcosmetics.compatibility.BlockingRenderAccess.avatar(playerState);
            if (avatar == null) return;
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
            pose.pushPose();
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            ViewModelAnimations.position(pose, side);
            swing = ViewModelAnimations.swing(swing, partialTick, hand, stack);
            BlockingPose.apply(pose, side, swing, ViewModelAnimations.equip(equip));
            ViewModelAnimations.model(pose, side);
            com.customcosmetics.compatibility.BlockingRenderAccess.item(handsState, hand)
                .submit(pose, collector, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
            ci.cancel();
        }

        @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
        private float customcosmetics$visualSwing(float swing, @Local(argsOnly = true, ordinal = 0) float partial,
                @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack) {
            return ViewModelAnimations.swing(swing, partial, hand, stack);
        }
        @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), ordinal = 3, argsOnly = true)
        private float customcosmetics$visualEquip(float equip) { return ViewModelAnimations.equip(equip); }
        @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
        private void customcosmetics$viewPosition(@Coerce Object playerState, @Coerce Object handsState,
                float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
            if (!stack.isEmpty()) ViewModelAnimations.position(pose, ViewModelAnimations.side(hand));
        }
        @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
        private void customcosmetics$viewModel(@Coerce Object playerState, @Coerce Object handsState,
                float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
            if (!stack.isEmpty()) ViewModelAnimations.model(pose, ViewModelAnimations.side(hand));
        }
        @WrapOperation(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
        private void customcosmetics$swingAxes(PoseStack pose, float x, float y, float z, Operation<Void> original) {
            ViewModelAnimations.swingTranslation(pose, x, y, z);
        }
        @WrapWithCondition(method = "submitHandsWithItems", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"))
        private boolean customcosmetics$handMovement(PoseStack pose, com.mojang.math.Axis axis, float angle) {
            return ViewModelAnimations.handMovement();
        }
    }

    @Pseudo
    @Mixin(targets = "net.minecraft.client.player.FirstPersonHandsAndItems")
    public static class ModernEquipment {
        @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
        private void customcosmetics$instantEquip(ItemStack current, ItemStack next,
                net.minecraft.client.player.LocalPlayer player, CallbackInfoReturnable<Boolean> ci) {
            if (ViewModelAnimations.enabled() && com.customcosmetics.config.ConfigManager.animationDisableEquip) ci.setReturnValue(true);
        }
    }

    @Mixin(targets = "net.minecraft.client.renderer.entity.layers.ItemInHandLayer")
    public static class ThirdPersonItem {
        @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
        private void customcosmetics$thirdPersonItem(net.minecraft.client.renderer.entity.state.ArmedEntityRenderState state,
                net.minecraft.client.renderer.item.ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
            var level = Minecraft.getInstance().level;
            if (!(state instanceof AvatarRenderState avatar) || level == null || !(level.getEntity(avatar.id) instanceof Player player)) return;
            BlockHand hand = SwordBlocking.hand(player);
            if (hand == BlockHand.NONE) return;
            HumanoidArm blockingArm = hand == BlockHand.MAIN ? player.getMainArm() : player.getMainArm().getOpposite();
            if (arm == blockingArm) BlockingPose.thirdPerson(pose, arm == HumanoidArm.RIGHT ? 1 : -1);
        }
    }

    @Mixin(value = PlayerModel.class, priority = 900)
    public static class ThirdPerson {
        @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
        private void customcosmetics$blockPose(AvatarRenderState state, CallbackInfo ci) {
            var level = Minecraft.getInstance().level;
            if (level == null || !(level.getEntity(state.id) instanceof Player player)) return;
            BlockHand blocking = SwordBlocking.hand(player);
            if (blocking == BlockHand.NONE) return;
            HumanoidArm arm = blocking == BlockHand.MAIN ? player.getMainArm() : player.getMainArm().getOpposite();
            PlayerModel model = (PlayerModel) (Object) this;
            var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            part.xRot = part.xRot * 0.5f - 0.9424778f;
            part.yRot = 0;
            part.zRot = 0;
            // Sleeves are children of the arms in 26.1+, so they already inherit this pose.
            // Copying it to the child rotates the sleeve twice and survives vanilla's bare-hand reset.
        }
    }
}
