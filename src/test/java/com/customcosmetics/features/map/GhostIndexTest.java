package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GhostIndexTest {
    @Test void denseOrdinaryReplacementsNeverBecomeStandaloneRenderCandidates(){
        var edits=new ArrayList<MapEdits.Edit>();
        for(int x=-40;x<40;x++)for(int y=0;y<64;y++)for(int z=-40;z<40;z++)edits.add(new MapEdits.Edit(new MapEdits.Position(x,y,z),"minecraft:deepslate_bricks",false));
        var index=new GhostIndex(edits);assertEquals(0,index.size());
        index.nearby(0,32,0,96,e->fail("An ordinary retexture entered the per-frame renderer"));
    }
    @Test void ghostsAreSelectedByThreeDimensionalDistanceIncludingNegativeSections(){
        var near=new MapEdits.Edit(new MapEdits.Position(-17,30,-1),"minecraft:chest",true);
        var far=new MapEdits.Edit(new MapEdits.Position(500,30,0),"minecraft:water",true);
        var high=new MapEdits.Edit(new MapEdits.Position(-17,500,-1),"minecraft:stone",true);
        var real=new MapEdits.Edit(new MapEdits.Position(-17,30,-2),"minecraft:stone",false);
        var index=new GhostIndex(List.of(near,far,high,real));var found=new ArrayList<MapEdits.Edit>();
        index.nearby(-16.5,30.5,-.5,96,found::add);assertEquals(List.of(near),found);assertEquals(3,index.size());
    }
}
