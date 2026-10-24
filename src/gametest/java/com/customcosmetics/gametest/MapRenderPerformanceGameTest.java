package com.customcosmetics.gametest;

import com.customcosmetics.features.map.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.*;

public final class MapRenderPerformanceGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        // Preserve visual containers, fluids, hidden blocks and temporary server mining behavior.
        new MapSpecialGameTest().runTest(context);
        try(var world=context.worldBuilder().create()){
            context.waitTicks(25);
            sidebarCache(context);
            context.runOnClient(client->{MapEditor.enabled=true;MapEditor.dungeonOnly=false;MapEditor.autoRooms=false;MapEditor.editing=false;MapEditor.clear();});
            context.waitTicks(60);context.runOnClient(client->report(client,"BASELINE"));
            context.runOnClient(client->{
                var edits=new HashMap<MapEdits.Position,MapEdits.Edit>();
                for(int x=-40;x<40;x++)for(int y=-60;y<4;y++)for(int z=-40;z<40;z++){
                    var p=new MapEdits.Position(x,y,z);edits.put(p,new MapEdits.Edit(p,"minecraft:deepslate_bricks",false));
                }
                MapEditor.DATA.replace(edits);MapEditor.refresh();
            });
            context.waitTicks(100);
            context.runOnClient(client->{
                if(MapEditor.visible().size()!=409600)throw new AssertionError("Dense fixture was not visible");
                if(GhostBlocks.examinedCandidates!=0)throw new AssertionError("Ordinary edits still enter standalone rendering: "+GhostBlocks.examinedCandidates);
                report(client,"DENSE_409600");MapEditor.DATA.reset();MapEditor.enabled=false;MapEditor.autoRooms=true;MapEditor.dungeonOnly=true;MapEditor.refresh();
            });
        }
        context.waitTicks(2);context.runOnClient(client->{
            try{
                var source=GhostBlocks.class.getDeclaredField("indexed");source.setAccessible(true);
                var pending=GhostBlocks.class.getDeclaredField("building");pending.setAccessible(true);
                var index=GhostBlocks.class.getDeclaredField("index");index.setAccessible(true);
                if(source.get(null)!=null||pending.get(null)!=null||((GhostIndex)index.get(null)).size()!=0)throw new AssertionError("Disconnect retained a large map render index");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
        System.out.println("DENSE_MAP_RENDER_NO_ORDINARY_PER_FRAME_CANDIDATES_OK");
    }
    private static void report(Object client,String phase){try{System.out.println("MAP_RENDER_"+phase+"_FPS="+client.getClass().getMethod("getFps").invoke(client));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void sidebarCache(ClientGameTestContext context){
        final DungeonSidebarReader.Snapshot[] cached={null};
        context.runOnClient(client->{
            var board=client.level.getScoreboard();
            var objective=board.addObjective("map_cache",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("SKYBLOCK"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
            var team=board.addPlayerTeam("map_cache_location");team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("⏣ The Catacombs (M7)"));
            board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("§u"),objective).set(1);
            board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);
            var before=DungeonSidebarReader.read(client);board.addPlayerToTeam("§u",team);cached[0]=DungeonSidebarReader.read(client);
            if(before==cached[0]||!cached[0].detected())throw new AssertionError("Team membership did not invalidate sidebar recognition");
        });
        context.waitTicks(3);
        context.runOnClient(client->{
            if(DungeonSidebarReader.read(client)!=cached[0])throw new AssertionError("Unchanged sidebar was parsed again after its old 50 ms expiry");
            var board=client.level.getScoreboard();board.removePlayerFromTeam("§u");var removed=DungeonSidebarReader.read(client);
            if(removed==cached[0]||removed.rows().stream().anyMatch(s->s.contains("Catacombs")))throw new AssertionError("Removing a sidebar row's team retained stale rows");
            var team=board.getPlayerTeam("map_cache_location");team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("⏣ Village"));board.addPlayerToTeam("§u",team);
            if(DungeonSidebarReader.read(client).detected())throw new AssertionError("Leaving the dungeon retained the old run");
            board.removeObjective(board.getObjective("map_cache"));board.removePlayerTeam(team);DungeonSidebarReader.clearCache();
        });
        System.out.println("MAP_SIDEBAR_UNCHANGED_REUSE_TEAM_MEMBERSHIP_INVALIDATION_OK");
    }
}
