// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import java.util.*;

/** Local game data only. No additional identity lookups or cosmetic-service requests. */
public final class SharingNametag {
    private static final Cache CACHE=new Cache(512);
    private static final Cache RENDERED=new Cache(512);
    private static List<TabNametags.Row> tabRows=List.of();
    private static final Map<UUID,Optional<Component>> tabMatches=new HashMap<>();
    private static long nextTabSnapshot;
    private SharingNametag(){}
    public static String normalize(String value){return "vanilla".equals(value)?"vanilla":"simple";}
    public static void tick(Minecraft client){if(CACHE.world(client.level)||client.level==null){tabRows=List.of();tabMatches.clear();nextTabSnapshot=0;}RENDERED.world(client.level);}
    public static void remember(Minecraft client,UUID uuid,Component text){
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING))return;
        tick(client);CACHE.put(uuid,text);RENDERED.put(uuid,text);
    }
    public static Component label(Minecraft client,UUID uuid,String account){
        tick(client);
        if(client.level!=null){
            var player=client.level.getPlayerByUUID(uuid);
            if(player!=null){var rendered=RENDERED.get(uuid);if(rendered!=null)return rendered;var text=player.getDisplayName();CACHE.put(uuid,text);return text;}
            var info=client.getConnection()==null?null:client.getConnection().getPlayerInfo(uuid);
            var team=info!=null&&info.getTeam()!=null?info.getTeam():client.level.getScoreboard().getPlayersTeam(account);
            if(team!=null){var text=PlayerTeam.formatNameForTeam(team,Component.literal(account));CACHE.put(uuid,text);return text;}
            if(info!=null&&info.getTabListDisplayName()!=null){var text=info.getTabListDisplayName();CACHE.put(uuid,text);return text;}
            var tab=tabName(client,uuid,account);if(tab!=null){CACHE.put(uuid,tab);return tab;}
        }
        var cached=CACHE.get(uuid);return cached!=null?cached:Component.literal(account);
    }
    private static Component tabName(Minecraft client,UUID uuid,String account){
        if(client.getConnection()==null)return null;
        long now=System.nanoTime()/1_000_000;
        if(now>=nextTabSnapshot){
            nextTabSnapshot=now+500;tabMatches.clear();
            tabRows=client.getConnection().getOnlinePlayers().stream().map(info->{
                var profile=info.getProfile();var display=info.getTabListDisplayName();
                if(display==null&&info.getTeam()!=null)display=PlayerTeam.formatNameForTeam(info.getTeam(),Component.literal(profile.name()));
                return new TabNametags.Row(profile.id(),profile.name(),display);
            }).toList();
        }
        if(tabMatches.size()>512)tabMatches.clear();
        return tabMatches.computeIfAbsent(uuid,ignored->Optional.ofNullable(TabNametags.find(uuid,account,tabRows))).orElse(null);
    }

    /** A world-scoped bounded cache; never retains entities, connections or old worlds. */
    public static final class Cache {
        private final int limit;
        private final LinkedHashMap<UUID,Component> entries=new LinkedHashMap<>(16,.75f,true);
        private java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
        public Cache(int limit){if(limit<1)throw new IllegalArgumentException("Invalid name cache limit");this.limit=limit;}
        public boolean world(Object next){if(world.get()!=next||next==null&&!entries.isEmpty()){entries.clear();world=new java.lang.ref.WeakReference<>(next);return true;}return false;}
        public void put(UUID uuid,Component text){
            if(uuid==null||text==null||text.getString().isBlank()||text.getString().codePointCount(0,text.getString().length())>256)return;
            if(!text.equals(entries.get(uuid)))entries.put(uuid,text.copy());
            while(entries.size()>limit)entries.remove(entries.keySet().iterator().next());
        }
        public Component get(UUID uuid){return entries.get(uuid);}
        public int size(){return entries.size();}
    }
}
