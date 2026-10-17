// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class MapStorageTest {
    @Test void externalFilenameStillSupportsOverwriteRenameAndRecoverableDelete()throws Exception{
        var store=new MapStorage(temp);store.initialize();var preset=new MapEdits.Preset("Shared",List.of(new MapEdits.Edit(ZERO,"minecraft:stone",false)),"");store.savePreset(preset);
        Path external=store.presets.resolve("好友改过的名字.ccmap");Files.move(MapLayoutCodec.path(store.presets,"Shared"),external);store.loadPresets();
        var replacement=new MapEdits.Preset("Shared",List.of(new MapEdits.Edit(ZERO,"minecraft:gold_block",false)),"");store.savePreset(replacement);
        assertFalse(Files.exists(MapLayoutCodec.path(store.presets,"Shared")));assertEquals("minecraft:gold_block",MapLayoutCodec.read(external).edits().get(ZERO));
        store.renamePreset(replacement,"Renamed");assertFalse(Files.exists(external));assertEquals(Set.of("Renamed"),store.loadPresets().keySet());
        store.deletePreset("Renamed");assertTrue(store.loadPresets().isEmpty());try(var files=Files.walk(store.root.resolve("backups/presets"))){assertEquals(2,files.filter(Files::isRegularFile).count());}
    }
    @Test void rescanningReadsAddedReplacedAndDeletedFilesRegardlessOfFilename()throws Exception{
        var store=new MapStorage(temp);store.initialize();
        store.savePreset(new MapEdits.Preset("Original",List.of(new MapEdits.Edit(ZERO,"minecraft:stone",false)),""));
        var cached=store.loadPresets();assertEquals(Set.of("Original"),cached.keySet());
        Path shared=store.presets.resolve("圣诞分享.CCMAP");
        MapLayoutCodec.write(shared,new MapLayoutCodec.Layout("Shared","",null,Map.of(ZERO,"minecraft:snow_block"),Map.of()));
        assertFalse(cached.containsKey("Shared"));var refreshed=store.loadPresets();assertEquals(Set.of("Original","Shared"),refreshed.keySet());
        assertEquals("minecraft:snow_block",refreshed.get("Shared").edits().getFirst().material());
        MapLayoutCodec.write(shared,new MapLayoutCodec.Layout("Shared","",null,Map.of(ZERO,"minecraft:gold_block"),Map.of()));
        Files.delete(MapLayoutCodec.path(store.presets,"Original"));refreshed=store.loadPresets();
        assertEquals(Set.of("Shared"),refreshed.keySet());assertEquals("minecraft:gold_block",refreshed.get("Shared").edits().getFirst().material());
        Files.delete(shared);assertTrue(store.loadPresets().isEmpty());
    }
    @Test void scanReportsBadFilenameAndIgnoresNonFiles()throws Exception{
        var store=new MapStorage(temp);store.initialize();Files.createDirectory(store.presets.resolve("folder.ccmap"));
        store.savePreset(new MapEdits.Preset("Good",List.of(new MapEdits.Edit(ZERO,"minecraft:stone",false)),""));
        assertEquals(Set.of("Good"),store.loadPresets().keySet());
        Path broken=store.presets.resolve("broken.ccmap");Files.writeString(broken,"not a layout");
        assertTrue(assertThrows(IOException.class,store::loadPresets).getMessage().contains("broken.ccmap"));
        Files.delete(broken);assertEquals(Set.of("Good"),store.loadPresets().keySet());
    }
    @TempDir Path temp;
    private static final MapEdits.Position ZERO=new MapEdits.Position(0,0,0);
    private static RoomLayouts.Frame frame(String name){return new RoomLayouts.Frame(name,name,ZERO,0);}

    @Test void codecPreservesExtremeCoordinatesPaletteOriginsFingerprintAndAnchor()throws Exception{
        var edits=new HashMap<MapEdits.Position,String>();var flags=new HashMap<MapEdits.Position,Boolean>();
        var random=new Random(42);
        for(int i=0;i<8000;i++){
            var p=new MapEdits.Position(random.nextInt(),random.nextInt(),random.nextInt());edits.put(p,i%2==0?"minecraft:chest[facing=north,type=single,waterlogged=false]":"minecraft:water[level=0]");
            if(i%3!=0)flags.put(p,i%3==1);
        }
        edits.put(new MapEdits.Position(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE),"minecraft:air");
        edits.put(new MapEdits.Position(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE),"minecraft:air");
        var anchor=new RoomLayouts.LegacyAnchor(123,456,3);var file=temp.resolve("test.ccmap");
        MapLayoutCodec.write(file,new MapLayoutCodec.Layout("room:Example","fingerprint",anchor,edits,flags));var reopened=MapLayoutCodec.read(file);
        assertEquals(edits,reopened.edits());assertEquals(flags,reopened.origins());assertEquals(anchor,reopened.anchor());assertEquals("fingerprint",reopened.fingerprint());
        assertThrows(UnsupportedOperationException.class,()->reopened.edits().clear());
    }
    @Test void compressedMillionBlockFixtureRoundTripsAndIsFarSmallerThanJson()throws Exception{
        int count=1_000_000;var edits=new HashMap<MapEdits.Position,String>();
        for(int i=0;i<count;i++)edits.put(new MapEdits.Position(i/10000,i/100%100,i%100),i%17==0?"minecraft:gold_block":"minecraft:dark_oak_planks");
        var file=temp.resolve("million.ccmap");long start=System.nanoTime();MapLayoutCodec.write(file,new MapLayoutCodec.Layout("room:Million","",null,edits,Map.of()));
        var restored=MapLayoutCodec.read(file);assertEquals(edits,restored.edits());
        assertTrue(Files.size(file)<count,"Palette/run/gzip fixture should need less than one byte per block");
        System.out.printf(Locale.ROOT,"Storage fixture: %,d blocks, %,d bytes, %.2fs save + reload%n",count,Files.size(file),(System.nanoTime()-start)/1e9);
    }
    @Test void corruptTruncatedAndFutureDataAreRejectedAndFailedWriteKeepsOriginal()throws Exception{
        var file=temp.resolve("test.ccmap");var good=new MapLayoutCodec.Layout("safe","",null,Map.of(ZERO,"minecraft:stone"),Map.of());MapLayoutCodec.write(file,good);byte[] original=Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class,()->MapLayoutCodec.write(file,new MapLayoutCodec.Layout("bad","",null,Map.of(ZERO,"../invalid"),Map.of())));assertArrayEquals(original,Files.readAllBytes(file));
        Files.write(file,Arrays.copyOf(original,original.length-5));assertThrows(IOException.class,()->MapLayoutCodec.read(file));
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))){out.writeInt(0x43434D50);out.writeInt(2);}assertThrows(IOException.class,()->MapLayoutCodec.read(file));
        Files.write(file,original);byte[] corrupt=original.clone();corrupt[corrupt.length-8]^=1;Files.write(file,corrupt);assertThrows(IOException.class,()->MapLayoutCodec.read(file));
    }
    @Test void mixedMaterialsCompressionComparedWithActualLegacyJson()throws Exception{
        int count=200_000;var random=new Random(73);String[] palette={"minecraft:stone_bricks","minecraft:dark_oak_planks","minecraft:quartz_block","minecraft:glass","minecraft:gold_block","minecraft:air","minecraft:water[level=0]","minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"};
        var edits=new HashMap<MapEdits.Position,String>();var flags=new HashMap<MapEdits.Position,Boolean>();
        for(int i=0;i<count;i++){var p=new MapEdits.Position(i/40000-3,i/200%200-70,i%200-100);edits.put(p,palette[random.nextInt(palette.length)]);if(i%5!=0)flags.put(p,i%7==0);}
        Path json=temp.resolve("legacy.json"),compact=temp.resolve("mixed.ccmap");
        try(var writer=new com.google.gson.stream.JsonWriter(Files.newBufferedWriter(json))){writer.setIndent("  ");writer.beginObject().name("version").value(1).name("rooms").beginObject().name("room:Mixed").beginArray();for(var e:edits.entrySet()){var p=e.getKey();writer.beginObject().name("x").value(p.x()).name("y").value(p.y()).name("z").value(p.z()).name("material").value(e.getValue());if(flags.containsKey(p))writer.name("originallyAir").value(flags.get(p));writer.endObject();}writer.endArray().endObject().endObject();}
        MapLayoutCodec.write(compact,new MapLayoutCodec.Layout("room:Mixed","",null,edits,flags));var restored=MapLayoutCodec.read(compact);assertEquals(edits,restored.edits());assertEquals(flags,restored.origins());assertTrue(Files.size(compact)<Files.size(json)/10);
        System.out.printf(Locale.ROOT,"Mixed storage fixture: %,d blocks, JSON %,d bytes -> ccmap %,d bytes (%.2f%%)%n",count,Files.size(json),Files.size(compact),Files.size(compact)*100.0/Files.size(json));
    }
    @Test void directoriesRewriteOnlyChangedRoomAndPreserveUnknownAndFalseOrigins()throws Exception{
        Path directory=temp.resolve("rooms");var store=new RoomLayouts();store.change(frame("room:A"),ZERO,"minecraft:stone");store.change(frame("room:B"),ZERO,"minecraft:glass");store.rememberOrigin("room:A",ZERO,false);store.save(directory);
        Path unchanged=MapLayoutCodec.path(directory,"room:B");var stamp=java.nio.file.attribute.FileTime.fromMillis(1000);Files.setLastModifiedTime(unchanged,stamp);
        store.change(frame("room:A"),new MapEdits.Position(1,0,0),"minecraft:water");store.rememberOrigin("room:A",new MapEdits.Position(1,0,0),true);store.save(directory);assertEquals(stamp,Files.getLastModifiedTime(unchanged));
        var reopened=new RoomLayouts();reopened.load(directory);assertEquals(store.edits("room:A"),reopened.edits("room:A"));assertEquals(false,reopened.originallyAir("room:A",ZERO));assertEquals(true,reopened.originallyAir("room:A",new MapEdits.Position(1,0,0)));assertNull(reopened.originallyAir("room:B",ZERO));
        store.clear("room:A");store.save(directory);assertFalse(Files.exists(MapLayoutCodec.path(directory,"room:A")));reopened.load(directory);assertEquals(1,reopened.count());
        Files.writeString(directory.resolve("corrupt.ccmap"),"broken");assertThrows(IOException.class,()->reopened.load(directory));assertEquals("minecraft:glass",reopened.edits("room:B").get(ZERO));
    }
    @Test void migrationSeparatesSettingsPresetsRoomsAndArchivesExactOriginals()throws Exception{
        var preset=new MapEdits.Preset("My room",List.of(new MapEdits.Edit(ZERO,"minecraft:glass",true),new MapEdits.Edit(new MapEdits.Position(-5,70,3),"minecraft:dark_oak_planks",false)),"sample-fingerprint");
        var oldSettings=new JsonObject();oldSettings.addProperty("enabled",true);oldSettings.add("presets",new Gson().toJsonTree(List.of(preset)));oldSettings.add("favorites",new Gson().toJsonTree(List.of("minecraft:chest")));
        String settingsJson=new GsonBuilder().setPrettyPrinting().create().toJson(oldSettings);
        String roomsJson="{\"version\":1,\"rooms\":{\"floor:F7\":[{\"x\":1,\"y\":70,\"z\":2,\"material\":\"minecraft:stone\",\"originallyAir\":false}],\"floor:M7\":[{\"x\":3,\"y\":70,\"z\":4,\"material\":\"minecraft:gold_block\",\"originallyAir\":true}],\"room:A\":[{\"x\":0,\"y\":0,\"z\":0,\"material\":\"minecraft:water\"}]},\"anchors\":{\"room:A\":{\"baseCore\":1,\"rotationCore\":2,\"distance\":3}}}";
        Files.writeString(temp.resolve("map-editor.json"),settingsJson);Files.writeString(temp.resolve("map-rooms.json"),roomsJson);
        var store=new MapStorage(temp);var settings=store.initialize();assertTrue(settings.get("enabled").getAsBoolean());assertFalse(settings.has("presets"));assertFalse(Files.exists(temp.resolve("map-editor.json")));assertFalse(Files.exists(temp.resolve("map-rooms.json")));
        var loaded=store.loadPresets().get("My room");assertEquals(preset.fingerprint(),loaded.fingerprint());assertEquals(preset.place(ZERO,0),loaded.place(ZERO,0));
        var rooms=new RoomLayouts();rooms.load(store.rooms);assertEquals(2,rooms.edits("floor:F7+M7").size());assertEquals(false,rooms.originallyAir("floor:M7",new MapEdits.Position(1,70,2)));assertEquals(true,rooms.originallyAir("floor:F7",new MapEdits.Position(3,70,4)));assertNull(rooms.originallyAir("room:A",ZERO));assertEquals(new RoomLayouts.LegacyAnchor(1,2,3),rooms.anchor("room:A"));
        try(var files=Files.list(store.root.resolve("backups"))){Path backup=files.findFirst().orElseThrow();assertEquals(settingsJson,Files.readString(backup.resolve("map-editor.json")));assertEquals(roomsJson,Files.readString(backup.resolve("map-rooms.json")));}
        Path presetFile=MapLayoutCodec.path(store.presets,"My room");byte[] before=Files.readAllBytes(presetFile);settings.addProperty("enabled",false);store.saveSettings(settings);assertArrayEquals(before,Files.readAllBytes(presetFile));assertFalse(Files.readString(store.settings).contains("presets"));
        assertFalse(store.initialize().get("enabled").getAsBoolean());store.deletePreset("My room");assertTrue(store.loadPresets().isEmpty());
    }
    @Test void failedMigrationKeepsOriginalsAndRetryPreservesNewerData()throws Exception{
        String original="{\"enabled\":true,\"presets\":[{\"name\":\"Saved\",\"edits\":[],\"fingerprint\":\"old\"}]}";
        Files.writeString(temp.resolve("map-editor.json"),original);Files.writeString(temp.resolve("map-rooms.json"),"{broken");var store=new MapStorage(temp);
        assertThrows(IOException.class,store::initialize);assertEquals(original,Files.readString(temp.resolve("map-editor.json")));assertFalse(Files.exists(store.root.resolve("backups")));
        store.savePreset(new MapEdits.Preset("Saved",List.of(new MapEdits.Edit(ZERO,"minecraft:gold_block",false)),"new"));var settings=new JsonObject();settings.addProperty("enabled",false);store.saveSettings(settings);
        Files.writeString(temp.resolve("map-rooms.json"),"{\"version\":1,\"rooms\":{}}");assertFalse(store.initialize().get("enabled").getAsBoolean());assertEquals("new",store.loadPresets().get("Saved").fingerprint());
    }
    @Test void importedRoomFilesHaveBackupsAndKeepRotatedAnchorsAfterClearing()throws Exception{
        var rooms=new RoomLayouts();Path directory=temp.resolve("rooms"),backup=temp.resolve("backup");rooms.change(frame("room:A"),ZERO,"minecraft:stone");rooms.save(directory);byte[] before=Files.readAllBytes(MapLayoutCodec.path(directory,"room:A"));
        var anchor=new RoomLayouts.LegacyAnchor(12,34,2);rooms.importTo(directory,backup,Map.of("room:A",new FmeImport.Layout(Map.of(ZERO,"minecraft:gold_block"),anchor)),true);
        assertArrayEquals(before,Files.readAllBytes(backup.resolve(MapLayoutCodec.path(directory,"room:A").getFileName())));assertEquals("minecraft:gold_block",rooms.edits("room:A").get(ZERO));rooms.clear("room:A");rooms.save(directory);
        var reopened=new RoomLayouts();reopened.load(directory);assertEquals(anchor,reopened.anchor("room:A"));assertTrue(reopened.edits("room:A").isEmpty());
    }
    @Test void stableFileNamesCannotEscapeStorageOrUseWindowsReservedNames(){
        for(String name:List.of("../../escape","CON","NUL","room:A/B","..","My room")){Path path=MapLayoutCodec.path(temp,name);assertEquals(temp,path.getParent());assertTrue(path.getFileName().toString().startsWith("layout-"));}
        assertNotEquals(MapLayoutCodec.path(temp,"A:B"),MapLayoutCodec.path(temp,"A/B"));
    }
}
