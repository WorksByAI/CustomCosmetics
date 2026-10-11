/*
 * Third-person blocking offsets adapted from FelpisLipe/BringBlockingBack (MIT),
 * commit f15a780e0f077dfd5468aadbc7efa9c7afbc90dd, misc/Utils.java.
 * Copyright (c) 2026 Felps
 * Earlier render hooks adapted from TeamMidnightDust/SwordBlocking (MIT).
 * Copyright (c) 2020 MidnightDust
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified 2026-10-11 for handedness, the full downward blockhit swing and compatible item display transforms.
 * See NOTICE, LICENSES/BringBlockingBack-MIT.txt and LICENSES/SwordBlocking-MIT.txt.
 */
package com.customcosmetics.features.blocking;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.ViewModelAnimations;

/** Render-only PvP pose. The resource pack's item display transform remains intact. */
public final class BlockingPose {
    private static final org.joml.Matrix4f RIGHT_DISPLAY_INVERSE = modernDisplay(1).invert();
    private static final org.joml.Matrix4f LEFT_DISPLAY_INVERSE = modernDisplay(-1).invert();
    private BlockingPose() {}
    public static void apply(PoseStack pose, int side) { apply(pose, side, 0, 0); }
    public static void apply(PoseStack pose, int side, float swing, float ignoredEquip) {
        swing = Float.isFinite(swing) ? Math.clamp(swing, 0f, 1f) : 0;
        var style = BlockingStyle.parse(ConfigManager.swordBlockingStyle);
        if (ViewModelAnimations.enabled() && (ConfigManager.animationSwingX != 1 || ConfigManager.animationSwingY != 1 || ConfigManager.animationSwingZ != 1)) {
            var rest = new PoseStack(); nativeApply(rest, side, 0, style);
            var moving = new PoseStack(); nativeApply(moving, side, swing, style);
            var origin = rest.last().pose(); var current = moving.last().pose();
            // Scale only the swing's displacement, retaining the approved guard and rotation arc.
            pose.translate((current.m30() - origin.m30()) * (float)(ConfigManager.animationSwingX - 1),
                (current.m31() - origin.m31()) * (float)(ConfigManager.animationSwingY - 1),
                (current.m32() - origin.m32()) * (float)(ConfigManager.animationSwingZ - 1));
        }
        nativeApply(pose, side, swing, style);
    }
    private static void nativeApply(PoseStack pose, int side, float swing, BlockingStyle style) {
        if (style == BlockingStyle.JIGSAW) flatApply(pose, side, swing);
        else defaultApply(pose, side, swing);
    }
    private static void flatApply(PoseStack pose, int side, float swing) {
        var rest = new PoseStack(); defaultApply(rest, side, 0);
        if (swing == 0 || swing == 1) {
            pose.mulPose(rest.last().pose());
            return;
        }
        var moving = new PoseStack(); defaultApply(moving, side, swing);
        // Compare the displayed sword, rather than the pre-display model basis.
        var display = modernDisplay(side);
        var guard = new org.joml.Matrix4f(rest.last().pose()).mul(display);
        var stroke = new org.joml.Matrix4f(moving.last().pose()).mul(display);
        var guardBlade = new org.joml.Vector3f(1, 1, 0).mulDirection(guard);
        var strokeBlade = new org.joml.Vector3f(1, 1, 0).mulDirection(stroke);
        float angle = (float) Math.atan2(guardBlade.x * strokeBlade.y - guardBlade.y * strokeBlade.x,
            guardBlade.x * strokeBlade.x + guardBlade.y * strokeBlade.y);
        // Ease the remaining screen-plane tilt toward a level crossguard at the
        // bottom of the stroke, without tipping either end in depth.
        angle += (float) Math.toRadians(12) * (float) Math.sin(Math.sqrt(swing) * Math.PI) * side;
        // Keep Default's wrist travel and downward direction, but rotate only in
        // the camera's XY plane. Both ends retain their resting depth and length.
        pose.translate(stroke.m30(), stroke.m31(), guard.m32());
        pose.mulPose(new org.joml.Matrix4f().rotationZ(angle));
        pose.translate(-guard.m30(), -guard.m31(), -guard.m32());
        pose.mulPose(rest.last().pose());
    }
    private static void defaultApply(PoseStack pose, int side, float swing) {
        float root = (float) Math.sin(Math.sqrt(swing) * Math.PI);
        // Modern attack recovery also lowers the equipped item. Blocking keeps the
        // guard raised instead; only its render transform is changed, never the cooldown.
        // Raise the whole guard to the supplied reference height without changing its arc.
        pose.translate(0.56f * side, -0.40f, -0.72f);
        // 1.8 uses a different first-person model basis. Convert that basis before
        // the modern resource-pack display transform, rather than rotate a modern guard.
        rotate(pose, Axis.YP, (45f - 20f * (float) Math.sin(swing * swing * Math.PI)) * side);
        rotate(pose, Axis.ZP, -20f * root * side);
        rotate(pose, Axis.XP, -80f * root);
        pose.scale(.4f, .4f, .4f);
        pose.translate(-.5f * side, .2f, 0);
        rotate(pose, Axis.YP, 30f * side);
        rotate(pose, Axis.XP, -80f);
        rotate(pose, Axis.YP, 60f * side);
        pose.translate(0, 4f / 16, 2f / 16);
        rotate(pose, Axis.YP, -135f * side);
        rotate(pose, Axis.ZP, 25f * side);
        pose.scale(1.7f, 1.7f, 1.7f);
        if (side < 0) pose.scale(-1f, 1f, 1f);
        pose.mulPose(side > 0 ? RIGHT_DISPLAY_INVERSE : LEFT_DISPLAY_INVERSE);
    }
    public static void thirdPerson(PoseStack pose, int side) {
        rotate(pose, Axis.YP, -90f * side);
        rotate(pose, Axis.ZP, -40f * side);
        rotate(pose, Axis.XP, 51f);
        rotate(pose, Axis.ZP, 180f * side);
        rotate(pose, Axis.XP, 197.2f);
        pose.translate(-0.22f * side, 0.13f, -0.22f);
    }
    private static void rotate(PoseStack pose, Axis axis, float degrees) {
        pose.mulPose(new org.joml.Matrix4f().rotation(axis.rotationDegrees(degrees)));
    }
    private static org.joml.Matrix4f modernDisplay(int side) {
        // Effective default handheld display after Minecraft's left-hand conversion.
        return new org.joml.Matrix4f().translation(1.13f / 16 * side, 3.2f / 16, 1.13f / 16)
            .rotateY((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(25)).scale(.68f);
    }
}
