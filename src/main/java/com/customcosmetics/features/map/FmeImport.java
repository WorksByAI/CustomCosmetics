// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Read-only parser for the legacy Extras JSON format; parsing never changes the live configuration. */
public final class FmeImport {
    public record Layout(Map<MapEdits.Position,String> edits,RoomLayouts.LegacyAnchor anchor){public Layout{edits=MapEdits.immutable(edits);}}
    public record Plan(Map<String,Layout> layouts,Set<String> favorites,List<String> warnings,int skipped){
        public Plan{layouts=Map.copyOf(layouts);favorites=Set.copyOf(favorites);warnings=List.copyOf(warnings);}
        public int blocks(){return layouts.values().stream().mapToInt(l->l.edits.size()).sum();}
    }
    private final Map<String,Layout> layouts=new LinkedHashMap<>();private final Set<String> favorites=new LinkedHashSet<>();private final List<String> warnings=new ArrayList<>();private int skipped;
    private FmeImport(){}
    public static Plan read(Path selected,Function<String,String> material,Function<String,String> favorite)throws IOException{
        var parser=new FmeImport();Path folder=Files.isDirectory(selected)?selected:selected.toAbsolutePath().getParent();boolean standard=Files.isDirectory(selected)||Set.of("extrasConfig.json","floorsConfig.json","favBlocks.json").contains(selected.getFileName().toString());
        try{
            if(standard){boolean found=false;for(String name:List.of("extrasConfig.json","floorsConfig.json","favBlocks.json")){Path path=folder.resolve(name);if(!Files.isRegularFile(path))continue;found=true;parser.parse(name,json(path),material,favorite);}if(!found)throw new IOException("No FME config files found in this folder");}
            else{var json=json(selected);boolean fav=json.isJsonArray();boolean rooms=json.isJsonObject()&&json.getAsJsonObject().entrySet().stream().anyMatch(e->e.getValue().isJsonObject()&&e.getValue().getAsJsonObject().has("preBlocks"));parser.parse(fav?"favBlocks.json":rooms?"extrasConfig.json":"floorsConfig.json",json,material,favorite);}
        }catch(RuntimeException error){throw new IOException("Invalid FME config: "+error.getMessage(),error);}
        Map<String,Layout> shared;
        try{shared=RoomLayouts.sharedBossLayouts(parser.layouts);}catch(RuntimeException error){throw new IOException("Invalid FME config: "+error.getMessage(),error);}
        if(parser.layouts.containsKey("floor:F7")&&parser.layouts.containsKey("floor:M7")){var f7=parser.layouts.get("floor:F7").edits();var m7=parser.layouts.get("floor:M7").edits();if(f7.entrySet().stream().anyMatch(e->m7.containsKey(e.getKey())&&!m7.get(e.getKey()).equals(e.getValue())))parser.warn("F7/M7 boss edits share one layout; F7 wins overlapping legacy coordinates. Source files stay unchanged.");}
        return new Plan(shared,parser.favorites,parser.warnings,parser.skipped);
    }
    private static JsonElement json(Path path)throws IOException{if(Files.size(path)>8*1024*1024)throw new IOException("FME file exceeds 8 MiB");String text=Files.readString(path).strip();return text.isEmpty()?new JsonObject():JsonParser.parseString(text);}
    private void parse(String filename,JsonElement json,Function<String,String> material,Function<String,String> favorite){
        if(filename.equals("favBlocks.json")){if(json.isJsonObject()&&json.getAsJsonObject().isEmpty())return;for(var entry:json.getAsJsonArray()){if(favorites.size()>=256)throw new IllegalArgumentException("Too many favorites");try{favorites.add(favorite.apply(entry.getAsString()));}catch(RuntimeException error){skipped++;warn("Favorite: "+entry);}}return;}
        for(var entry:json.getAsJsonObject().entrySet()){
            if(layouts.size()>=512||entry.getKey().length()>100)throw new IllegalArgumentException("Too many or invalid room names");var room=entry.getValue().getAsJsonObject();RoomLayouts.LegacyAnchor anchor=null;JsonObject blocks=room;
            if(filename.equals("extrasConfig.json")){blocks=room.getAsJsonObject("preBlocks");if(blocks==null||blocks.isEmpty())continue;anchor=new RoomLayouts.LegacyAnchor(room.get("baseCore").getAsInt(),room.get("rotationCore").getAsInt(),room.has("coreDistance")?room.get("coreDistance").getAsInt():2);}
            var edits=new LinkedHashMap<MapEdits.Position,String>();for(var block:blocks.entrySet()){
                String converted;try{converted=material.apply(block.getKey());if(converted==null)throw new IllegalArgumentException();new MapEdits.Edit(new MapEdits.Position(0,0,0),converted,false);}catch(RuntimeException error){skipped+=block.getValue().getAsJsonArray().size();warn("Unsupported block: "+block.getKey());continue;}
                for(var point:block.getValue().getAsJsonArray()){var pos=position(point);if(edits.putIfAbsent(pos,converted)!=null)throw new IllegalArgumentException("Duplicate block coordinate in "+entry.getKey());}
            }
            if(edits.isEmpty())continue;if(anchor!=null)layouts.put("room:"+entry.getKey(),new Layout(edits,anchor));else{
                String floor=entry.getKey().toUpperCase(Locale.ROOT);if(floor.equals("0"))layouts.put("floor:E",new Layout(edits,null));else if(floor.matches("[1-7]")){layouts.put("floor:F"+floor,new Layout(edits,null));layouts.put("floor:M"+floor,new Layout(edits,null));}else if(floor.matches("[FM][1-7]|E"))layouts.put("floor:"+floor,new Layout(edits,null));else throw new IllegalArgumentException("Invalid floor: "+floor);
            }
        }
    }
    private void warn(String message){if(warnings.size()<12)warnings.add(message);}
    private static MapEdits.Position position(JsonElement point){int x,y,z;if(point.isJsonObject()){var p=point.getAsJsonObject();x=p.get("x").getAsInt();y=p.get("y").getAsInt();z=p.get("z").getAsInt();}else{String[] p=point.getAsString().split("\\s*,\\s*",-1);if(p.length!=3)throw new IllegalArgumentException("Invalid coordinate");x=Integer.parseInt(p[0].strip());y=Integer.parseInt(p[1]);z=Integer.parseInt(p[2].strip());}if(Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000||Math.abs((long)y)>2048)throw new IllegalArgumentException("Coordinate outside world limits");return new MapEdits.Position(x,y,z);}
}
