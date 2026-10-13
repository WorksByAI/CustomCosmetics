/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.features.smolpeople;

import com.customcosmetics.gui.module.Module;
import com.customcosmetics.gui.module.ModuleManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SmolPeopleNametag {
    public static final float VERTICAL_OFFSET = -0.4f;

    private SmolPeopleNametag() {
    }

    public static boolean isSmolPeopleActive() {
        Module module = ModuleManager.getModuleByName("SmolPeople");
        return module != null && module.isEnabled();
    }

    public static boolean usesSmolNametag(int entityId) {
        return SmolPlayers.shouldApplySmolTo(entityId);
    }

    public static float getMirrorNametagArmorStandOffset(int entityId) {
        if (!SmolPlayers.isMirrorNametagArmorStand(entityId)) {
            return 0f;
        }
        return VERTICAL_OFFSET;
    }

    public static float getVerticalOffset(int entityId) {
        float offset=usesSmolNametag(entityId)?VERTICAL_OFFSET:0f;
        var player=SmolPlayers.getPlayerByEntityId(entityId);
        return player==null?offset:(float)com.customcosmetics.features.playersize.PlayerSize.settings(entityId).nameOffset(player.getBbHeight(),offset);
    }

    public static float adjustNametagHeight(float baseHeight, int entityId) {
        return baseHeight + getVerticalOffset(entityId);
    }
}
