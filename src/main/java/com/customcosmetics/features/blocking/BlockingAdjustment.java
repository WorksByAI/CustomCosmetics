// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Local first-person offsets only. Shared third-person poses use their original transform. */
public final class BlockingAdjustment {
    private BlockingAdjustment() {}
    public static void position(PoseStack pose, int side) {
        pose.translate(bounded(ConfigManager.blockingX,2)*side,bounded(ConfigManager.blockingY,2),bounded(ConfigManager.blockingZ,2));
    }
    public static void rotation(PoseStack pose, int side) {
        com.customcosmetics.features.animations.ViewModelAnimations.rotate(pose,Axis.XP,bounded(ConfigManager.blockingRotationX,180));
        com.customcosmetics.features.animations.ViewModelAnimations.rotate(pose,Axis.YP,bounded(ConfigManager.blockingRotationY,180)*side);
        com.customcosmetics.features.animations.ViewModelAnimations.rotate(pose,Axis.ZP,bounded(ConfigManager.blockingRotationZ,180)*side);
    }
    public static float bounded(double value, double limit) { return (float)(Double.isFinite(value)?Math.clamp(value,-limit,limit):0); }
    public static void reset() {
        ConfigManager.blockingX=ConfigManager.blockingY=ConfigManager.blockingZ=0;
        ConfigManager.blockingRotationX=ConfigManager.blockingRotationY=ConfigManager.blockingRotationZ=0;
    }
}
