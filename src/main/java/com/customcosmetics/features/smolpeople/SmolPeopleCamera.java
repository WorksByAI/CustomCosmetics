/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.features.smolpeople;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SmolPeopleCamera {
    public static final float THIRD_PERSON_FRONT_Y_OFFSET = -0.65f;

    private SmolPeopleCamera() {
    }

    public static boolean isThirdPersonFrontActive() {
        if (!SmolPeopleNametag.isSmolPeopleActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
    }

    public static Vec3 applyThirdPersonFrontOffset(Vec3 position) {
        if (!isThirdPersonFrontActive()) {
            return position;
        }
        return new Vec3(position.x, position.y + THIRD_PERSON_FRONT_Y_OFFSET, position.z);
    }
}
