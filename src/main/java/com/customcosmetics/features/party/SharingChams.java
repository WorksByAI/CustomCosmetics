// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.PartySync;
import com.customcosmetics.sync.RemoteAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/** Applies only to an already-loaded shared player, never to gameplay or first-person arms. */
public final class SharingChams {
    private SharingChams(){}
    public static boolean applies(int entityId){
        var client=Minecraft.getInstance();
        if(!(ConfigManager.sharingChamsEnabled||ConfigManager.locationChamsEnabled)||!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING)||!RemoteAccess.online()||client.level==null||client.player==null)return false;
        var entity=client.level.getEntity(entityId);
        if(!(entity instanceof AbstractClientPlayer player)||player==client.player||player.isInvisible()||player.isSpectator())return false;
        boolean party=PartyFeatures.sharingPositions()&&PartyFeatures.roster().contains(player.getUUID(),player.getGameProfile().name());
        if(party)return SharingVisuals.chams(true)&&PartyDisplayRange.visible(SharingVisuals.maxDistance(true),client.player.distanceToSqr(player));
        if(!PositionSharing.receiving(client))return false;
        var shared=PartySync.teammates().get(player.getUUID());
        return shared!=null&&shared.expiresAt()>System.currentTimeMillis()&&SharingVisuals.chams(false)&&PartyDisplayRange.visible(SharingVisuals.maxDistance(false),client.player.distanceToSqr(player));
    }
}
