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
        if(!ConfigManager.sharingChamsEnabled||!RemoteAccess.online()||client.level==null||client.player==null)return false;
        var entity=client.level.getEntity(entityId);
        if(!(entity instanceof AbstractClientPlayer player)||player==client.player||player.isInvisible()||player.isSpectator())return false;
        if(!PartyDisplayRange.visible(ConfigManager.partyMaxDisplayDistance,client.player.distanceToSqr(player)))return false;
        if(PartyFeatures.roster().contains(player.getUUID(),player.getGameProfile().name()))return true;
        if(!PositionSharing.receiving(client))return false;
        var shared=PartySync.teammates().get(player.getUUID());return shared!=null&&shared.expiresAt()>System.currentTimeMillis();
    }
}
