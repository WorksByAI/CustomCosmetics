package com.customcosmetics.gametest;

import com.customcosmetics.features.map.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.*;

public final class MapEditPerformanceGameTest implements FabricClientGameTest {
    public static boolean active;public static long maxFrame,maxTick;
    private static final java.util.List<java.lang.management.GarbageCollectorMXBean> collectors=java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
    public static long mapGcTime(){long total=0;for(var bean:collectors)total+=Math.max(0,bean.getCollectionTime());return total;}
    private static final int COUNT=64*64*256;
    private java.util.concurrent.CompletableFuture<Void> pending;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{com.customcosmetics.config.ConfigManager.offlineMode=true;com.customcosmetics.config.ConfigManager.syncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("forceload add -32 -32 47 47");world.getServer().runCommand("tp @a 0.5 65 4 180 0");context.waitTicks(25);
            world.getServer().runCommand("fill -3 63 -3 3 64 5 minecraft:stone");world.getServer().runCommand("fill 0 65 0 0 72 0 minecraft:stone");world.getServer().runCommand("tp @a 0.5 65 4 180 0");context.waitTicks(5);
            context.runOnClient(client->{MapEditor.forceKuudra(false);MapEditor.forceDungeon(true);MapEditor.enabled=false;MapEditor.DATA.reset();MapEditor.autoRooms=true;MapEditor.ROOMS.clear("floor:F7+M7");
                var changes=new HashMap<MapEdits.Position,Optional<String>>();var origins=new HashMap<MapEdits.Position,Boolean>();
                for(int x=-32;x<32;x++)for(int y=-64;y<192;y++)for(int z=-32;z<32;z++){var p=new MapEdits.Position(x,y,z);changes.put(p,Optional.of("minecraft:deepslate_bricks"));origins.put(p,false);}
                MapEditor.ROOMS.changeAll(MapEditor.currentRoom(),changes,origins);MapEditor.enabled=true;maxFrame=maxTick=0;active=true;MapEditor.tick(client);
            });
            for(int i=0;i<400;i++){final boolean[] done={false};context.runOnClient(client->done[0]=MapEditor.visible().size()>=COUNT);if(done[0])break;context.waitTicks(1);}
            context.runOnClient(client->check(MapEditor.visible().size()>=COUNT,"Restoration incomplete: "+MapEditor.visible().size()+" / "+COUNT+"; "+MapEditor.status()));context.waitTicks(3);
            context.runOnClient(client->{MapEditor.editing=true;MapEditor.material="minecraft:gold_block";long began=System.nanoTime();MapEditor.click(0);long edit=(System.nanoTime()-began)/1_000_000;
                check(MapEditor.DATA.snapshot().size()==COUNT-1,"Single edit lost the million-block baseline");check(edit<250,"Single edit still blocks the client: "+edit+"ms");
                began=System.nanoTime();MapEditor.undo();long undo=(System.nanoTime()-began)/1_000_000;check(MapEditor.DATA.snapshot().size()==COUNT,"Sparse undo lost baseline edits");check(undo<250,"Undo still blocks the client: "+undo+"ms");
                System.out.println("MAP_MILLION_RESTORE_SPARSE_EDIT_MS="+edit+" UNDO_MS="+undo+" MAX_FRAME_MS="+maxFrame/1_000_000+" MAX_MAP_TICK_MS="+maxTick/1_000_000);check(maxTick<250_000_000L,"Map tick blocked gameplay");check(maxFrame<500_000_000L,"A frame stalled during restoration");active=false;MapEditor.editing=false;});
            context.runOnClient(client->{active=true;MapEditor.presetName="Million performance";pending=MapEditor.savePresetInteractive();});context.waitFor(client->pending.isDone());context.runOnClient(client->pending.join());
            context.runOnClient(client->{pending=MapEditor.loadPresetInteractive(null);});context.waitFor(client->pending.isDone());context.runOnClient(client->{pending.join();check(MapEditor.visible().size()==COUNT,"Large preset load lost edits");pending=MapEditor.renamePresetInteractive("Million renamed");});
            context.waitFor(client->pending.isDone());context.runOnClient(client->{pending.join();MapEditor.deletePreset();active=false;System.out.println("MAP_MILLION_PRESET_SAVE_LOAD_RENAME_MAX_FRAME_MS="+maxFrame/1_000_000);check(maxFrame<500_000_000L,"A large preset operation stalled a frame");});
            context.runOnClient(client->{active=true;select("first",new net.minecraft.core.BlockPos(-20,65,-20));select("second",new net.minecraft.core.BlockPos(19,104,19));MapEditor.material="minecraft:gold_block";MapEditor.fill();});
            var filled=new MapEdits.Position(0,70,0);
            context.waitFor(client->MapEditor.DATA.snapshot().get(filled).material().equals("minecraft:gold_block")&&!MapEditor.busy());
            context.waitTicks(1);context.runOnClient(client->{MapEditor.undoInteractive();});
            context.waitFor(client->!MapEditor.busy()&&MapEditor.DATA.snapshot().get(filled).material().equals("minecraft:deepslate_bricks"));
            context.runOnClient(client->{active=false;check(MapEditor.DATA.snapshot().size()==COUNT,"Region fill undo lost baseline");System.out.println("MAP_MILLION_REGION_FILL_UNDO_MAX_FRAME_MS="+maxFrame/1_000_000);check(maxFrame<500_000_000L,"Region fill stalled a frame");});
            context.waitTicks(20);context.runOnClient(client->{MapEditor.flushRooms();check(MapEditor.ROOMS.edits("floor:F7+M7").size()==COUNT,"Background saves lost edits");MapEditor.enabled=false;MapEditor.autoRooms=false;MapEditor.DATA.reset();MapEditor.forceDungeon(false);});
        }
        System.out.println("MILLION_BLOCK_RESTORE_SPARSE_EDIT_UNDO_BACKGROUND_SAVE_OK");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void select(String name,net.minecraft.core.BlockPos value){try{var field=MapEditor.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
