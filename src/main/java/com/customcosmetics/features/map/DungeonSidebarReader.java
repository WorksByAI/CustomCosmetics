// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.customcosmetics.compatibility.SidebarAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;
import java.util.*;

/** Reads the active objective, including legacy Hypixel labels stored in team prefix/suffix pairs. */
public final class DungeonSidebarReader {
    public record Snapshot(String objective,String title,List<String> rows,List<String> raw,boolean detected,String floor,boolean kuudra){
        public Snapshot(String objective,String title,List<String> rows,List<String> raw,boolean detected,String floor){this(objective,title,rows,raw,detected,floor,DungeonSidebar.kuudra(title,rows));}
        public Snapshot{rows=List.copyOf(rows);raw=List.copyOf(raw);}
    }
    private static Object cachedLevel,cachedConnection,cachedBoard,cachedTeam,cachedObjective;
    private static long cachedRevision,cachedAt;
    private static Snapshot cached;
    public static void clearCache(){cached=null;cachedLevel=cachedConnection=cachedBoard=cachedTeam=cachedObjective=null;}
    private static final DungeonSession SESSION=new DungeonSession();
    private static Snapshot snapshot(Minecraft client,String objective,String title,List<String> rows,List<String> raw){
        var context=SESSION.update(client.level,client.player==null?null:client.getConnection(),title,rows);
        return new Snapshot(objective,title,rows,raw,context.detected(),context.floor());
    }
    private DungeonSidebarReader(){}
    public static Snapshot read(Minecraft client){return read(client,false);}
    public static Snapshot read(Minecraft client,boolean diagnostics){
        if(client.level==null||client.player==null){clearCache();return snapshot(client,"","",List.of(),List.of());}
        var board=client.level.getScoreboard();var objective=board.getDisplayObjective(DisplaySlot.SIDEBAR);
        var slot=SidebarAccess.teamSlot(client.player.getTeam());if(slot!=null&&board.getDisplayObjective(slot)!=null)objective=board.getDisplayObjective(slot);
        long revision=(Object)board instanceof SidebarRevision tracker?tracker.customcosmetics$sidebarRevision():Long.MIN_VALUE;
        long now=System.nanoTime();
        if(!diagnostics&&cached!=null&&cachedLevel==client.level&&cachedConnection==client.getConnection()&&cachedBoard==board
            &&cachedTeam==client.player.getTeam()&&cachedObjective==objective&&cachedRevision==revision&&now-cachedAt<50_000_000)return cached;
        Snapshot result;
        if(objective==null)result=snapshot(client,"","",List.of(),List.of());
        else result=readObjective(client,board,objective,diagnostics);
        if(!diagnostics){cachedLevel=client.level;cachedConnection=client.getConnection();cachedBoard=board;cachedTeam=client.player.getTeam();cachedObjective=objective;cachedRevision=revision;cachedAt=now;cached=result;}
        return result;
    }
    private static Snapshot readObjective(Minecraft client,Scoreboard board,Objective objective,boolean diagnostics){
        var rows=new LinkedHashSet<String>();var raw=new ArrayList<String>();
        for(var row:board.listPlayerScores(objective)){
            var team=board.getPlayersTeam(row.owner());
            String display=PlayerTeam.formatNameForTeam(team,row.ownerName()).getString();if(!row.isHidden())rows.add(display);
            String prefix=team==null?"":team.getPlayerPrefix().getString(),suffix=team==null?"":team.getPlayerSuffix().getString();
            // Legacy Hypixel inserts synthetic row IDs such as '§u' between prefix and suffix.
            // Those IDs are not visible text; including one splits "Catacombs" and loses detection.
            // Some custom sidebars also display these labels with a hidden '#' owner.
            if(team!=null&&!DungeonSidebar.normalize(prefix+suffix).isBlank())rows.add(prefix+suffix);
            if(diagnostics&&raw.size()<32)raw.add("owner="+escaped(row.owner())+", hidden="+row.isHidden()+", display="+escaped(display)+", prefix="+escaped(prefix)+", suffix="+escaped(suffix));
        }
        return snapshot(client,objective.getName(),objective.getDisplayName().getString(),List.copyOf(rows),raw);
    }
    private static String escaped(String text){var out=new StringBuilder();text.codePoints().limit(256).forEach(c->{if(c<32||c>126)out.append(String.format(Locale.ROOT,"\\u{%x}",c));else out.appendCodePoint(c);});return out.toString();}
}
