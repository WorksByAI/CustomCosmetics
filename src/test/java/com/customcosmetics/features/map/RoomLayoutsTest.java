package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomLayoutsTest {
    @TempDir Path temp;
    @Test void savedRoomFollowsEveryRotationAndTranslation()throws Exception{
        var store=new RoomLayouts();var canonical=new MapEdits.Position(3,71,-7);
        var first=new RoomLayouts.Frame("room:Example","Example",new MapEdits.Position(-185,0,-153),3);
        store.change(first,first.world(canonical),"minecraft:gold_block");store.save(temp.resolve("rooms.json"));
        var reopened=new RoomLayouts();reopened.load(temp.resolve("rooms.json"));assertEquals(1,reopened.count());
        for(int turn=0;turn<4;turn++){var next=new RoomLayouts.Frame(first.key(),first.label(),new MapEdits.Position(-121,0,-89),turn);var edit=reopened.edits(next.key()).entrySet().iterator().next();assertEquals(canonical,edit.getKey());assertEquals(next.origin().add(canonical.rotate(turn)),next.world(edit.getKey()));assertEquals("minecraft:gold_block",edit.getValue());}
    }
    @Test void deletingLastEditPersistsAndOtherRoomsAndFloorsStaySeparate()throws Exception{
        var store=new RoomLayouts();var p=new MapEdits.Position(0,70,0);var a=new RoomLayouts.Frame("room:A","A",p,0);var b=new RoomLayouts.Frame("floor:M7","M7",p,0);
        store.change(a,p,"minecraft:stone");store.change(b,p,"minecraft:air");store.change(a,p,null);store.save(temp.resolve("rooms.json"));
        var copy=new RoomLayouts();copy.load(temp.resolve("rooms.json"));assertTrue(copy.edits(a.key()).isEmpty());assertEquals("minecraft:air",copy.edits(b.key()).get(new MapEdits.Position(0,0,0)));assertThrows(UnsupportedOperationException.class,()->copy.edits(b.key()).clear());
    }
    @Test void corruptFileCannotPartlyReplaceCurrentLibrary()throws Exception{
        var store=new RoomLayouts();var p=new MapEdits.Position(0,70,0);store.change(new RoomLayouts.Frame("safe","safe",p,0),p,"minecraft:gold_block");
        var path=temp.resolve("rooms.json");Files.writeString(path,"{\"version\":1,\"rooms\":{\"good\":[],\"bad\":[{\"x\":0,\"y\":0,\"z\":0,\"material\":\"../escape\"}]}}");
        assertThrows(java.io.IOException.class,()->store.load(path));assertEquals(1,store.count());assertFalse(store.edits("safe").isEmpty());
    }
    @Test void duplicateCoordinatesAndFutureFormatsAreRejected()throws Exception{
        var path=temp.resolve("rooms.json");var store=new RoomLayouts();String row="{\"x\":0,\"y\":70,\"z\":0,\"material\":\"minecraft:stone\"}";
        Files.writeString(path,"{\"version\":1,\"rooms\":{\"A\":["+row+","+row+"]}}");assertThrows(java.io.IOException.class,()->store.load(path));
        Files.writeString(path,"{\"version\":2,\"rooms\":{}}");assertThrows(java.io.IOException.class,()->store.load(path));
    }
    @Test void largeRoomLibrarySavesReloadsAndKeepsOriginsBeyondOldCountAndFileLimits()throws Exception{
        int count=90000;var store=new RoomLayouts();var frame=new RoomLayouts.Frame("room:Large","Large",new MapEdits.Position(0,0,0),1);var changes=new java.util.LinkedHashMap<MapEdits.Position,java.util.Optional<String>>();
        for(int i=0;i<count;i++)changes.put(new MapEdits.Position(i%300,70,i/300),java.util.Optional.of("minecraft:dark_oak_planks"));
        var p=new MapEdits.Position(0,70,0);store.changeAll(frame,changes,java.util.Map.of(p,false));
        var path=temp.resolve("large.json");store.save(path);assertTrue(Files.size(path)>8L*1024*1024,"Fixture must exceed the former file-size cap");
        var reopened=new RoomLayouts();reopened.load(path);assertEquals(count,reopened.edits(frame.key()).size());assertEquals(Boolean.FALSE,reopened.originallyAir(frame.key(),frame.relative(p)));
        var copied=reopened.copy();copied.change(frame,new MapEdits.Position(99999,70,0),"minecraft:gold_block");assertEquals(count,reopened.edits(frame.key()).size());assertEquals(count+1,copied.edits(frame.key()).size());
    }
    @Test void largeSharedBossLayoutsAndImportedLibrariesHaveNoBlockCountCap(){
        var edits=new java.util.LinkedHashMap<MapEdits.Position,String>();for(int i=0;i<70000;i++)edits.put(new MapEdits.Position(i,70,0),"minecraft:stone");
        var store=new RoomLayouts();store.importLayouts(java.util.Map.of("floor:M7",new FmeImport.Layout(edits,null)),true);assertEquals(70000,store.edits("floor:F7").size());
        var flags=new java.util.HashMap<MapEdits.Position,Boolean>();edits.keySet().forEach(p->flags.put(p,false));store.rememberOrigins("floor:F7",flags);assertEquals(Boolean.FALSE,store.originallyAir("floor:M7",new MapEdits.Position(69999,70,0)));
    }
    @Test void clearingAndEditingAnImportedRoomKeepsItsRotationReference()throws Exception{
        var store=new RoomLayouts();var anchor=new RoomLayouts.LegacyAnchor(100,200,3);var point=new MapEdits.Position(1,70,2);store.importLayouts(java.util.Map.of("room:A",new FmeImport.Layout(java.util.Map.of(point,"minecraft:stone"),anchor)),true);
        store.clear("room:A");var frame=new RoomLayouts.Frame("room:A","A",new MapEdits.Position(-185,0,-185),2);store.change(frame,frame.world(point),"minecraft:gold_block");store.save(temp.resolve("rooms.json"));var loaded=new RoomLayouts();loaded.load(temp.resolve("rooms.json"));assertEquals(anchor,loaded.anchor("room:A"));assertEquals("minecraft:gold_block",loaded.edits("room:A").get(point));
    }
    @Test void f7AndM7BossEditsShareSavesDeletesAndReset()throws Exception{
        var store=new RoomLayouts();var zero=new MapEdits.Position(0,0,0);var p=new MapEdits.Position(100,70,10);var f7=new RoomLayouts.Frame("floor:F7","F7",zero,0);var m7=new RoomLayouts.Frame("floor:M7","M7",zero,0);
        assertEquals(f7.key(),m7.key());store.change(f7,p,"minecraft:gold_block");assertEquals("minecraft:gold_block",store.edits("floor:M7").get(p));store.change(m7,p,"minecraft:diamond_block");assertEquals("minecraft:diamond_block",store.edits("floor:F7").get(p));
        var path=temp.resolve("shared.json");store.save(path);var loaded=new RoomLayouts();loaded.load(path);assertEquals(1,loaded.count());assertEquals(store.edits(f7.key()),loaded.edits(m7.key()));assertFalse(Files.readString(path).contains("\"floor:F7\""));assertFalse(Files.readString(path).contains("\"floor:M7\""));
        loaded.change(m7,p,null);loaded.save(path);var deleted=new RoomLayouts();deleted.load(path);assertTrue(deleted.edits(f7.key()).isEmpty());deleted.change(f7,p,"minecraft:stone");deleted.clear("floor:M7");assertTrue(deleted.edits("floor:F7").isEmpty());
    }
    @Test void legacyBossFloorsMergeWithAnExactBackupAndF7ConflictPriority()throws Exception{
        String original="{\"version\":1,\"rooms\":{\"floor:M7\":[{\"x\":1,\"y\":70,\"z\":2,\"material\":\"minecraft:diamond_block\"},{\"x\":3,\"y\":70,\"z\":4,\"material\":\"minecraft:gold_block\"}],\"floor:F7\":[{\"x\":1,\"y\":70,\"z\":2,\"material\":\"minecraft:stone\"}],\"floor:M6\":[]}}";
        var path=temp.resolve("legacy.json");Files.writeString(path,original);var store=new RoomLayouts();store.load(path);assertEquals(original,Files.readString(path));assertEquals(2,store.count());assertEquals(2,store.edits("floor:F7").size());assertEquals("minecraft:stone",store.edits("floor:M7").get(new MapEdits.Position(1,70,2)));
        try(var files=Files.list(temp)){var backup=files.filter(p->p.getFileName().toString().contains("before-shared-f7-m7")).findFirst().orElseThrow();assertEquals(original,Files.readString(backup));}
        store.save(path);var loaded=new RoomLayouts();loaded.load(path);assertEquals(store.edits("floor:F7"),loaded.edits("floor:M7"));assertTrue(Files.readString(path).contains("floor:F7+M7"));
    }
    @Test void savedEditsKeepRealSurfacesDistinctFromAirAdditions()throws Exception{
        var store=new RoomLayouts();var real=new MapEdits.Position(10,65,20);var air=new MapEdits.Position(11,65,20);var frame=new RoomLayouts.Frame("floor:M7","Boss",new MapEdits.Position(0,0,0),0);
        store.changeAll(frame,java.util.Map.of(real,java.util.Optional.of("minecraft:chest[facing=west]"),air,java.util.Optional.of("minecraft:water")),java.util.Map.of(real,false,air,true));var path=temp.resolve("origins.json");store.save(path);var copy=new RoomLayouts();copy.load(path);
        assertEquals(Boolean.FALSE,copy.originallyAir("floor:F7",real));assertEquals(Boolean.TRUE,copy.originallyAir("floor:M7",air));assertEquals("minecraft:chest[facing=west]",copy.edits(frame.key()).get(real));copy.changeAll(frame,java.util.Map.of(real,java.util.Optional.empty()));assertNull(copy.originallyAir(frame.key(),real));
        copy.importLayouts(java.util.Map.of(frame.key(),new FmeImport.Layout(java.util.Map.of(air,"minecraft:lava"),null)),true);assertNull(copy.originallyAir(frame.key(),air));copy.rememberOrigin(frame.key(),air,false);copy.save(path);var again=new RoomLayouts();again.load(path);assertEquals(Boolean.FALSE,again.originallyAir(frame.key(),air));
    }
}
