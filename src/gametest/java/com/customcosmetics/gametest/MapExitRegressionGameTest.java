package com.customcosmetics.gametest;

import com.customcosmetics.features.map.*;
import com.customcosmetics.config.ConfigManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.*;

/** Run with a fresh test config: no imported layouts or presets. */
public final class MapExitRegressionGameTest implements FabricClientGameTest {
    private long scans,maxTick;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("forceload add -32 -32 47 47");
            world.getServer().runCommand("tp @a 0.5 70 0.5");
            world.getServer().runOnServer(MapAdventureGameTest::capturedM7Sidebar);context.waitTicks(20);
            context.runOnClient(client->{
                check(MapEditor.ROOMS.count()==0&&MapEditor.presetEntries().isEmpty(),"Requires an empty isolated map config");
                MapAdventureGameTest.remoteWorld=true;MapEditor.forceDungeon=false;MapEditor.forceKuudra=false;
                MapEditor.dungeonOnly=true;MapEditor.enabled=true;MapEditor.editing=false;MapEditor.autoRooms=true;
                MapEditor.DATA.reset();DungeonRooms.reset();scans=DungeonRooms.discoveryPasses();
                check(MapEditor.inDungeon(client)&&MapEditor.floor().equals("M7"),"Captured M7 sidebar was not detected");
            });
            context.waitTicks(30);
            context.runOnClient(client->{
                check(DungeonRooms.discoveryPasses()==scans,"Empty non-editing run still performed room discovery");
                check(MapEditor.DATA.snapshot().isEmpty(),"Empty run created map edits");
            });
            world.getServer().runOnServer(MapExitRegressionGameTest::hub);context.waitTicks(10);
            context.runOnClient(client->{
                check(!MapEditor.inDungeon(client),"M7 context survived the Hub sidebar in the same ClientLevel");
                for(int i=0;i<100;i++){long start=System.nanoTime();MapEditor.tick(client);maxTick=Math.max(maxTick,System.nanoTime()-start);}
                check(DungeonRooms.discoveryPasses()==scans,"Leaving an empty run launched discovery");
                check(maxTick<250_000_000L,"Empty exit map tick blocked for "+maxTick/1_000_000.0+" ms");
                System.out.println("MAP_EMPTY_M7_HUB_EXIT maxTickMs="+maxTick/1_000_000.0+" discoveryPasses=0 edits="+MapEditor.DATA.snapshot().size());
            });
            world.getServer().runOnServer(MapAdventureGameTest::capturedM7Sidebar);context.waitTicks(10);
            context.runOnClient(client->{MapEditor.editing=true;});context.waitTicks(20);
            context.runOnClient(client->{
                check(DungeonRooms.discoveryPasses()>scans,"Enabling editing did not resume room discovery");
                MapEditor.editing=false;
                var frame=new RoomLayouts.Frame("floor:M7","Boss",new MapEdits.Position(0,0,0),0);
                var edits=new HashMap<MapEdits.Position,Optional<String>>();var origins=new HashMap<MapEdits.Position,Boolean>();
                for(int x=300;x<316;x++)for(int z=300;z<316;z++){var p=new MapEdits.Position(x,80,z);edits.put(p,Optional.of("minecraft:gold_block"));origins.put(p,false);}
                MapEditor.ROOMS.changeAll(frame,edits,origins);
            });
            boolean restored=false;
            for(int i=0;i<120&&!restored;i++){boolean[] done={false};context.runOnClient(client->done[0]=MapEditor.DATA.snapshot().size()==256);restored=done[0];if(!restored)context.waitTicks(1);}
            check(restored,"Known-origin boss layout was not preloaded while player remained outside unloaded boss chunks");
            context.runOnClient(client->{
                var frame=new RoomLayouts.Frame("floor:M7","Boss",new MapEdits.Position(0,0,0),0);var edits=new HashMap<MapEdits.Position,Optional<String>>();
                for(int x=0;x<16;x++)for(int z=0;z<16;z++)edits.put(new MapEdits.Position(x,80,z),Optional.of("minecraft:gold_block"));
                MapEditor.ROOMS.changeAll(frame,edits);
            });
            boolean originsSaved=false;
            for(int i=0;i<120&&!originsSaved;i++){boolean[] done={false};context.runOnClient(client->done[0]=MapEditor.DATA.snapshot().size()==512&&Boolean.TRUE.equals(MapEditor.ROOMS.originallyAir("floor:M7",new MapEdits.Position(0,80,0))));originsSaved=done[0];if(!originsSaved)context.waitTicks(1);}
            check(originsSaved,"First automatic restore did not retain sampled original-air flags");
            context.runOnClient(client->{check(MapEditor.roomOriginWorkerThread().startsWith("CustomCosmetics-MapPrepare"),"Origin normalization ran on the client thread");System.out.println("MAP_ORIGIN_COMMIT_WORKER="+MapEditor.roomOriginWorkerThread());});
            world.getServer().runOnServer(MapExitRegressionGameTest::hub);context.waitTicks(10);
            context.runOnClient(client->{
                check(!MapEditor.inDungeon(client)&&MapEditor.visible().isEmpty(),"Saved map remained visible outside the dungeon");
                check(MapEditor.ROOMS.edits("floor:M7").size()==512,"Exit deleted the saved boss layout");
                System.out.println("MAP_SAVED_BOSS_PRELOAD_AND_EXIT_PRESERVES_LAYOUT_OK");
            });
        }finally{
            context.runOnClient(client->{MapAdventureGameTest.remoteWorld=false;MapEditor.editing=false;MapEditor.enabled=false;MapEditor.DATA.reset();MapEditor.forceDungeon=false;MapEditor.forceKuudra=false;});
        }
    }
    private static void hub(net.minecraft.server.MinecraftServer server){
        var board=server.getScoreboard();var objective=board.getObjective("cc_exit_hub");
        if(objective==null)objective=board.addObjective("cc_exit_hub",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("SKYBLOCK"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
        var row=board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("cc_exit_hub_location"),objective);row.set(1);row.display(net.minecraft.network.chat.Component.literal("⏣ Dungeon Hub"));
        board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
