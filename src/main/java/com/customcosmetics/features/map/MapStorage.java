// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Small settings are independent of compressed presets and room autosaves. */
final class MapStorage {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    final Path root,settings,presets,rooms;
    private final Path base;
    private volatile Map<String,Path> presetFiles=Map.of();
    MapStorage(Path base){this.base=base;root=base.resolve("map-editor");settings=root.resolve("settings.json");presets=root.resolve("presets");rooms=root.resolve("rooms");}

    JsonObject initialize()throws IOException{
        Files.createDirectories(root);Files.createDirectories(presets);Files.createDirectories(rooms);
        Path oldSettings=base.resolve("map-editor.json"),oldRooms=base.resolve("map-rooms.json");
        JsonObject migrated=new JsonObject();
        if(Files.isRegularFile(oldSettings)){
            try(var reader=new JsonReader(Files.newBufferedReader(oldSettings))){
                reader.beginObject();while(reader.hasNext()){
                    String field=reader.nextName();
                    if(field.equals("presets")){
                        reader.beginArray();while(reader.hasNext()){
                            var layout=readLegacyPreset(reader);Path target=MapLayoutCodec.path(presets,layout.name());
                            if(!Files.exists(target))MapLayoutCodec.write(target,layout);
                            else if(!MapLayoutCodec.read(target).name().equals(layout.name()))throw new IOException("Invalid existing preset");
                        }reader.endArray();
                    }else migrated.add(field,JsonParser.parseReader(reader));
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing legacy settings data");
            }catch(RuntimeException error){throw new IOException("Could not migrate map-editor.json; original file retained",error);}
        }
        if(Files.isRegularFile(oldRooms)){
            var legacy=new RoomLayouts();legacy.loadLegacy(oldRooms,false);
            var current=new RoomLayouts();current.load(rooms);current.mergeMissing(legacy);current.save(rooms);
        }
        // Validate existing settings before archiving anything. A failed migration retains both originals.
        JsonObject result=Files.isRegularFile(settings)?loadSettings():migrated;
        if(!Files.exists(settings))saveSettings(result);
        if(Files.isRegularFile(oldSettings)||Files.isRegularFile(oldRooms)){
            Path backup=root.resolve("backups").resolve("legacy-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss"))+"-"+UUID.randomUUID());
            Files.createDirectories(backup);
            for(Path old:List.of(oldSettings,oldRooms))if(Files.isRegularFile(old))Files.move(old,backup.resolve(old.getFileName()));
        }
        return result;
    }
    private JsonObject loadSettings()throws IOException{
        try(var reader=Files.newBufferedReader(settings)){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(RuntimeException error){throw new IOException("Invalid map settings",error);}
    }
    void saveSettings(JsonObject value)throws IOException{
        if(value.has("presets"))throw new IllegalArgumentException("Presets must be saved separately");
        Files.createDirectories(root);Path temp=Files.createTempFile(root,"settings-",".tmp");
        try{Files.writeString(temp,GSON.toJson(value));MapLayoutCodec.move(temp,settings);}finally{Files.deleteIfExists(temp);}
    }
    Map<String,MapEdits.Preset> loadPresets()throws IOException{
        var result=new TreeMap<String,MapEdits.Preset>();
        var paths=new HashMap<String,Path>();
        try(var files=Files.list(presets)){
            for(Path file:files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ccmap")).sorted().toList()){
                try{
                var layout=MapLayoutCodec.read(file);var edits=new ArrayList<MapEdits.Edit>(layout.edits().size());
                layout.edits().forEach((p,material)->edits.add(new MapEdits.Edit(p,material,Boolean.TRUE.equals(layout.origins().get(p)))));
                MapEdits.Preset preset;
                try{preset=new MapEdits.Preset(layout.name(),edits,layout.fingerprint());}catch(RuntimeException error){throw new IOException("Invalid map preset",error);}
                if(result.put(preset.name(),preset)!=null)throw new IOException("Duplicate map preset: "+preset.name());
                paths.put(preset.name(),file);
                }catch(IOException|RuntimeException error){throw new IOException("Could not read preset "+file.getFileName()+": "+error.getMessage(),error);}
            }
        }
        presetFiles=Map.copyOf(paths);return result;
    }
    private Path presetFile(String name){return presetFiles.getOrDefault(name,MapLayoutCodec.path(presets,name));}
    private void rememberFile(String name,Path path){var next=new HashMap<>(presetFiles);if(path==null)next.remove(name);else next.put(name,path);presetFiles=Map.copyOf(next);}
    void savePreset(MapEdits.Preset preset)throws IOException{
        var edits=new HashMap<MapEdits.Position,String>();var origins=new HashMap<MapEdits.Position,Boolean>();
        for(var edit:preset.edits()){if(edits.put(edit.position(),edit.material())!=null)throw new IOException("Duplicate preset coordinate");if(edit.originallyAir())origins.put(edit.position(),true);}
        Path target=presetFile(preset.name());MapLayoutCodec.write(target,new MapLayoutCodec.Layout(preset.name(),preset.fingerprint(),null,edits,origins));rememberFile(preset.name(),target);
    }
    private static MapLayoutCodec.Layout readLegacyPreset(JsonReader reader)throws IOException{
        String name=null,fingerprint="";boolean foundEdits=false;var edits=new HashMap<MapEdits.Position,String>();var origins=new HashMap<MapEdits.Position,Boolean>();var palette=new HashMap<String,String>();
        reader.beginObject();while(reader.hasNext())switch(reader.nextName()){
            case "name" -> name=reader.nextString();
            case "fingerprint" -> {if(reader.peek()==JsonToken.NULL)reader.nextNull();else fingerprint=reader.nextString();}
            case "edits" -> {
                if(foundEdits)throw new IOException("Duplicate preset edits");foundEdits=true;reader.beginArray();while(reader.hasNext()){
                    MapEdits.Edit edit=GSON.fromJson(reader,MapEdits.Edit.class);if(edit==null)throw new IOException("Invalid preset edit");
                    String material=palette.computeIfAbsent(edit.material(),id->id);
                    if(edits.put(edit.position(),material)!=null)throw new IOException("Duplicate preset coordinate");if(edit.originallyAir())origins.put(edit.position(),true);
                }reader.endArray();
            }
            default -> reader.skipValue();
        }reader.endObject();new MapEdits.Preset(name,List.of(),fingerprint);if(!foundEdits)throw new IOException("Missing preset edits");
        return new MapLayoutCodec.Layout(name,fingerprint,null,edits,origins);
    }
    void renamePreset(MapEdits.Preset old,String name)throws IOException{
        var replacement=new MapEdits.Preset(name,old.edits(),old.fingerprint());
        if(old.name().equals(name))return;
        Path target=MapLayoutCodec.path(presets,name);
        if(Files.exists(target))throw new IOException("A preset with that name already exists");
        MapLayoutCodec.rename(presetFile(old.name()),target,replacement.name());
        try{deletePreset(old.name());}catch(IOException error){Files.deleteIfExists(target);throw error;}
        rememberFile(name,target);
    }
    void deletePreset(String name)throws IOException{
        Path source=presetFile(name);if(!Files.exists(source)){rememberFile(name,null);return;}
        Path backup=root.resolve("backups").resolve("presets").resolve(UUID.randomUUID().toString());Files.createDirectories(backup);
        Files.move(source,backup.resolve(source.getFileName()));
        rememberFile(name,null);
    }
}
