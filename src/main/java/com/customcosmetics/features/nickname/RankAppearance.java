// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.*;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.sync.PresenceSync;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Rebuild only when local settings, synchronization or the current login changes. */
public final class RankAppearance {
    private static final RankText EMPTY=new RankText(List.of());
    private static RankText cached=EMPTY;
    private static Object snapshot,connection;
    private static RankSettings local;
    private static UUID self;
    private static boolean online;
    private static List<PlayerBlocklist.Entry> blocked;
    private static final Map<String,String> STRINGS=new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>512;}
    };
    private RankAppearance(){}
    public static RankSettings local(){return FeatureModules.enabled(FeatureModules.Feature.NICK)
        ?new RankSettings(ConfigManager.nickTweaksRankEnabled,ConfigManager.nickTweaksRankPlusColor,ConfigManager.nickTweaksRankType):RankSettings.OFF;}
    public static void invalidate(){snapshot=null;local=null;cached=EMPTY;STRINGS.clear();}
    private static RankText current(){
        if(!FeatureModules.enabled(FeatureModules.Feature.NICK))return EMPTY;
        boolean enabled=ConfigManager.nickTweaksRankEnabled;
        var remote=PresenceSync.remoteRanks();
        if(!enabled&&remote.isEmpty())return EMPTY;
        var client=Minecraft.getInstance();
        if(client==null||client.player==null||!PresenceWorld.isHypixel(client))return EMPTY;
        var id=client.getUser().getProfileId();
        Object version=PresenceSync.appearanceSnapshot();
        if(snapshot==version&&online==!ConfigManager.offlineMode&&connection==client.getConnection()&&local!=null&&local.enabled()==enabled
            &&local.plusColor().equals(ConfigManager.nickTweaksRankPlusColor)&&local.type().equals(ConfigManager.nickTweaksRankType)
            &&id.equals(self)&&blocked==ConfigManager.nickTweaksBlockedPlayers)return cached;
        var state=local();
        var targets=new LinkedHashMap<UUID,RankText.Target>();
        if(state.enabled())targets.put(id,new RankText.Target(client.getUser().getName(),state.prefix()));
        for(var rank:remote){
            if(!rank.administrator()&&rank.uuid().equals(id))continue;
            if(!rank.uuid().equals(id)&&PlayerBlocklist.contains(ConfigManager.nickTweaksBlockedPlayers,rank.uuid(),rank.name()))continue;
            targets.put(rank.uuid(),new RankText.Target(rank.name(),rank.prefix()));
        }
        cached=new RankText(List.copyOf(targets.values()));snapshot=version;connection=client.getConnection();
        local=state;self=id;online=!ConfigManager.offlineMode;blocked=ConfigManager.nickTweaksBlockedPlayers;STRINGS.clear();return cached;
    }
    public static boolean active(){return current().active();}
    public static String apply(String text){
        var renderer=current();if(!renderer.active())return text;
        var result=STRINGS.get(text);if(result!=null)return result;
        result=renderer.apply(text);STRINGS.put(text,result);return result;
    }
    public static FormattedCharSequence apply(FormattedCharSequence text){return current().apply(text);}
}
