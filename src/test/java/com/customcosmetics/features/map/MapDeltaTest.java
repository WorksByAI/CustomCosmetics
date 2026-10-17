package com.customcosmetics.features.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapDeltaTest {
    @TempDir Path directory;
    private static final MapEdits.Position P=new MapEdits.Position(5,6,7);
    /** A million-entry base that fails immediately if a supposedly sparse edit enumerates it. */
    private static <V> Map<MapEdits.Position,V> virtual(V value){return new AbstractMap<>(){
        @Override public int size(){return 1_000_000;}
        @Override public V get(Object p){return P.equals(p)?value:null;}
        @Override public boolean containsKey(Object p){return P.equals(p);}
        @Override public Set<Entry<MapEdits.Position,V>> entrySet(){throw new AssertionError("Single edit traversed the entire map");}
    };}
    @Test void singleEditRemovalAndUndoNeverTraverseAMillionEntryBase(){
        var old=new MapEdits.Edit(P,"minecraft:stone",false);var data=new MapEdits();var base=MapEdits.own(virtual(old));data.replace(base);
        var replacement=new MapEdits.Edit(P,"minecraft:gold_block",false);data.put(replacement);assertEquals(replacement,data.snapshot().get(P));assertEquals(old,base.get(P));assertEquals(1_000_000,data.snapshot().size());
        assertEquals(Set.of(P),data.undoPositions());data.remove(P);assertEquals(999_999,data.snapshot().size());assertNull(data.snapshot().get(P));
        data.undo();assertEquals(replacement,data.snapshot().get(P));data.undo();assertSame(base,data.snapshot());
    }
    @Test void sparseSnapshotsRemainImmutableAndSerializeExactly(){
        var base=MapEdits.immutable(Map.of(P,"minecraft:stone"));var other=new MapEdits.Position(8,9,10);
        var one=MapEdits.change(base,Map.of(other,"minecraft:gold_block"));var two=MapEdits.change(one,Collections.singletonMap(P,null));
        assertEquals(Map.of(P,"minecraft:stone"),base);assertEquals(2,one.size());assertEquals(Map.of(other,"minecraft:gold_block"),two);
        assertThrows(UnsupportedOperationException.class,()->two.entrySet().iterator().next().setValue("minecraft:air"));
        assertEquals(Set.of(P),MapEdits.differences(one,two));assertEquals(Set.of(P),MapEdits.differences(two,one));
    }
    @Test void roomChangesDoNotEnumerateLargeMaterialOrOriginTables()throws Exception{
        var rooms=new RoomLayouts();var frame=new RoomLayouts.Frame("floor:F7","Boss",new MapEdits.Position(0,0,0),0);
        var materials=RoomLayouts.class.getDeclaredField("rooms");materials.setAccessible(true);((Map<String,Map<MapEdits.Position,String>>)materials.get(rooms)).put(frame.key(),MapEdits.own(virtual("minecraft:stone")));
        var origins=RoomLayouts.class.getDeclaredField("origins");origins.setAccessible(true);((Map<String,Map<MapEdits.Position,Boolean>>)origins.get(rooms)).put(frame.key(),MapEdits.own(virtual(false)));
        rooms.changeAll(frame,Map.of(P,Optional.of("minecraft:gold_block")),Map.of(P,false));assertEquals("minecraft:gold_block",rooms.edits(frame.key()).get(P));assertEquals(false,rooms.originallyAir(frame.key(),P));
    }
    @Test void backgroundSaveAcknowledgesOnlyTheExactSnapshotThatWasWritten()throws Exception{
        var tasks=new ArrayList<Runnable>();var queue=new RoomSaveQueue(tasks::add);var rooms=new RoomLayouts();var frame=new RoomLayouts.Frame("floor:F7","Boss",new MapEdits.Position(0,0,0),0);
        rooms.change(frame,P,"minecraft:stone");queue.tick(rooms,directory);assertEquals(1,tasks.size());
        rooms.change(frame,P,"minecraft:gold_block");tasks.removeFirst().run();queue.tick(rooms,directory);assertTrue(rooms.hasChanges());assertEquals(1,tasks.size());
        var disk=new RoomLayouts();disk.load(directory);assertEquals("minecraft:stone",disk.edits(frame.key()).get(P));
        tasks.removeFirst().run();queue.tick(rooms,directory);assertFalse(rooms.hasChanges());disk.load(directory);assertEquals("minecraft:gold_block",disk.edits(frame.key()).get(P));
    }
    @Test void backgroundBulkDiffUsesSparseChangesAndPreservesOtherRooms(){
        var frame=new RoomLayouts.Frame("floor:F7","Boss",new MapEdits.Position(0,0,0),0);var baseline=new RoomLayouts();baseline.change(frame,P,"minecraft:stone");
        var before=MapEdits.own(virtual(new MapEdits.Edit(P,"minecraft:stone",false)));var after=MapEdits.change(before,Map.of(P,new MapEdits.Edit(P,"minecraft:gold_block",false)));
        var result=MapChange.prepare(before,after,baseline.copy(),p->frame,(id,t)->id);
        assertEquals("minecraft:gold_block",result.rooms().edits(frame.key()).get(P));assertEquals("minecraft:stone",baseline.edits(frame.key()).get(P));assertTrue(result.unknown().isEmpty());assertFalse(baseline.sameData(result.rooms()));
    }
}
