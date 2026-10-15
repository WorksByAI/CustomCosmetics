// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.party.*;
import net.minecraft.client.Minecraft;
import java.util.*;

/** One realtime channel for visual blocking and party markers. Cosmetics and chat stay independent. */
public final class RealtimeSync {
    private static RealtimeClient live;private static String endpoint="";private static PresenceAuthClient.Session identity;
    private static long generation,nextAttempt;private static boolean authenticating;private static BlockHand hand=BlockHand.NONE;private static String item="";
    private RealtimeSync(){}
    public static boolean enabled(){return !"udp".equals(ConfigManager.syncBlockingTransport);}
    public static void block(BlockHand value,String id){
        boolean share=ConfigManager.swordBlockingEnabled&&ConfigManager.swordBlockingSyncEnabled&&ConfigManager.syncEnabled;
        hand=share?value:BlockHand.NONE;item=share?id:"";if(live!=null)live.block(hand,item);
    }
    public static BlockingTransport.Pose pose(UUID id){if(!RemoteAccess.online()||live==null)return null;var pose=live.blocks().get(id);return pose!=null&&pose.expiresAt()>System.nanoTime()/1000000?pose:null;}
    public static Map<UUID,PartyClient.Teammate> teammates(){return RemoteAccess.online()&&live!=null?live.teammates():Map.of();}
    public static String status(){return live==null?"Disconnected":live.status();}
    public static String connectionStatus(){return live==null||live.closed()?PartySync.status():live.status();}
    public static List<String> diagnostics(){
        if(!RemoteAccess.online())return List.of("Realtime: Offline mode; remote connections are closed.");
        if(!enabled())return List.of("Realtime: Manual legacy UDP transport does not support party P2P.");
        if(live!=null)return live.diagnostics();
        return List.of("Realtime: "+(authenticating?"Waiting for Minecraft authentication":identity==null?"No authenticated realtime session":"Waiting to connect; retry in "+Math.max(0,nextAttempt-System.currentTimeMillis())+"ms"));
    }
    public static void disconnect(){generation++;if(live!=null)live.close();live=null;endpoint="";identity=null;nextAttempt=0;authenticating=false;}
    public static void tick(Minecraft client){
        boolean blocking=ConfigManager.swordBlockingEnabled&&ConfigManager.swordBlockingSyncEnabled&&ConfigManager.syncEnabled;
        boolean party=PartyFeatures.sharingPositions()&&!PartyFeatures.roster().empty();
        if(!enabled()||!RemoteAccess.online()||client.player==null||client.level==null||client.hasSingleplayerServer()||!blocking&&!party){disconnect();return;}
        String base=PresenceSync.serviceBaseUrl(),key=base+"|"+ConfigManager.syncBlockingTransport;var auth=PresenceSync.actionSession();
        if(!key.equals(endpoint)||auth!=identity){disconnect();endpoint=key;identity=auth;}
        if(live!=null&&!live.closed()){
            var world=PresenceWorld.read(client);var player=client.player;var roster=party?PartyFeatures.roster():PartyRoster.EMPTY;
            live.update(new RealtimeClient.View(world.world(),blocking?world.players():Set.of(),roster,PresenceWorld.environment(client),party,party&&ConfigManager.partyP2pEnabled),
                blocking?hand:BlockHand.NONE,blocking?item:"",party?new PartyClient.Position(player.getX(),player.getY(),player.getZ(),player.getBbWidth(),player.getBbHeight()):null);return;
        }
        if(System.currentTimeMillis()<nextAttempt||authenticating)return;nextAttempt=System.currentTimeMillis()+5000;
        long version=generation;
        if(auth==null){authenticating=true;PresenceSync.authenticatedSession().whenComplete((session,error)->client.execute(()->{if(version!=generation)return;authenticating=false;if(error==null)nextAttempt=0;}));return;}
        try{live=RemoteAccess.channel(()->new RealtimeClient(base,auth,"wss".equals(ConfigManager.syncBlockingTransport)));}catch(Exception unavailable){/* Bounded retry; no visual state is persisted. */}
    }
}
