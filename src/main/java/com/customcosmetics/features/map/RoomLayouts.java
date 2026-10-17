// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Room edits use canonical coordinates, independent of a dungeon instance's layout. */
public final class RoomLayouts {
    public record LegacyAnchor(int baseCore,int rotationCore,int distance){public LegacyAnchor{if(distance<1||distance>32)throw new IllegalArgumentException("Invalid legacy rotation distance");}}
    public record Frame(String key,String label,MapEdits.Position origin,int turns) {
        public Frame{key=canonicalKey(key);}
        public MapEdits.Position relative(MapEdits.Position world){return world.subtract(origin).rotate(-turns);}
        public MapEdits.Position world(MapEdits.Position relative){return relative.rotate(turns).add(origin);}
    }
    private static final int MAX_ROOMS=512;
    private final Map<String,Map<MapEdits.Position,String>> rooms=new TreeMap<>();
    private final Map<String,LegacyAnchor> anchors=new TreeMap<>();
    private final Map<String,Map<MapEdits.Position,Boolean>> origins=new TreeMap<>();
    private boolean dirty;
    private final Set<String> dirtyRooms=new HashSet<>();
    RoomLayouts copy(){var copy=new RoomLayouts();copy.rooms.putAll(rooms);copy.anchors.putAll(anchors);copy.origins.putAll(origins);copy.dirty=dirty;copy.dirtyRooms.addAll(dirtyRooms);return copy;}
    void adopt(RoomLayouts candidate){rooms.clear();rooms.putAll(candidate.rooms);anchors.clear();anchors.putAll(candidate.anchors);origins.clear();origins.putAll(candidate.origins);dirty|=candidate.dirty;dirtyRooms.addAll(candidate.dirtyRooms);}
    public static String canonicalKey(String key){return key.equals("floor:F7")||key.equals("floor:M7")?"floor:F7+M7":key;}
    public Map<MapEdits.Position,String> edits(String key){return rooms.getOrDefault(canonicalKey(key),Map.of());}
    public int count(){return rooms.size();}
    public LegacyAnchor anchor(String key){return anchors.get(key);}
    public Map<String,LegacyAnchor> anchors(){return Map.copyOf(anchors);}
    public void clear(String key){key=canonicalKey(key);if(rooms.remove(key)!=null){dirty=true;dirtyRooms.add(key);}origins.remove(key);}
    public Boolean originallyAir(String key,MapEdits.Position relative){return origins.getOrDefault(canonicalKey(key),Map.of()).get(relative);}
    public void rememberOrigin(String key,MapEdits.Position relative,boolean air){rememberOrigins(key,Map.of(relative,air));}
    public void rememberOrigins(String key,Map<MapEdits.Position,Boolean> additions){key=canonicalKey(key);var edits=rooms.getOrDefault(key,Map.of());var existing=origins.getOrDefault(key,Map.of());var values=new HashMap<>(existing);additions.forEach((p,air)->{if(edits.containsKey(p))values.put(p,air);});if(!values.equals(existing)){origins.put(key,MapEdits.immutable(values));dirty=true;dirtyRooms.add(key);}}
    /** Shared boss geometry; existing shared data wins, then F7, then M7-only coordinates. */
    public static Map<String,FmeImport.Layout> sharedBossLayouts(Map<String,FmeImport.Layout> source){
        var result=new TreeMap<>(source);var merged=new LinkedHashMap<MapEdits.Position,String>();boolean found=false;
        for(String key:List.of("floor:M7","floor:F7","floor:F7+M7")){var layout=result.remove(key);if(layout!=null){found=true;merged.putAll(layout.edits());}}
        if(found)result.put("floor:F7+M7",new FmeImport.Layout(merged,null));return Map.copyOf(result);
    }
    public void importLayouts(Map<String,FmeImport.Layout> imported,boolean replace){
        var next=new TreeMap<>(rooms);var nextAnchors=new TreeMap<>(anchors);
        var resetOrigins=new HashSet<String>();for(var entry:sharedBossLayouts(imported).entrySet()){if(!replace&&next.containsKey(entry.getKey()))continue;resetOrigins.add(entry.getKey());next.put(entry.getKey(),entry.getValue().edits());if(entry.getValue().anchor()!=null)nextAnchors.put(entry.getKey(),entry.getValue().anchor());else nextAnchors.remove(entry.getKey());}
        if(next.size()>MAX_ROOMS)throw new IllegalArgumentException("Room library is full");
        rooms.clear();rooms.putAll(next);anchors.clear();anchors.putAll(nextAnchors);resetOrigins.forEach(origins::remove);dirty=true;dirtyRooms.addAll(resetOrigins);
    }
    public void change(Frame frame,MapEdits.Position world,String material){
        changeAll(frame,Map.of(world,Optional.ofNullable(material)));
    }
    public void changeAll(Frame frame,Map<MapEdits.Position,Optional<String>> changes){
        changeAll(frame,changes,Map.of());
    }
    public void changeAll(Frame frame,Map<MapEdits.Position,Optional<String>> changes,Map<MapEdits.Position,Boolean> sourceOrigins){
        if(frame==null)return;
        var existing=rooms.getOrDefault(frame.key(),Map.of());var copy=new LinkedHashMap<>(existing);
        var originalOrigins=origins.getOrDefault(frame.key(),Map.of());var nextOrigins=new HashMap<>(originalOrigins);
        for(var entry:changes.entrySet()){var relative=frame.relative(entry.getKey());String material=entry.getValue().orElse(null);if(material==null){copy.remove(relative);nextOrigins.remove(relative);}else{new MapEdits.Edit(relative,material,false);copy.put(relative,material);if(sourceOrigins.containsKey(entry.getKey()))nextOrigins.put(relative,sourceOrigins.get(entry.getKey()));}}
        if(copy.equals(existing)&&nextOrigins.equals(originalOrigins))return;
        if(!rooms.containsKey(frame.key())&&rooms.size()>=MAX_ROOMS)throw new IllegalArgumentException("Room library is full");
        if(copy.isEmpty()){rooms.remove(frame.key());origins.remove(frame.key());}else{rooms.put(frame.key(),MapEdits.immutable(copy));origins.put(frame.key(),MapEdits.immutable(nextOrigins));}dirty=true;dirtyRooms.add(frame.key());
    }
    public void load(Path path)throws IOException{
        if(Files.isDirectory(path)){loadDirectory(path);return;}
        loadLegacy(path,true);
    }
    void loadLegacy(Path path,boolean backup)throws IOException{
        if(!Files.isRegularFile(path))return;
        var next=new TreeMap<String,Map<MapEdits.Position,String>>();var nextAnchors=new TreeMap<String,LegacyAnchor>();var nextOrigins=new TreeMap<String,Map<MapEdits.Position,Boolean>>();boolean migrated=false;
        try(var reader=new com.google.gson.stream.JsonReader(Files.newBufferedReader(path))){
            int version=-1;boolean foundRooms=false;var materialPool=new HashMap<String,String>();
            reader.beginObject();while(reader.hasNext()){
                switch(reader.nextName()){
                    case "version" -> version=reader.nextInt();
                    case "rooms" -> {
                        if(foundRooms)throw new IllegalArgumentException("Duplicate room library");foundRooms=true;
                        reader.beginObject();while(reader.hasNext()){
                            String key=reader.nextName();if(key.length()>160||next.size()>=MAX_ROOMS||next.containsKey(key))throw new IllegalArgumentException("Invalid room library");
                            migrated|=key.equals("floor:F7")||key.equals("floor:M7");
                            var edits=new HashMap<MapEdits.Position,String>();var flags=new HashMap<MapEdits.Position,Boolean>();reader.beginArray();
                            while(reader.hasNext()){
                                var e=JsonParser.parseReader(reader).getAsJsonObject();var p=new MapEdits.Position(e.get("x").getAsInt(),e.get("y").getAsInt(),e.get("z").getAsInt());
                                String material=e.get("material").getAsString();material=materialPool.computeIfAbsent(material,id->{new MapEdits.Edit(p,id,false);return id;});
                                if(edits.put(p,material)!=null)throw new IllegalArgumentException("Duplicate room edit");
                                if(e.has("originallyAir")){if(!e.get("originallyAir").isJsonPrimitive()||!e.getAsJsonPrimitive("originallyAir").isBoolean())throw new IllegalArgumentException("Invalid edit origin");flags.put(p,e.get("originallyAir").getAsBoolean());}
                            }
                            reader.endArray();next.put(key,MapEdits.immutable(edits));nextOrigins.put(key,MapEdits.immutable(flags));
                        }reader.endObject();
                    }
                    case "anchors" -> {
                        var value=JsonParser.parseReader(reader).getAsJsonObject();for(var e:value.entrySet()){
                            var a=e.getValue().getAsJsonObject();nextAnchors.put(e.getKey(),new LegacyAnchor(a.get("baseCore").getAsInt(),a.get("rotationCore").getAsInt(),a.get("distance").getAsInt()));
                        }
                    }
                    default -> reader.skipValue();
                }
            }reader.endObject();if(version!=1||!foundRooms||reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Unknown room library version");
            nextAnchors.keySet().retainAll(next.keySet());
            var sharedOrigins=new HashMap<MapEdits.Position,Boolean>();for(String key:List.of("floor:M7","floor:F7","floor:F7+M7"))for(var p:next.getOrDefault(key,Map.of()).keySet()){sharedOrigins.remove(p);var value=nextOrigins.getOrDefault(key,Map.of()).get(p);if(value!=null)sharedOrigins.put(p,value);}nextOrigins.remove("floor:M7");nextOrigins.remove("floor:F7");if(!sharedOrigins.isEmpty())nextOrigins.put("floor:F7+M7",MapEdits.immutable(sharedOrigins));
            var layouts=new TreeMap<String,FmeImport.Layout>();next.forEach((key,edits)->layouts.put(key,new FmeImport.Layout(edits,nextAnchors.get(key))));var shared=sharedBossLayouts(layouts);next.clear();nextAnchors.clear();shared.forEach((key,layout)->{next.put(key,layout.edits());if(layout.anchor()!=null)nextAnchors.put(key,layout.anchor());});
        }catch(RuntimeException error){throw new IOException("Invalid room library",error);}
        // Keep an exact, recoverable copy before a legacy per-floor library is rewritten.
        if(migrated&&backup)Files.copy(path,path.resolveSibling(path.getFileName()+".before-shared-f7-m7-"+UUID.randomUUID()+".bak"));
        rooms.clear();rooms.putAll(next);anchors.clear();anchors.putAll(nextAnchors);origins.clear();origins.putAll(nextOrigins);dirty=migrated;dirtyRooms.clear();if(migrated)dirtyRooms.addAll(next.keySet());
    }
    public void save(Path path)throws IOException{
        if(!dirty)return;
        if(!path.getFileName().toString().endsWith(".json")){saveDirectory(path);return;}
        Files.createDirectories(path.toAbsolutePath().getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");
        // Stream the local library: a large room must not build a second JSON tree and giant String.
        try(var writer=new JsonWriter(Files.newBufferedWriter(temp))){
            writer.setIndent("  ");writer.beginObject().name("version").value(1).name("rooms").beginObject();
            for(var room:rooms.entrySet()){
                writer.name(room.getKey()).beginArray();
                var rows=room.getValue().entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<MapEdits.Position,String> e)->e.getKey().x()).thenComparingInt(e->e.getKey().y()).thenComparingInt(e->e.getKey().z())).toList();
                for(var e:rows){var p=e.getKey();writer.beginObject().name("x").value(p.x()).name("y").value(p.y()).name("z").value(p.z()).name("material").value(e.getValue());Boolean air=originallyAir(room.getKey(),p);if(air!=null)writer.name("originallyAir").value(air);writer.endObject();}
                writer.endArray();
            }
            writer.endObject().name("anchors");new Gson().toJson(anchors,anchors.getClass(),writer);writer.endObject();
        }
        try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}dirty=false;dirtyRooms.clear();
    }
    private void loadDirectory(Path path)throws IOException{
        var candidate=new RoomLayouts();var seen=new HashSet<String>();
        try(var files=Files.list(path)){
            for(Path source:files.filter(p->p.getFileName().toString().endsWith(".ccmap")).sorted().toList()){
                var layout=MapLayoutCodec.read(source);String key=canonicalKey(layout.name());
                if(!key.equals(layout.name())||!seen.add(key)||seen.size()>MAX_ROOMS)throw new IOException("Invalid or duplicate room layout: "+key);
                if(!layout.edits().isEmpty())candidate.rooms.put(key,layout.edits());
                if(!layout.origins().isEmpty())candidate.origins.put(key,layout.origins());
                if(layout.anchor()!=null)candidate.anchors.put(key,layout.anchor());
            }
        }
        rooms.clear();anchors.clear();origins.clear();adopt(candidate);dirty=false;dirtyRooms.clear();
    }
    void saveAll(Path directory)throws IOException{dirty=true;dirtyRooms.addAll(rooms.keySet());dirtyRooms.addAll(anchors.keySet());saveDirectory(directory);}
    void mergeMissing(RoomLayouts source){
        var keys=new HashSet<>(rooms.keySet());keys.addAll(source.rooms.keySet());if(keys.size()>MAX_ROOMS)throw new IllegalArgumentException("Room library is full");
        for(var entry:source.rooms.entrySet())if(!rooms.containsKey(entry.getKey())){
            String key=entry.getKey();rooms.put(key,entry.getValue());
            if(source.origins.containsKey(key))origins.put(key,source.origins.get(key));
            if(source.anchors.containsKey(key))anchors.put(key,source.anchors.get(key));
            dirty=true;dirtyRooms.add(key);
        }
    }
    private void saveDirectory(Path directory)throws IOException{
        Files.createDirectories(directory);
        for(String key:dirtyRooms){
            var path=MapLayoutCodec.path(directory,key);
            if(!rooms.containsKey(key)&&!anchors.containsKey(key))Files.deleteIfExists(path);
            else MapLayoutCodec.write(path,new MapLayoutCodec.Layout(key,"",anchors.get(key),edits(key),origins.getOrDefault(key,Map.of())));
        }
        dirty=false;dirtyRooms.clear();
    }
    /** Persist an import before changing live state; restore exact prior files if any write fails. */
    void importTo(Path directory,Path backup,Map<String,FmeImport.Layout> imported,boolean replace)throws IOException{
        save(directory);Files.createDirectories(backup);var candidate=copy();candidate.importLayouts(imported,replace);
        var affected=new HashSet<>(candidate.dirtyRooms);var existing=new HashSet<String>();
        for(String key:affected){Path source=MapLayoutCodec.path(directory,key);if(Files.isRegularFile(source)){Files.copy(source,backup.resolve(source.getFileName()));existing.add(key);}}
        try{candidate.save(directory);}catch(IOException failure){
            for(String key:affected)try{Path target=MapLayoutCodec.path(directory,key);if(existing.contains(key))Files.copy(backup.resolve(target.getFileName()),target,StandardCopyOption.REPLACE_EXISTING);else Files.deleteIfExists(target);}catch(IOException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
        adopt(candidate);dirty=false;dirtyRooms.clear();
    }
}
