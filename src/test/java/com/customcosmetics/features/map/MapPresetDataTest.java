// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapPresetDataTest {
    @TempDir Path directory;
    private static final MapEdits.Position ZERO=new MapEdits.Position(0,0,0);
    @Test void canonicalEditsFollowEveryDetectedRotationAndPreserveGhostFlags(){
        for(int from=0;from<4;from++)for(int to=0;to<4;to++){
            var source=new RoomLayouts.Frame("room:Altar","Altar",new MapEdits.Position(-185,0,-153),from);
            var target=new RoomLayouts.Frame("room:Altar","Altar",new MapEdits.Position(-25,0,-57),to);
            var relative=new MapEdits.Position(3,70,-5);
            var edits=List.of(new MapEdits.Edit(source.world(relative),"minecraft:oak_stairs[facing="+from+"]",false),new MapEdits.Edit(source.world(new MapEdits.Position(4,70,-5)),"minecraft:gold_block",true));
            // A deterministic state rotator keeps this test independent of Minecraft's registry bootstrap.
            java.util.function.BiFunction<String,Integer,String> rotate=(id,turns)->id.contains("facing=")?"minecraft:oak_stairs[facing="+Math.floorMod(Integer.parseInt(id.substring(id.length()-2,id.length()-1))+turns,4)+"]":id;
            var preset=MapPresetData.capture("Theme",source,edits,rotate);
            var placed=MapPresetData.place(preset,target,0,rotate);
            assertEquals("minecraft:oak_stairs[facing="+to+"]",placed.get(target.world(relative)).material());
            assertFalse(placed.get(target.world(relative)).originallyAir());
            assertTrue(placed.get(target.world(new MapEdits.Position(4,70,-5))).originallyAir());
        }
    }
    @Test void mismatchCannotPlaceAndF7M7ShareOneLayout(){
        var f7=new RoomLayouts.Frame("floor:F7","F7",ZERO,0);
        var preset=MapPresetData.capture("Boss",f7,List.of(new MapEdits.Edit(new MapEdits.Position(100,60,200),"minecraft:gold_block",false)),(id,t)->id);
        assertEquals("F7 / M7 Boss",MapPresetData.roomLabel(preset));
        assertEquals(1,MapPresetData.place(preset,new RoomLayouts.Frame("floor:M7","M7",ZERO,0),0,(id,t)->id).size());
        assertThrows(IllegalArgumentException.class,()->MapPresetData.place(preset,new RoomLayouts.Frame("room:Other","Other",ZERO,0),0,(id,t)->id));
        assertEquals("",MapPresetData.roomKey(new MapEdits.Preset("Old",List.of(),"legacyhash")));
    }
    @Test void metadataSurvivesDiskRenameAndDeleteIsRecoverable()throws Exception{
        var storage=new MapStorage(directory);storage.initialize();
        var preset=MapPresetData.capture("Brown",new RoomLayouts.Frame("floor:F7","Boss",ZERO,0),List.of(new MapEdits.Edit(ZERO,"minecraft:dark_oak_planks",false)),(id,t)->id);
        storage.savePreset(preset);storage.renamePreset(preset,"New Brown");
        var loaded=storage.loadPresets();assertEquals(Set.of("New Brown"),loaded.keySet());
        assertEquals(preset.fingerprint(),loaded.get("New Brown").fingerprint());
        storage.savePreset(preset);assertThrows(java.io.IOException.class,()->storage.renamePreset(preset,"New Brown"));
        assertEquals(2,storage.loadPresets().size());storage.deletePreset("New Brown");assertEquals(Set.of("Brown"),storage.loadPresets().keySet());
        try(var files=Files.walk(storage.root.resolve("backups/presets"))){assertEquals(2,files.filter(p->p.toString().endsWith(".ccmap")).count());}
    }
}
