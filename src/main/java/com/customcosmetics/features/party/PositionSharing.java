// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.sync.RemoteAccess;
import net.minecraft.client.Minecraft;

public final class PositionSharing {
    private PositionSharing() {}
    public static boolean enabled() { return ConfigManager.positionSharingEnabled || ConfigManager.positionSharingReceive; }
    public static PositionAudience audience(Minecraft client) {
        if (!enabled() || !RemoteAccess.online() || !PresenceWorld.hasHypixelLocation(client)) return PositionAudience.PARTY;
        return PositionAudience.settings(ConfigManager.positionSharingEnabled, ConfigManager.positionSharingMode,
            ConfigManager.positionSharingTargets, ConfigManager.positionSharingReceive);
    }
    public static boolean receiving(Minecraft client) { return ConfigManager.positionSharingReceive && RemoteAccess.online() && PresenceWorld.hasHypixelLocation(client); }
}
