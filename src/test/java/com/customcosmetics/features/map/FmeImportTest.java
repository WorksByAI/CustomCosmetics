package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FmeImportTest {
    @TempDir Path temp;
    @Test void importsRealRoomFloorAndFavoriteFormatsWithoutChangingSource()throws Exception{
        String json="{\"Altar\":{\"baseCore\":1823952110,\"rotationCore\":1234,\"coreDistance\":3,\"preBlocks\":{\"minecraft:wool[color=red]\":[\"1, 70, -2\"],\"minecraft:air\":[{\"x\":2,\"y\":71,\"z\":3}]}}}";
        var file=temp.resolve("extrasConfig.json");Files.writeString(file,json);Files.writeString(temp.resolve("floorsConfig.json"),"{\"7\":{\"minecraft:stone[variant=granite]\":[\"-185, 64, -25\"]}}");Files.writeString(temp.resolve("favBlocks.json"),"[\"minecraft:wool14\",\"minecraft:sea_lantern0\"]");
        var plan=FmeImport.read(file,LegacyMaterials::convert,LegacyMaterials::favorite);assertEquals(2,plan.layouts().size());assertEquals(3,plan.blocks());assertEquals(0,plan.skipped());assertEquals(json,Files.readString(file));
        var room=plan.layouts().get("room:Altar");assertEquals("minecraft:red_wool",room.edits().get(new MapEdits.Position(1,70,-2)));assertEquals(new RoomLayouts.LegacyAnchor(1823952110,1234,3),room.anchor());assertTrue(plan.layouts().containsKey("floor:F7+M7"));assertTrue(plan.favorites().contains("minecraft:red_wool"),plan.favorites().toString());assertTrue(plan.favorites().contains("minecraft:sea_lantern"),plan.favorites().toString());
        // The original FME importer feeds the new compressed store, with no format conversion by users.
        var storage=new MapStorage(temp.resolve("modern"));var settings=storage.initialize();var layouts=new RoomLayouts();layouts.importTo(storage.rooms,storage.root.resolve("backups/import"),plan.layouts(),true);
        settings.add("favorites",new com.google.gson.Gson().toJsonTree(plan.favorites()));storage.saveSettings(settings);
        var reopened=new RoomLayouts();reopened.load(storage.rooms);assertEquals(room.edits(),reopened.edits("room:Altar"));assertEquals(room.anchor(),reopened.anchor("room:Altar"));assertEquals(plan.layouts().get("floor:F7+M7").edits(),reopened.edits("floor:M7"));
        var placement=new RoomLayouts.Frame("room:Altar","Altar",new MapEdits.Position(-185,0,-153),3);var relative=new MapEdits.Position(1,70,-2);assertEquals(relative,placement.relative(placement.world(relative)));
        assertEquals(plan.favorites().size(),storage.initialize().getAsJsonArray("favorites").size());assertEquals(json,Files.readString(file));
    }
    @Test void legacyFloorImportAcceptsMoreThanTheOldTotalBlockLimit()throws Exception{
        var json=new StringBuilder("{\"7\":{\"minecraft:stone\":[");for(int i=0;i<70000;i++){if(i>0)json.append(',');json.append('"').append(i).append(", 70, 0\"");}json.append("]}}");
        var path=temp.resolve("floorsConfig.json");Files.writeString(path,json);var plan=FmeImport.read(path,id->id,id->id);assertEquals(70000,plan.blocks());assertEquals(70000,plan.layouts().get("floor:F7+M7").edits().size());
    }
    @Test void invalidItemsAreReportedAndDuplicatesRejectTheImport()throws Exception{
        var file=temp.resolve("extrasConfig.json");Files.writeString(file,"{\"A\":{\"baseCore\":1,\"rotationCore\":2,\"preBlocks\":{\"bad:block\":[\"1, 2, 3\"]}}}");var plan=FmeImport.read(file,id->{throw new IllegalArgumentException();},id->id);assertEquals(1,plan.skipped());assertFalse(plan.warnings().isEmpty());assertTrue(plan.layouts().isEmpty());
        Files.writeString(file,"{\"A\":{\"baseCore\":1,\"rotationCore\":2,\"preBlocks\":{\"minecraft:stone\":[\"1, 2, 3\",\"1, 2, 3\"]}}}");assertThrows(java.io.IOException.class,()->FmeImport.read(file,id->id,id->id));
    }
    @Test void mergingKeepsExistingLayoutsUnlessReplacementIsChosen()throws Exception{
        var store=new RoomLayouts();var p=new MapEdits.Position(1,70,2);store.change(new RoomLayouts.Frame("room:A","A",new MapEdits.Position(0,0,0),0),p,"minecraft:gold_block");var imported=Map.of("room:A",new FmeImport.Layout(Map.of(p,"minecraft:stone"),new RoomLayouts.LegacyAnchor(1,2,3)));
        store.importLayouts(imported,false);assertEquals("minecraft:gold_block",store.edits("room:A").get(p));assertNull(store.anchor("room:A"));store.importLayouts(imported,true);store.save(temp.resolve("rooms.json"));var copy=new RoomLayouts();copy.load(temp.resolve("rooms.json"));assertEquals("minecraft:stone",copy.edits("room:A").get(p));assertEquals(3,copy.anchor("room:A").distance());
    }
    @Test void vanillaFlatteningPreservesLegacyVariantsAndProperties(){
        assertEquals("minecraft:lime_stained_glass",LegacyMaterials.convert("minecraft:stained_glass[color=lime]"));assertEquals("minecraft:red_wool",LegacyMaterials.convert("35:14"));assertTrue(LegacyMaterials.convert("minecraft:stone_slab[variant=stone,half=top]").contains("type=top"));assertEquals("minecraft:granite",LegacyMaterials.convert("minecraft:stone[variant=granite]"));
    }
    @Test void slabAndWoodNamesTrackPostFlatteningRenames(){assertTrue(LegacyMaterials.convert("44:0").startsWith("minecraft:smooth_stone_slab["));assertEquals("minecraft:oak_wood",LegacyMaterials.modernName("minecraft:oak_bark"));assertThrows(IllegalArgumentException.class,()->LegacyMaterials.convert("253:0"));}
    @Test void separateLegacySeventhFloorConfigsImportAsOneSharedLayout()throws Exception{
        var file=temp.resolve("floorsConfig.json");String json="{\"M7\":{\"minecraft:gold_block\":[\"1, 70, 2\",\"3, 70, 4\"]},\"F7\":{\"minecraft:stone\":[\"1, 70, 2\"]}}";Files.writeString(file,json);var plan=FmeImport.read(file,id->id,id->id);assertEquals(1,plan.layouts().size());assertEquals(2,plan.blocks());assertEquals("minecraft:stone",plan.layouts().get("floor:F7+M7").edits().get(new MapEdits.Position(1,70,2)));assertFalse(plan.warnings().isEmpty());assertEquals(json,Files.readString(file));
    }
}
