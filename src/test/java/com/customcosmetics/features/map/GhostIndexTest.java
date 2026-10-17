package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GhostIndexTest {
    @Test void denseGhostQueriesHaveAHardCandidateBudgetAndPreferNearbySections(){
        var edits=new ArrayList<MapEdits.Edit>();for(int x=-32;x<32;x++)for(int z=-32;z<32;z++)edits.add(new MapEdits.Edit(new MapEdits.Position(x,64,z),"minecraft:stone",true));
        var index=new GhostIndex(edits);var found=new ArrayList<MapEdits.Edit>();index.nearby(1,65,1,96,100,found::add);assertEquals(100,found.size());assertTrue(found.stream().allMatch(e->e.position().x()>=0&&e.position().x()<16&&e.position().z()>=0&&e.position().z()<16));
    }
    @Test void individualGhostChangesUpdateOnlyTheirSection(){
        var p=new MapEdits.Position(-17,30,-1);var index=new GhostIndex(List.of());var ghost=new MapEdits.Edit(p,"minecraft:gold_block",true);
        index.update(p,ghost);assertEquals(1,index.size());var found=new ArrayList<MapEdits.Edit>();index.nearby(-17,30,-1,8,found::add);assertEquals(List.of(ghost),found);
        index.update(p,new MapEdits.Edit(p,"minecraft:stone",false));assertEquals(0,index.size());index.update(p,ghost);index.update(p,null);assertEquals(0,index.size());
    }
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
