// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.party.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class PartySync{
    private static PartyClient live;private static long generation,nextAttempt;private static boolean authenticating;
    private static String endpoint="";private static PresenceAuthClient.Session identity;
    private static volatile Map<UUID,PartyClient.Teammate> remote=Map.of();private static volatile String status="Disconnected";
    private PartySync(){}
    public static String status(){return status;}
    public static Map<UUID,PartyClient.Teammate> teammates(){return RemoteAccess.online()&&PartyFeatures.sharingPositions()?(RealtimeSync.enabled()?RealtimeSync.teammates():remote):Map.of();}
    public static void disconnect(){if(live==null&&endpoint.isEmpty()&&!authenticating)return;generation++;if(live!=null)live.close();live=null;endpoint="";identity=null;remote=Map.of();nextAttempt=0;authenticating=false;status="Disconnected";}
    public static void tick(Minecraft client){
        String base=PresenceSync.serviceBaseUrl();var auth=PresenceSync.actionSession();
        if(!base.equals(endpoint)||identity!=auth){disconnect();endpoint=base;identity=auth;}
        var roster=PartyFeatures.roster();
        if(roster.empty()){if(live!=null){generation++;live.close();live=null;remote=Map.of();}status=PartyFeatures.membershipStatus();return;}
        if(live!=null&&!live.closed()){
            var world=PresenceWorld.read(client);var player=client.player;boolean esp=PartyFeatures.sharingPositions()&&!RealtimeSync.enabled();
            live.update(new PartyClient.Scope(roster,world.world(),PresenceWorld.environment(client),esp,ConfigManager.partyChatEnabled),esp?new PartyClient.Position(player.getX(),player.getY(),player.getZ(),player.getBbWidth(),player.getBbHeight()):null);
            boolean ready=live.sharingReady();status=ready?(PartyFeatures.sharingPositions()&&RealtimeSync.enabled()?RealtimeSync.status():"Connected"+(remote.isEmpty()?"":"\nTCP: "+remote.size())):"Connecting";PartyFeatures.connected(ready);return;
        }
        if(live!=null&&live.closed())PartyFeatures.connectionFailed();
        remote=Map.of();if(System.currentTimeMillis()<nextAttempt||authenticating)return;nextAttempt=System.currentTimeMillis()+15000;
        if(auth==null){
            authenticating=true;status="Authenticating";long version=generation;
            PresenceSync.authenticatedSession().whenComplete((session,error)->client.execute(()->{if(version!=generation)return;authenticating=false;if(error!=null){status="Minecraft account authentication required";PartyFeatures.connectionFailed();}else nextAttempt=0;}));return;
        }
        long version=generation;status="Connecting";
        try{live=RemoteAccess.channel(()->new PartyClient(base,auth,poses->{if(version==generation)remote=poses;},message->client.execute(()->{
            if(version!=generation||!ConfigManager.partyChatEnabled||!RemoteAccess.online()||!PartyFeatures.roster().contains(message.uuid(),message.name()))return;
            ClientUiAccess.chat(client).addClientSystemMessage(Component.literal("[CC Party] ").withColor(0xFFA6CF).append(Component.literal(message.name()+": ").withColor(0xFFA6CF)).append(Component.literal(message.text()).withColor(0xFFFFFF)));
            if(message.delivered()==0)PartyFeatures.notice("No other party members are connected to Party IRC.");
        }),error->client.execute(()->{if(version==generation&&RemoteAccess.online()&&ConfigManager.partyChatEnabled)PartyFeatures.notice(error);})));
        }catch(Exception failure){status="Party service unavailable";PartyFeatures.connectionFailed();}
    }
    public static void chat(String message){
        if(!RemoteAccess.online())PartyFeatures.notice("Party IRC is unavailable in offline mode.");
        else if(PartyFeatures.roster().empty())PartyFeatures.notice(PartyFeatures.membershipStatus()+".");
        else if(live==null||!live.chat(message))PartyFeatures.notice("Party IRC is not connected yet. "+status+".");
    }
}
