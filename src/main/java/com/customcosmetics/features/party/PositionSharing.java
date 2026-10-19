// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.sync.RemoteAccess;
import net.minecraft.client.Minecraft;

public final class PositionSharing {
    private PositionSharing() {}
    public static boolean enabled() { return (ConfigManager.positionSharingEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING)) || (ConfigManager.positionSharingReceive && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING)); }
    public static PositionAudience audience(Minecraft client) {
        if (!enabled() || !RemoteAccess.online() || !PresenceWorld.hasHypixelLocation(client)) return PositionAudience.PARTY;
        return PositionAudience.settings((ConfigManager.positionSharingEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING)), ConfigManager.positionSharingMode,
            ConfigManager.positionSharingTargets, (ConfigManager.positionSharingReceive && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING)));
    }
    public static boolean receiving(Minecraft client) { return (ConfigManager.positionSharingReceive && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING)) && RemoteAccess.online() && PresenceWorld.hasHypixelLocation(client); }
}
