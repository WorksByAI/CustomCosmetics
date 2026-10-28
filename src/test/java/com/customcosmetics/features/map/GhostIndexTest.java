package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GhostIndexTest {
    @Test void leavingAnArenaStopsObsoleteLargeIndexWorkWithinOneBatch(){
        var visited=new java.util.concurrent.atomic.AtomicInteger();var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        Collection<MapEdits.Edit> edits=new AbstractCollection<>(){
            public int size(){return 2_000_000;}
            public Iterator<MapEdits.Edit> iterator(){return new Iterator<>(){int at;public boolean hasNext(){return at<size();}public MapEdits.Edit next(){int n=at++;visited.incrementAndGet();if(n==300)cancelled.set(true);return new MapEdits.Edit(new MapEdits.Position(n,64,0),"minecraft:stone",false);}};}
        };
        assertThrows(java.util.concurrent.CancellationException.class,()->new GhostIndex(edits,cancelled::get));
        assertTrue(visited.get()<=557,"Cancelled work kept walking a two-million-block source");
        assertTrue(visited.get()>=301);
    }
    @Test void denseGhostQueriesHaveAHardCandidateBudgetAndPreferNearbySections(){
        var edits=new ArrayList<MapEdits.Edit>();for(int x=-32;x<32;x++)for(int z=-32;z<32;z++)edits.add(new MapEdits.Edit(new MapEdits.Position(x,64,z),"minecraft:stone",true));
        var index=new GhostIndex(edits);var found=new ArrayList<MapEdits.Edit>();index.nearby(1,65,1,96,100,found::add);assertEquals(100,found.size());assertTrue(found.stream().allMatch(e->e.position().x()>=0&&e.position().x()<16&&e.position().z()>=0&&e.position().z()<16));
    }
    @Test void individualGhostChangesUpdateOnlyTheirSection(){
        var p=new MapEdits.Position(-17,30,-1);var index=new GhostIndex(List.of());var ghost=new MapEdits.Edit(p,"minecraft:gold_block",true);
        index.update(p,null,ghost);assertEquals(1,index.size());var found=new ArrayList<MapEdits.Edit>();index.nearby(-17,30,-1,8,found::add);assertEquals(List.of(ghost),found);
        var ordinary=new MapEdits.Edit(p,"minecraft:stone",false);
        index.update(p,ghost,ordinary);assertEquals(0,index.size());assertTrue(index.hasOrdinaryEdits(-2,-1));
        index.update(p,ordinary,ghost);assertFalse(index.hasOrdinaryEdits(-2,-1));index.update(p,ghost,null);assertEquals(0,index.size());
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
    @Test void removingOneOrdinaryEditKeepsTheOtherContainerCandidateInItsChunk(){
        var a=new MapEdits.Edit(new MapEdits.Position(-17,30,-1),"minecraft:gold_block",false);
        var b=new MapEdits.Edit(new MapEdits.Position(-18,31,-2),"minecraft:gold_block",false);
        var index=new GhostIndex(List.of(a,b));assertTrue(index.hasOrdinaryEdits(-2,-1));assertFalse(index.hasOrdinaryEdits(-1,-1));
        index.update(a.position(),a,null);assertTrue(index.hasOrdinaryEdits(-2,-1));
        index.update(b.position(),b,null);assertFalse(index.hasOrdinaryEdits(-2,-1));
    }
    @Test void warmedMovingQueriesDoNotAllocateOneCoordinatePerVisitedSection(){
        var edits=new ArrayList<MapEdits.Edit>();
        for(int x=-64;x<=64;x+=16)for(int y=0;y<=128;y+=16)for(int z=-64;z<=64;z+=16)
            edits.add(new MapEdits.Edit(new MapEdits.Position(x,y,z),"minecraft:stone",true));
        var index=new GhostIndex(edits);var counter=new int[1];java.util.function.Consumer<MapEdits.Edit> consume=e->counter[0]++;
        for(int i=0;i<1000;i++)index.nearby(i%8*.25,64,i%7*.25,96,4096,consume);
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocation=(com.sun.management.ThreadMXBean)bean;
        org.junit.jupiter.api.Assumptions.assumeTrue(allocation.isThreadAllocatedMemorySupported());
        if(!allocation.isThreadAllocatedMemoryEnabled())allocation.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId(),before=allocation.getThreadAllocatedBytes(thread);
        for(int i=0;i<1000;i++)index.nearby(i%8*.25,64,i%7*.25,96,4096,consume);
        long bytes=allocation.getThreadAllocatedBytes(thread)-before;
        assertTrue(counter[0]>1000);assertTrue(bytes<131072,"Moving ghost query allocated "+bytes+" bytes");
        System.out.println("MAP_GHOST_1000_MOVING_QUERIES_ALLOCATED_BYTES="+bytes);
    }
}
