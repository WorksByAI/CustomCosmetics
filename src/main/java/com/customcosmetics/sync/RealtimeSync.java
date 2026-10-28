// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.party.*;
import net.minecraft.client.Minecraft;
import java.util.*;

/** One realtime channel for visual blocking and party markers. Appearance prefers authenticated direct peers; blocking retains its realtime cadence. */
public final class RealtimeSync {
    private static RealtimeClient live;private static String endpoint="";private static PresenceAuthClient.Session identity;
    private static long nextAppearance;
    private static long generation,nextAttempt;private static boolean authenticating;private static BlockHand hand=BlockHand.NONE;private static String item="";
    private RealtimeSync(){}
    private static final int APPEARANCE_MODULES=com.customcosmetics.config.FeatureModules.Feature.NICK.bit()|com.customcosmetics.config.FeatureModules.Feature.PLAYER_SIZE.bit()|com.customcosmetics.config.FeatureModules.Feature.SMOL.bit()|com.customcosmetics.config.FeatureModules.Feature.EQUIPMENT.bit()|com.customcosmetics.config.FeatureModules.Feature.CREATURE_MODEL.bit()|com.customcosmetics.config.FeatureModules.Feature.SKINS.bit();
    private static boolean appearanceEnabled(){
        return ConfigManager.syncEnabled&&ConfigManager.partyP2pEnabled&&com.customcosmetics.client.ModErrors.active(com.customcosmetics.client.ModErrors.Key.APPEARANCE)&&(ConfigManager.disabledModules&APPEARANCE_MODULES)!=APPEARANCE_MODULES;
    }
    public static boolean enabled(){return !"udp".equals(ConfigManager.syncBlockingTransport)||PositionSharing.enabled()||appearanceEnabled();}
    public static void block(BlockHand value,String id){
        boolean share=(ConfigManager.swordBlockingEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS))&&ConfigManager.swordBlockingSyncEnabled&&ConfigManager.syncEnabled;
        hand=share?value:BlockHand.NONE;item=share?id:"";if(live!=null)live.block(hand,item);
    }
    public static BlockingTransport.Pose pose(UUID id){if(!RemoteAccess.online()||live==null)return null;var pose=live.blocks().get(id);return pose!=null&&pose.expiresAt()>System.nanoTime()/1000000?pose:null;}
    public static Map<UUID,PartyClient.Teammate> teammates(){return RemoteAccess.online()&&live!=null?live.teammates():Map.of();}
    public static String status(){return live==null?"Disconnected":live.status();}
    public static Set<UUID> directPeers(){return !RemoteAccess.online()||live==null||live.closed()?Set.of():live.directPeers();}
    public static String connectionStatus(){return live==null||live.closed()?PartySync.status():live.status();}
    public static List<String> diagnostics(){
        if(!RemoteAccess.online())return List.of("Realtime: Offline mode; remote connections are closed.");
        if(!enabled())return List.of("Realtime: Manual legacy UDP transport does not support party P2P.");
        if(live!=null)return live.diagnostics();
        return List.of("Realtime: "+(authenticating?"Waiting for Minecraft authentication":identity==null?"No authenticated realtime session":"Waiting to connect; retry in "+Math.max(0,nextAttempt-System.currentTimeMillis())+"ms"));
    }
    public static void disconnect(){PresenceSync.peerAppearances(Map.of());nextAppearance=0;if(live==null&&endpoint.isEmpty()&&identity==null&&!authenticating&&nextAttempt==0)return;generation++;if(live!=null)live.close();live=null;endpoint="";identity=null;nextAttempt=0;authenticating=false;}
    public static void onSettingsClosed(){if(live!=null&&live.requestedUdpPort()!=ConfigManager.getSyncP2pPort())disconnect();}
    public static void tick(Minecraft client){
        boolean blocking=(ConfigManager.swordBlockingEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS))&&ConfigManager.swordBlockingSyncEnabled&&ConfigManager.syncEnabled;
        boolean appearance=appearanceEnabled()&&PresenceWorld.isHypixel(client);
        boolean party=PartyFeatures.sharingPositions()&&!PartyFeatures.roster().empty();
        var audience=PositionSharing.audience(client);boolean locations=audience.receive()||audience.publishes(PartyFeatures.roster(),party);
        if(!enabled()||!RemoteAccess.online()||client.player==null||client.level==null||client.hasSingleplayerServer()||!blocking&&!party&&!locations&&!appearance){disconnect();return;}
        String base=PresenceSync.serviceBaseUrl(),key=base+"|"+ConfigManager.syncBlockingTransport;var auth=PresenceSync.actionSession();
        if(!key.equals(endpoint)||auth!=identity){disconnect();endpoint=key;identity=auth;}
        if(live!=null&&!live.closed()){
            var world=PresenceWorld.read(client);var player=client.player;var roster=PartyFeatures.roster();
            live.update(new RealtimeClient.View(world.world(),blocking?world.players():Set.of(),roster,PresenceWorld.environment(client),party,(party||locations||appearance)&&ConfigManager.partyP2pEnabled,audience,appearance?world.players():Set.of()),
                blocking?hand:BlockHand.NONE,blocking?item:"",audience.publishes(roster,party)?new PartyClient.Position(player.getX(),player.getY(),player.getZ(),player.getBbWidth(),player.getBbHeight()):null);
            long now=System.nanoTime()/1_000_000;
            if(appearance&&now>=nextAppearance){nextAppearance=now+5000;live.appearance(PresenceSync.capturePeerAppearance());}
            else if(!appearance){nextAppearance=0;live.appearance(null);}
            PresenceSync.peerAppearances(appearance?live.appearances():Map.of());return;
        }
        if(System.currentTimeMillis()<nextAttempt||authenticating)return;nextAttempt=System.currentTimeMillis()+5000;
        long version=generation;
        if(auth==null){authenticating=true;PresenceSync.authenticatedSession().whenComplete((session,error)->client.execute(()->{if(version!=generation)return;authenticating=false;if(error==null)nextAttempt=0;}));return;}
        try{live=RemoteAccess.channel(()->new RealtimeClient(base,auth,"wss".equals(ConfigManager.syncBlockingTransport)));}catch(Exception unavailable){/* Bounded retry; no visual state is persisted. */}
    }
}
