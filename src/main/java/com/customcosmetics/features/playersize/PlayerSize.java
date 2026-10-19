/*
 * Player model scaling adapted from Odin's PlayerSize rendering hook.
 * Copyright (c) 2025, odtheking. Original code: BSD-3-Clause; see LICENSES/Odin-BSD-3-Clause.txt.
 * Modifications Copyright (C) 2026 CustomCosmetics contributors; AGPL-3.0-or-later.
 */
package com.customcosmetics.features.playersize;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.smolpeople.SmolPlayers;
import com.customcosmetics.sync.PresenceSync;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

public final class PlayerSize {
    private PlayerSize(){}
    public static PlayerSizeSettings settings(int entityId){
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.PLAYER_SIZE))return PlayerSizeSettings.NORMAL;
        var client=Minecraft.getInstance();var player=SmolPlayers.getPlayerByEntityId(entityId);
        if(player==null)return PlayerSizeSettings.NORMAL;
        var self=client.player==null?null:client.player.getUUID();
        return PlayerSizeSettings.choose(self,player.getUUID(),ConfigManager.offlineMode,PlayerSizeSettings.local(),PresenceSync.getRemotePlayerSize(player.getUUID()));
    }
    public static void apply(int entityId,PoseStack pose){
        var settings=settings(entityId);if(settings.enabled())pose.scale((float)settings.x(),(float)settings.y(),(float)settings.z());
    }
}
