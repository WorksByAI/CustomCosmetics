/*
 * View-model options and swing-duration math adapted from Noamm9/NoammAddons (CC0-1.0),
 * commit e8f1a596268030a830002c667a0d890c77a6a960, Animations / MixinItemInHandRenderer.
 * Swing duration implementation ported from MixinLivingEntity at 44e447abbe2f3a1c0678c6388abd3a3b0b2287d8.
 * Adapted 2026-10-11 to native swing update/interpolation order in separate render state;
 * never changes game input, entity state, or vanilla swing duration.
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See NOTICE and LICENSES/NoammAddons-CC0.txt.
 */
package com.customcosmetics.features.animations;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.blocking.SwordItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public final class ViewModelAnimations {
    private static final SwingTimeline clock = new SwingTimeline();
    private static InteractionHand swingingHand = InteractionHand.MAIN_HAND;
    private static Object world, localPlayer;
    private ViewModelAnimations() {}
    public static void tick(Minecraft client) {
        resetIfNeeded(client);
        if (client.player == null || !customTiming(client.player.getMainHandItem().isEmpty())) clock.clear();
    }
    private static void resetIfNeeded(Minecraft client) {
        if (world == client.level && localPlayer == client.player) return;
        world = client.level; localPlayer = client.player;
        clock.clear(); swingingHand = InteractionHand.MAIN_HAND;
    }
    /** Observe vanilla's existing swing call. Never invokes a swing or changes its outcome. */
    public static void observeSwing(Object player, InteractionHand hand) {
        if (!customTiming() || player != Minecraft.getInstance().player) return;
        var client = Minecraft.getInstance(); resetIfNeeded(client);
        if (!customTiming(client.player.getMainHandItem().isEmpty())) { clock.clear(); return; }
        if (clock.request(swingDuration(client))) swingingHand = hand;
    }
    /** Advance exactly where vanilla advances its animation, rather than predicting it at client tick end. */
    public static void advanceSwing(Object player) {
        var client = Minecraft.getInstance();
        if (player != client.player) return;
        resetIfNeeded(client);
        if (!customTiming(client.player.getMainHandItem().isEmpty())) { clock.clear(); return; }
        clock.tick(swingDuration(client));
    }
    public static int swingDuration(Minecraft client) {
        var player = client.player;
        if (player != null && customTiming(player.getMainHandItem().isEmpty())) {
            var haste = player == null ? null : player.getEffect(MobEffects.HASTE);
            var fatigue = player == null ? null : player.getEffect(MobEffects.MINING_FATIGUE);
            return noammDuration(haste == null ? 0 : 1 + haste.getAmplifier(),
                fatigue == null ? 0 : 1 + fatigue.getAmplifier(),
                ConfigManager.animationIgnoreHaste, ConfigManager.animationSwingSpeed);
        }
        int base = player == null ? 6 : com.customcosmetics.compatibility.SwingAccess.baseDuration(player,
            player.getItemInHand(com.customcosmetics.compatibility.SwingAccess.hand(player)));
        var fatigue = player == null ? null : player.getEffect(MobEffects.MINING_FATIGUE);
        int digSpeed = player != null && MobEffectUtil.hasDigSpeed(player) ? 1 + MobEffectUtil.getDigSpeedAmplification(player) : 0;
        base = effectDuration(base, digSpeed, fatigue == null ? 0 : 1 + fatigue.getAmplifier(), false);
        return Math.max(1, base);
    }
    /** Port of NoammAddons MixinLivingEntity.adjustSwingLength (CC0-1.0), with settings passed explicitly. */
    public static int noammDuration(int hasteLevel, int fatigueLevel, boolean ignoreHaste, double speed) {
        int length;
        if (ignoreHaste) length = 6;
        else if (hasteLevel > 0) length = 6 - hasteLevel;
        else if (fatigueLevel > 0) length = 6 + fatigueLevel * 2;
        else length = 6;
        // Clamp only the final result, as upstream does, including effects whose raw duration is nonpositive.
        return duration(length, speed);
    }
    public static int effectDuration(int itemDuration, int digSpeedLevel, int fatigueLevel, boolean ignoreHaste) {
        // Classic view-model timing: neither modern item timing nor potion effects override this baseline.
        // Only our render clocks consume it; vanilla swings and attacks keep their original timing.
        if (ignoreHaste) return 6;
        if (digSpeedLevel > 0) return Math.max(1, itemDuration - digSpeedLevel);
        return Math.max(1, itemDuration + Math.max(0, fatigueLevel) * 2);
    }
    public static int duration(int base, double speed) { return Math.max(1, (int) (base * Math.exp(-Math.clamp(speed, -2, 1)))); }
    public static boolean enabled() { return (ConfigManager.animationsEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS)); }
    public static boolean customTiming() { return enabled() && (ConfigManager.animationSwingSpeed != 0 || ConfigManager.animationIgnoreHaste); }
    public static boolean customTiming(boolean mainHandEmpty) { return customTiming() && !mainHandEmpty; }
    public static boolean suppressSwing(String skyblockId) {
        return enabled() && ConfigManager.animationDisableSwing && (!ConfigManager.animationTerminatorOnly || "TERMINATOR".equals(skyblockId));
    }
    public static float swing(float vanilla, float partial, InteractionHand hand, ItemStack stack) {
        if (!stack.isEmpty() && com.customcosmetics.features.blocking.SwordItems.isSword(stack)
                && com.customcosmetics.features.blocking.VisualBlockhit.matches(hand)) {
            return suppressSwing(SwordItems.skyblockId(stack).orElse("")) ? 0
                : com.customcosmetics.features.blocking.VisualBlockhit.progress(partial);
        }
        if (!enabled() || stack.isEmpty()) return vanilla;
        if (suppressSwing(SwordItems.skyblockId(stack).orElse(""))) return 0;
        if (!customTiming()) return vanilla;
        var player = Minecraft.getInstance().player;
        if (player == null || !customTiming(player.getMainHandItem().isEmpty())) return vanilla;
        return hand == swingingHand ? clock.progress(partial) : 0;
    }
    public static float equip(float vanilla) { return enabled() && ConfigManager.animationDisableEquip ? 0 : vanilla; }
    public static boolean handMovement() { return !enabled() || !ConfigManager.animationDisableHandMove; }
    public static int side(InteractionHand hand) {
        var player = Minecraft.getInstance().player;
        var arm = player == null ? HumanoidArm.RIGHT : player.getMainArm();
        if (hand == InteractionHand.OFF_HAND) arm = arm.getOpposite();
        return arm == HumanoidArm.RIGHT ? 1 : -1;
    }
    public static void position(PoseStack pose, int side) {
        if (enabled()) pose.translate((float) ConfigManager.animationX * side, (float) ConfigManager.animationY, (float) ConfigManager.animationZ);
    }
    public static void model(PoseStack pose, int side) {
        if (!enabled()) return;
        rotate(pose, Axis.XP, (float) ConfigManager.animationRotationX);
        rotate(pose, Axis.YP, (float) ConfigManager.animationRotationY * side);
        rotate(pose, Axis.ZP, (float) ConfigManager.animationRotationZ * side);
        float scale = (float) ConfigManager.animationItemScale; pose.scale(scale, scale, scale);
    }
    public static void swingTranslation(PoseStack pose, float x, float y, float z) {
        pose.translate(enabled() ? x * (float) ConfigManager.animationSwingX : x,
            enabled() ? y * (float) ConfigManager.animationSwingY : y,
            enabled() ? z * (float) ConfigManager.animationSwingZ : z);
    }
    public static void rotate(PoseStack pose, Axis axis, float degrees) { pose.mulPose(new org.joml.Matrix4f().rotation(axis.rotationDegrees(degrees))); }
}
