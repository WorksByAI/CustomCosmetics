// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.*;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.sync.PresenceSync;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.customcosmetics.utils.IdentityFrameCache;
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
    private static long frame,revision;
    private static final List<IdentityFrameCache<Component,Component>> TAB_NAMES=List.of(new IdentityFrameCache<>(512),new IdentityFrameCache<>(512));
    private static List<PlayerBlocklist.Entry> blocked;
    private static final List<Map<String,String>> STRINGS=Arrays.stream(RankText.Context.values()).<Map<String,String>>map(context->new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>512;}
    }).toList();
    private RankAppearance(){}
    public static RankSettings local(){return FeatureModules.enabled(FeatureModules.Feature.NICK)
        ?new RankSettings(ConfigManager.nickTweaksRankEnabled,ConfigManager.nickTweaksRankPlusColor,ConfigManager.nickTweaksRankType,ConfigManager.nickTweaksRankTagColor):RankSettings.OFF;}
    public static void invalidate(){snapshot=null;local=null;cached=EMPTY;revision++;STRINGS.forEach(Map::clear);}
    public static void beginFrame(){frame++;}
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
            &&local.tagColor().equals(ConfigManager.nickTweaksRankTagColor)
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
        local=state;self=id;online=!ConfigManager.offlineMode;blocked=ConfigManager.nickTweaksBlockedPlayers;revision++;STRINGS.forEach(Map::clear);return cached;
    }
    public static boolean active(){return current().active();}
    public static String apply(String text){
        var renderer=current();if(!renderer.active())return text;
        var strings=STRINGS.get(RankDisplayContext.current().ordinal());var result=strings.get(text);if(result!=null)return result;
        result=renderer.apply(text,RankDisplayContext.current());strings.put(text,result);return result;
    }
    public static FormattedCharSequence apply(FormattedCharSequence text){return current().apply(text,RankDisplayContext.current());}
    public static FormattedCharSequence apply(FormattedCharSequence text,RankText.Context context){return current().apply(text,context);}
    /** Width and draw both request Tab names. Reuse the ephemeral result without mutating PlayerInfo. */
    public static Component tabName(Component name,boolean skyblock){
        var renderer=current();if(!renderer.active())return name;
        var cache=TAB_NAMES.get(skyblock?1:0);cache.begin(frame,revision);
        var cached=cache.get(name);if(cached!=null)return cached;
        var source=RankText.sequence(name);var result=renderer.apply(source,skyblock?RankText.Context.TAB_SKYBLOCK:RankText.Context.TAB_LOBBY);
        cached=result==source?name:RankText.component(result);cache.put(name,cached);return cached;
    }
}
