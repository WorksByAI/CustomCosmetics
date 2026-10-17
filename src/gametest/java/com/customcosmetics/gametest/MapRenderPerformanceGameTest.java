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
        System.out.println("DENSE_MAP_RENDER_NO_ORDINARY_PER_FRAME_CANDIDATES_OK");
    }
    private static void report(Object client,String phase){try{System.out.println("MAP_RENDER_"+phase+"_FPS="+client.getClass().getMethod("getFps").invoke(client));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
