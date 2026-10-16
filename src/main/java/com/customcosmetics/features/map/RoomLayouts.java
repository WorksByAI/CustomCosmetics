// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.google.gson.*;
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
    private static final int MAX_ROOMS=512,MAX_TOTAL=65536,MAX_BYTES=8*1024*1024;
    private final Map<String,Map<MapEdits.Position,String>> rooms=new TreeMap<>();
    private final Map<String,LegacyAnchor> anchors=new TreeMap<>();
    private final Map<String,Map<MapEdits.Position,Boolean>> origins=new TreeMap<>();
    private boolean dirty;
    RoomLayouts copy(){var copy=new RoomLayouts();copy.rooms.putAll(rooms);copy.anchors.putAll(anchors);copy.origins.putAll(origins);return copy;}
    public static String canonicalKey(String key){return key.equals("floor:F7")||key.equals("floor:M7")?"floor:F7+M7":key;}
    public Map<MapEdits.Position,String> edits(String key){return Map.copyOf(rooms.getOrDefault(canonicalKey(key),Map.of()));}
    public int count(){return rooms.size();}
    public LegacyAnchor anchor(String key){return anchors.get(key);}
    public Map<String,LegacyAnchor> anchors(){return Map.copyOf(anchors);}
    public void clear(String key){key=canonicalKey(key);if(rooms.remove(key)!=null)dirty=true;origins.remove(key);}
    public Boolean originallyAir(String key,MapEdits.Position relative){return origins.getOrDefault(canonicalKey(key),Map.of()).get(relative);}
    public void rememberOrigin(String key,MapEdits.Position relative,boolean air){key=canonicalKey(key);if(!rooms.getOrDefault(key,Map.of()).containsKey(relative))return;var values=new HashMap<>(origins.getOrDefault(key,Map.of()));if(!Objects.equals(values.put(relative,air),air)){origins.put(key,Map.copyOf(values));dirty=true;}}
    /** Shared boss geometry; existing shared data wins, then F7, then M7-only coordinates. */
    public static Map<String,FmeImport.Layout> sharedBossLayouts(Map<String,FmeImport.Layout> source){
        var result=new TreeMap<>(source);var merged=new LinkedHashMap<MapEdits.Position,String>();boolean found=false;
        for(String key:List.of("floor:M7","floor:F7","floor:F7+M7")){var layout=result.remove(key);if(layout!=null){found=true;merged.putAll(layout.edits());}}
        if(merged.size()>MapEdits.LIMIT)throw new IllegalArgumentException("Shared F7/M7 boss edit limit reached");
        if(found)result.put("floor:F7+M7",new FmeImport.Layout(merged,null));return Map.copyOf(result);
    }
    public void importLayouts(Map<String,FmeImport.Layout> imported,boolean replace){
        var next=new TreeMap<>(rooms);var nextAnchors=new TreeMap<>(anchors);
        var resetOrigins=new HashSet<String>();for(var entry:sharedBossLayouts(imported).entrySet()){if(!replace&&next.containsKey(entry.getKey()))continue;resetOrigins.add(entry.getKey());next.put(entry.getKey(),entry.getValue().edits());if(entry.getValue().anchor()!=null)nextAnchors.put(entry.getKey(),entry.getValue().anchor());else nextAnchors.remove(entry.getKey());}
        if(next.size()>MAX_ROOMS||next.values().stream().mapToInt(Map::size).sum()>MAX_TOTAL)throw new IllegalArgumentException("Imported library exceeds limits");
        rooms.clear();rooms.putAll(next);anchors.clear();anchors.putAll(nextAnchors);resetOrigins.forEach(origins::remove);dirty=true;
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
        if(copy.equals(existing)&&nextOrigins.equals(originalOrigins))return;if(copy.size()>MapEdits.LIMIT)throw new IllegalArgumentException("Room edit limit reached");
        if(!rooms.containsKey(frame.key())&&rooms.size()>=MAX_ROOMS)throw new IllegalArgumentException("Room library is full");
        if(rooms.values().stream().mapToInt(Map::size).sum()-existing.size()+copy.size()>MAX_TOTAL)throw new IllegalArgumentException("Room library block limit reached");
        if(copy.isEmpty()){rooms.remove(frame.key());origins.remove(frame.key());}else{rooms.put(frame.key(),Map.copyOf(copy));origins.put(frame.key(),Map.copyOf(nextOrigins));}dirty=true;
    }
    public void load(Path path)throws IOException{
        if(!Files.isRegularFile(path))return;if(Files.size(path)>MAX_BYTES)throw new IOException("Room library exceeds 8 MiB");
        var next=new TreeMap<String,Map<MapEdits.Position,String>>();var nextAnchors=new TreeMap<String,LegacyAnchor>();var nextOrigins=new TreeMap<String,Map<MapEdits.Position,Boolean>>();int total=0;boolean migrated=false;
        try{
            var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unknown room library version");
            migrated=root.getAsJsonObject("rooms").has("floor:F7")||root.getAsJsonObject("rooms").has("floor:M7");
            for(var item:root.getAsJsonObject("rooms").entrySet()){
                if(item.getKey().length()>160||next.size()>=MAX_ROOMS)throw new IllegalArgumentException("Invalid room library");
                var edits=new LinkedHashMap<MapEdits.Position,String>();var flags=new HashMap<MapEdits.Position,Boolean>();for(var row:item.getValue().getAsJsonArray()){
                    var e=row.getAsJsonObject();var p=new MapEdits.Position(e.get("x").getAsInt(),e.get("y").getAsInt(),e.get("z").getAsInt());var material=e.get("material").getAsString();new MapEdits.Edit(p,material,false);
                    if(edits.put(p,material)!=null||edits.size()>MapEdits.LIMIT||++total>MAX_TOTAL)throw new IllegalArgumentException("Too many or duplicate room edits");
                    if(e.has("originallyAir")){if(!e.get("originallyAir").isJsonPrimitive()||!e.getAsJsonPrimitive("originallyAir").isBoolean())throw new IllegalArgumentException("Invalid edit origin");flags.put(p,e.get("originallyAir").getAsBoolean());}
                }next.put(item.getKey(),Map.copyOf(edits));nextOrigins.put(item.getKey(),Map.copyOf(flags));
            }
            if(root.has("anchors"))for(var e:root.getAsJsonObject("anchors").entrySet()){if(!next.containsKey(e.getKey()))continue;var a=e.getValue().getAsJsonObject();nextAnchors.put(e.getKey(),new LegacyAnchor(a.get("baseCore").getAsInt(),a.get("rotationCore").getAsInt(),a.get("distance").getAsInt()));}
            var sharedOrigins=new HashMap<MapEdits.Position,Boolean>();for(String key:List.of("floor:M7","floor:F7","floor:F7+M7"))for(var p:next.getOrDefault(key,Map.of()).keySet()){sharedOrigins.remove(p);var value=nextOrigins.getOrDefault(key,Map.of()).get(p);if(value!=null)sharedOrigins.put(p,value);}nextOrigins.remove("floor:M7");nextOrigins.remove("floor:F7");if(!sharedOrigins.isEmpty())nextOrigins.put("floor:F7+M7",Map.copyOf(sharedOrigins));
            var layouts=new TreeMap<String,FmeImport.Layout>();next.forEach((key,edits)->layouts.put(key,new FmeImport.Layout(edits,nextAnchors.get(key))));var shared=sharedBossLayouts(layouts);next.clear();nextAnchors.clear();shared.forEach((key,layout)->{next.put(key,layout.edits());if(layout.anchor()!=null)nextAnchors.put(key,layout.anchor());});
        }catch(RuntimeException error){throw new IOException("Invalid room library",error);}
        // Keep an exact, recoverable copy before a legacy per-floor library is rewritten.
        if(migrated)Files.copy(path,path.resolveSibling(path.getFileName()+".before-shared-f7-m7-"+UUID.randomUUID()+".bak"));
        rooms.clear();rooms.putAll(next);anchors.clear();anchors.putAll(nextAnchors);origins.clear();origins.putAll(nextOrigins);dirty=migrated;
    }
    public void save(Path path)throws IOException{
        if(!dirty)return;var root=new JsonObject();root.addProperty("version",1);var list=new JsonObject();root.add("rooms",list);
        rooms.forEach((name,edits)->{var rows=new JsonArray();edits.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<MapEdits.Position,String> e)->e.getKey().x()).thenComparingInt(e->e.getKey().y()).thenComparingInt(e->e.getKey().z())).forEach(e->{var row=new JsonObject();row.addProperty("x",e.getKey().x());row.addProperty("y",e.getKey().y());row.addProperty("z",e.getKey().z());row.addProperty("material",e.getValue());Boolean air=originallyAir(name,e.getKey());if(air!=null)row.addProperty("originallyAir",air);rows.add(row);});list.add(name,rows);});
        root.add("anchors",new Gson().toJsonTree(anchors));String json=new GsonBuilder().setPrettyPrinting().create().toJson(root);if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Room library exceeds 8 MiB");
        Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,json);
        try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}dirty=false;
    }
}
