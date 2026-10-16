package com.customcosmetics.features.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DenseReplacementTest {
    @TempDir Path temp;
    @Test void denseThreeDimensionalBossGeometryDoesNotHangDuringSnapshotAndRoomSave(){
        assertTimeoutPreemptively(Duration.ofSeconds(20),()->{
            long start=System.nanoTime();int count=80*80*64;
            var plan=new MaterialReplacementPlan(Map.of(),"minecraft:stone_bricks","minecraft:deepslate_bricks");
            for(int x=-200;x<-120;x++)for(int y=30;y<94;y++)for(int z=-200;z<-120;z++)plan.accept(new MapEdits.Position(x,y,z),"minecraft:stone_bricks");
            var snapshot=plan.result();assertEquals(count,snapshot.size());
            var data=new MapEdits();data.replace(snapshot);assertSame(snapshot,data.snapshot());
            var changes=new HashMap<MapEdits.Position,Optional<String>>();snapshot.forEach((p,e)->changes.put(p,Optional.of(e.material())));
            var store=new RoomLayouts();var candidate=store.copy();var frame=new RoomLayouts.Frame("floor:F7","Boss",new MapEdits.Position(0,0,0),0);
            candidate.changeAll(frame,changes);assertTrue(store.edits(frame.key()).isEmpty());store.adopt(candidate);
            assertEquals(count,store.edits("floor:M7").size());assertEquals("minecraft:deepslate_bricks",store.edits(frame.key()).get(new MapEdits.Position(-121,93,-121)));
            store.save(temp.resolve("dense.json"));assertTrue(data.undo());assertTrue(data.snapshot().isEmpty());
            System.out.println("DENSE_409600_REPLACEMENT_SNAPSHOT_ROOM_SAVE_UNDO_MS="+(System.nanoTime()-start)/1_000_000);
        });
    }
    @Test void snapshotsAreDefensiveAndRejectMutationThroughEntries(){
        var p=new MapEdits.Position(1,2,3);var source=new HashMap<MapEdits.Position,String>();source.put(p,"minecraft:stone");
        var snapshot=MapEdits.immutable(source);source.clear();assertEquals("minecraft:stone",snapshot.get(new MapEdits.Position(1,2,3)));
        assertSame(snapshot,MapEdits.immutable(snapshot));assertThrows(UnsupportedOperationException.class,()->snapshot.entrySet().iterator().next().setValue("minecraft:air"));
        assertThrows(UnsupportedOperationException.class,()->snapshot.clear());assertEquals(p.hashCode(),new MapEdits.Position(1,2,3).hashCode());
        source.put(p,null);assertThrows(NullPointerException.class,()->MapEdits.immutable(source));
    }
}
