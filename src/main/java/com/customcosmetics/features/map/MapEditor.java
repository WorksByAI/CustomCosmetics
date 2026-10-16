// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.utils.MessageUtils;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

/** Local map editor. Edits are consumed only by renderers; authoritative states stay untouched. */
public final class MapEditor {
    public static boolean enabled,editing,dungeonOnly=true,autoRooms=true,showHud=true,forceDungeon;
    public static String material="minecraft:stone",presetName="My room";
    public static final MapEdits DATA=new MapEdits();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String,MapEdits.Preset> PRESETS=new TreeMap<>();
    public static final RoomLayouts ROOMS=new RoomLayouts();
    private static Path roomsFile;private static long nextRoomSave;
    private static final Set<String> appliedLayouts=new HashSet<>();
    private static final Map<MapEdits.Position,Optional<String>> pendingSaves=new LinkedHashMap<>();
    public static final Set<String> favorites=new LinkedHashSet<>();
    public static String lastRoom="None";
    private static volatile Map<MapEdits.Position,MapEdits.Edit> visible=Map.of();
    private static ClientLevel level;private static Path file;private static long nextScan,nextRestrictionNotice;
    private static MapEdits.Position anchor;private static BlockPos first,second;
    private MapEditor(){}
    public static MapEdits.Position position(BlockPos p){return new MapEdits.Position(p.getX(),p.getY(),p.getZ());}
    public static BlockPos blockPos(MapEdits.Position p){return new BlockPos(p.x(),p.y(),p.z());}
    public static void init(){file=CosmeticsConfigDir.getCosmeticsConfigDir().resolve("map-editor.json");if(Files.isRegularFile(file))try{
        var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        enabled=root.has("enabled")&&root.get("enabled").getAsBoolean();dungeonOnly=!root.has("dungeonOnly")||root.get("dungeonOnly").getAsBoolean();autoRooms=!root.has("autoRooms")||root.get("autoRooms").getAsBoolean();
        forceDungeon=root.has("forceDungeon")&&root.get("forceDungeon").getAsBoolean();
        if(root.has("freecamSpeed"))Freecam.speed=Freecam.validSpeed(root.get("freecamSpeed").getAsDouble());
        if(root.has("showHud"))showHud=root.get("showHud").getAsBoolean();
        if(root.has("material")&&state(root.get("material").getAsString())!=null)material=root.get("material").getAsString();
        if(root.has("favorites"))for(var item:root.getAsJsonArray("favorites"))if(favorites.size()<256&&selectable(item.getAsString()))favorites.add(item.getAsString());
        if(root.has("presets"))for(var item:root.getAsJsonArray("presets")){try{var p=GSON.fromJson(item,MapEdits.Preset.class);if(p!=null)PRESETS.put(p.name(),p);}catch(RuntimeException ignored){}}
    }catch(Exception error){notice("Could not load map presets: "+error.getMessage());}
        roomsFile=file.resolveSibling("map-rooms.json");try{ROOMS.load(roomsFile);}catch(Exception error){notice(error.getMessage());}
        DungeonRooms.init();MapEditorUi.init();GhostBlocks.init();
        Freecam.init();
    }
    public static void save(){if(file==null)return;try{var root=new JsonObject();root.addProperty("enabled",enabled);root.addProperty("dungeonOnly",dungeonOnly);root.addProperty("forceDungeon",forceDungeon);root.addProperty("autoRooms",autoRooms);root.addProperty("showHud",showHud);root.addProperty("material",material);root.add("favorites",GSON.toJsonTree(favorites));root.addProperty("freecamSpeed",Freecam.validSpeed(Freecam.speed));root.add("presets",GSON.toJsonTree(PRESETS.values()));Files.createDirectories(file.getParent());var temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(root));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}catch(Exception error){notice("Could not save map presets: "+error.getMessage());}}
    public static void flushRooms(){if(roomsFile!=null)try{ROOMS.save(roomsFile);}catch(Exception error){notice("Could not save room edits: "+error.getMessage());}}
    public static String importFme(FmeImport.Plan plan,boolean replace)throws java.io.IOException{
        if(roomsFile==null)throw new java.io.IOException("Map Editor is not initialized");ROOMS.save(roomsFile);save();
        var backup=file.getParent().resolve("map-import-backups").resolve(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")));Files.createDirectories(backup);
        for(var source:List.of(file,roomsFile))if(Files.isRegularFile(source))Files.copy(source,backup.resolve(source.getFileName()));
        var candidate=new RoomLayouts();candidate.load(roomsFile);candidate.importLayouts(plan.layouts(),replace);var temp=roomsFile.resolveSibling("map-rooms-import.json");candidate.save(temp);Files.move(temp,roomsFile,StandardCopyOption.REPLACE_EXISTING);ROOMS.load(roomsFile);
        for(var id:plan.favorites())if(favorites.size()<256)favorites.add(id);save();
        var before=DATA.snapshot();var kept=new LinkedHashMap<>(before);if(replace)kept.keySet().removeIf(p->{var frame=DungeonRooms.at(p);return frame!=null&&plan.layouts().containsKey(frame.key());});DATA.replace(kept);dirty(before.keySet());appliedLayouts.clear();DungeonRooms.reset();nextScan=0;refresh();
        return "Imported FME config. Backup: "+backup;
    }
    public static boolean allowed(Minecraft client){return client.level!=null&&client.player!=null&&(!dungeonOnly||client.hasSingleplayerServer()||inDungeon(client));}
    public static boolean inDungeon(Minecraft client){return client.level!=null&&client.player!=null&&(forceDungeon||automaticDungeon(client));}
    public static boolean automaticDungeon(Minecraft client){return DungeonSidebarReader.read(client).detected();}
    public static List<String> sidebarRows(){return DungeonSidebarReader.read(Minecraft.getInstance()).rows();}
    public static String floor(){return DungeonSidebarReader.read(Minecraft.getInstance()).floor();}
    public static void forceDungeon(boolean value){forceDungeon=value;nextScan=0;var client=Minecraft.getInstance();DungeonRooms.context(client);refresh();if(enabled&&inDungeon(client)){DungeonRooms.scan();savePending();if(autoRooms)applyRooms();}}
    public static void reportStatus(){
        notice(status());var sidebar=DungeonSidebarReader.read(Minecraft.getInstance(),true);
        com.customcosmetics.client.CustomCosmeticsClient.LOGGER.info("Map dungeon detector: forced={}; automatic={}; objective={}; title={}; floor={}; rows={}",forceDungeon,sidebar.detected(),sidebar.objective(),sidebar.title(),sidebar.floor(),sidebar.raw());
    }
    public static void tick(Minecraft client){
        if(level!=client.level){flushRooms();level=client.level;visible=Map.of();DATA.reset();pendingSaves.clear();appliedRooms.clear();appliedLayouts.clear();DungeonRooms.reset();anchor=null;first=second=null;editing=false;nextScan=0;lastRoom="None";}
        DungeonRooms.context(client);MapEditorUi.tick(client);long now=System.currentTimeMillis();if(now>=nextRoomSave){nextRoomSave=now+750;flushRooms();}
        MaterialReplacement.tick(client);boolean active=enabled&&allowed(client);
        if(!active){if(!visible.isEmpty()){visible=Map.of();dirty(DATA.snapshot().keySet());}return;}
        if(now>=nextScan){nextScan=now+2000;DungeonRooms.scan();savePending();if(autoRooms){applyRooms();if(!editing)scanRooms(client);}}
        visible=DATA.snapshot();
    }
    public static void refresh(){visible=enabled&&allowed(Minecraft.getInstance())?DATA.snapshot():Map.of();dirty(DATA.snapshot().keySet());save();}
    private static void dirty(Collection<MapEdits.Position> positions){if(level==null)return;var sections=new HashSet<MapEdits.Position>();for(var p:positions)sections.add(new MapEdits.Position(p.x()>>4,p.y()>>4,p.z()>>4));for(var p:sections)level.setSectionDirtyWithNeighbors(p.x(),p.y(),p.z());}
    public static BlockState realState(BlockPos p){return Minecraft.getInstance().level.getBlockState(p);}
    public static Map<MapEdits.Position,MapEdits.Edit> visible(){return visible;}
    /** Renderer-only neighbor/section culling. Never change collision, mining or world state. */
    public static boolean revealsFace(BlockPos p){if(visible.isEmpty())return false;var edit=visible.get(position(p));if(edit==null)return false;var target=state(edit.material());return target!=null&&!target.canOcclude();}
    /** Approximate light at a newly exposed render surface without relighting or changing the world. */
    public static int visualLight(net.minecraft.world.level.BlockAndLightGetter world,BlockPos p){int block=world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,p),sky=world.getBrightness(net.minecraft.world.level.LightLayer.SKY,p);for(var face:Direction.values()){var q=p.relative(face);block=Math.max(block,world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,q));sky=Math.max(sky,world.getBrightness(net.minecraft.world.level.LightLayer.SKY,q));}return block<<4|sky<<20;}
    public static BlockState state(String id){try{int bracket=id.indexOf('[');String name=bracket<0?id:id.substring(0,bracket);var key=Identifier.tryParse(name);var result=key==null?null:BuiltInRegistries.BLOCK.getOptional(key).map(Block::defaultBlockState).orElse(null);if(result==null)return null;if(bracket>=0){if(!id.endsWith("]"))return null;var seen=new HashSet<String>();for(String part:id.substring(bracket+1,id.length()-1).split(",")){String[] pair=part.split("=",-1);if(pair.length!=2||!seen.add(pair[0]))return null;var property=result.getBlock().getStateDefinition().getProperty(pair[0]);if(property==null)return null;result=withProperty(result,property,pair[1]);if(result==null)return null;}}return result;}catch(RuntimeException error){return null;}}
    private static <T extends Comparable<T>> BlockState withProperty(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value){return property.getValue(value).map(v->state.setValue(property,v)).orElse(null);}
    public static String rotateMaterial(String id,int turns){var state=state(id);if(state==null)return id;var rotated=state.rotate(Rotation.values()[Math.floorMod(turns,4)]);String name=BuiltInRegistries.BLOCK.getKey(rotated.getBlock()).toString();if(rotated.getProperties().isEmpty())return name;var props=new TreeMap<String,String>();rotated.getProperties().forEach(p->props.put(p.getName(),propertyName(p,rotated.getValue(p))));return name+"["+String.join(",",props.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"]";}
    @SuppressWarnings({"rawtypes","unchecked"}) private static String propertyName(net.minecraft.world.level.block.state.properties.Property property,Comparable value){return property.getName(value);}
    public static BlockStateModel wrap(BlockStateModel model,BlockPos p){
        if(model instanceof MaterialModel)return model;
        var edit=visible.get(position(p));if(edit==null)return model;
        var target=state(edit.material());if(target==null)return model;
        return new MaterialModel(model,Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(target),target.isAir());
    }
    public static BlockHitResult target(){
        var client=Minecraft.getInstance();if(client.player==null)return null;
        var viewer=client.getCameraEntity()==null?client.player:client.getCameraEntity();
        var start=viewer.getEyePosition();var end=start.add(viewer.getLookAngle().scale(6));
        var ray=client.level.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,viewer){
            @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos){var edit=visible.get(position(pos));return edit!=null&&edit.material().equals("minecraft:air")?net.minecraft.world.phys.shapes.Shapes.empty():super.getBlockShape(state,world,pos);}
        });
        BlockHitResult best=ray.getType()==HitResult.Type.BLOCK?ray:null;double distance=best==null?36:start.distanceToSqr(best.getLocation());
        for(var e:visible.values())if(e.ghost(client.level.getBlockState(blockPos(e.position())).isAir())){
            var box=new AABB(blockPos(e.position()));var point=box.clip(start,end);if(point.isPresent()&&start.distanceToSqr(point.get())<distance){var at=point.get();var d=Direction.getApproximateNearest(start.x-at.x,start.y-at.y,start.z-at.z);best=new BlockHitResult(at,d,blockPos(e.position()),false);distance=start.distanceToSqr(at);}
        }return best;
    }
    public static boolean click(int button){var client=Minecraft.getInstance();if(!editing||!enabled)return false;
        if(!allowed(client)){long now=System.currentTimeMillis();if(now>=nextRestrictionNotice){nextRestrictionNotice=now+3000;notice("Map Editor: dungeon not detected. Enable Force Dungeon in Map Editor, or use /customcosmetics map status.");}return true;}
        var hit=target();if(hit==null)return true;
        if(button==2){var edit=DATA.snapshot().get(position(hit.getBlockPos()));String id=edit==null?BuiltInRegistries.BLOCK.getKey(realState(hit.getBlockPos()).getBlock()).toString():edit.material();if(selectable(id)){material=id;save();}return true;}
        // Keyboard events precede the next body input tick; detached cameras can also suppress body sneak.
        var p=button==1?hit.getBlockPos().relative(hit.getDirection()):hit.getBlockPos();
        boolean force=client.hasShiftDown()||client.options.keyShift.isDown();
        MapCommands.run(()->{var before=DATA.snapshot();var old=before.get(position(p));
            if(button==0){if(old!=null)DATA.remove(position(p));else put(p,"minecraft:air");}
            else if(old!=null&&old.material().equals("minecraft:air")&&!force)DATA.remove(position(p));else put(p,material);
            remember(before);dirty(before.keySet());refresh();});return true;
    }
    private static void validateMaterial(String id){if(state(id)==null)throw new IllegalArgumentException("Unknown block: "+id);}
    public static boolean selectable(String id){var state=state(id);return state!=null&&!state.isAir();}
    public static net.minecraft.world.item.ItemStack icon(BlockState state){if(state.is(Blocks.WATER))return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);if(state.is(Blocks.LAVA))return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);return new net.minecraft.world.item.ItemStack(state.getBlock());}
    public static boolean hideBlockEntity(net.minecraft.world.level.block.entity.BlockEntity entity){var client=Minecraft.getInstance();var p=entity.getBlockPos();return client.level!=null&&visible.containsKey(position(p))&&client.level.getBlockEntity(p)==entity;}
    public static void favorite(String id){if(!favorites.remove(id)&&selectable(id)&&favorites.size()<256)favorites.add(id);save();}
    public static void put(BlockPos p,String id){var client=Minecraft.getInstance();validateMaterial(id);var old=DATA.snapshot().get(position(p));DATA.put(new MapEdits.Edit(position(p),id,old==null?client.level.getBlockState(p).isAir():old.originallyAir()));}
    static void applyBatch(Map<MapEdits.Position,MapEdits.Edit> before,Map<MapEdits.Position,MapEdits.Edit> next){
        if(DATA.snapshot()!=before)throw new IllegalStateException("Edits changed while scanning. No batch changes were applied.");
        var grouped=new LinkedHashMap<RoomLayouts.Frame,Map<MapEdits.Position,Optional<String>>>();
        var origins=new HashMap<MapEdits.Position,Boolean>();var rotations=new HashMap<String,String>();
        var unknown=new LinkedHashMap<MapEdits.Position,Optional<String>>();boolean dungeon=inDungeon(Minecraft.getInstance());
        next.forEach((p,edit)->{if(!edit.equals(before.get(p))){
            var frame=DungeonRooms.at(p);
            if(frame!=null)grouped.computeIfAbsent(frame,f->new LinkedHashMap<>()).put(p,Optional.of(rotations.computeIfAbsent(frame.turns()+":"+edit.material(),key->rotateMaterial(edit.material(),-frame.turns()))));
            else if(dungeon)unknown.put(p,Optional.of(edit.material()));
            origins.put(p,edit.originallyAir());
        }});
        var candidate=ROOMS.copy();grouped.forEach((frame,changes)->candidate.changeAll(frame,changes,origins));
        // Adopt the preflight result once instead of rebuilding every room a second time.
        DATA.replace(next);ROOMS.adopt(candidate);pendingSaves.putAll(unknown);
        grouped.forEach((frame,changes)->{changes.keySet().forEach(pendingSaves::remove);lastRoom=frame.label();});
        dirty(before.keySet());enabled=true;refresh();flushRooms();
    }
    public static MaterialReplacementPlan.Bounds selection(){return first==null||second==null?null:MaterialReplacementPlan.Bounds.between(position(first),position(second));}
    private static void remember(Map<MapEdits.Position,MapEdits.Edit> before){
        var after=DATA.snapshot();var changed=new HashSet<>(before.keySet());changed.addAll(after.keySet());var updates=new LinkedHashMap<MapEdits.Position,Optional<String>>();
        for(var p:changed)if(!Objects.equals(before.get(p),after.get(p))){var edit=after.get(p);updates.put(p,Optional.ofNullable(edit==null?null:edit.material()));}persist(updates,true);
    }
    private static void persist(Map<MapEdits.Position,Optional<String>> changes,boolean queueUnknown){
        boolean dungeon=inDungeon(Minecraft.getInstance());var grouped=new LinkedHashMap<RoomLayouts.Frame,Map<MapEdits.Position,Optional<String>>>();
        for(var e:changes.entrySet()){var frame=DungeonRooms.at(e.getKey());if(frame==null){if(queueUnknown&&dungeon)pendingSaves.put(e.getKey(),e.getValue());continue;}grouped.computeIfAbsent(frame,f->new LinkedHashMap<>()).put(e.getKey(),e.getValue().map(id->rotateMaterial(id,-frame.turns())));}
        grouped.forEach((frame,edits)->{var origins=new HashMap<MapEdits.Position,Boolean>();for(var p:edits.keySet()){var edit=DATA.snapshot().get(p);if(edit!=null)origins.put(p,edit.originallyAir());}ROOMS.changeAll(frame,edits,origins);edits.keySet().forEach(pendingSaves::remove);lastRoom=frame.label();});
    }
    private static void savePending(){persist(new LinkedHashMap<>(pendingSaves),false);}
    public static RoomLayouts.Frame currentRoom(){var client=Minecraft.getInstance();if(client.player==null)return null;var camera=client.getCameraEntity();return DungeonRooms.at(position((camera==null?client.player:camera).blockPosition()));}
    public static String roomLabel(){var frame=currentRoom();return frame==null?(inDungeon(Minecraft.getInstance())?"Scanning / unrecognized room (session edits)":"Outside dungeon (session edits)"):frame.label();}
    private static void applyRooms(){
        var frames=new HashSet<>(DungeonRooms.frames());var current=currentRoom();if(current!=null)frames.add(current);var copy=new LinkedHashMap<>(DATA.snapshot());boolean changed=false;
        for(var frame:frames){String id=frame.key()+"@"+frame.origin();var edits=ROOMS.edits(frame.key());if(edits.isEmpty()||appliedLayouts.contains(id))continue;boolean complete=true;var origins=new HashMap<MapEdits.Position,Boolean>();
            for(var e:edits.entrySet()){var p=frame.world(e.getKey());if(copy.containsKey(p))continue;if(!level.hasChunkAt(blockPos(p))){complete=false;continue;}
                var real=realState(blockPos(p));if(state(e.getValue())!=null){Boolean air=ROOMS.originallyAir(frame.key(),e.getKey());if(air==null){air=real.isAir();origins.put(e.getKey(),air);}copy.put(p,new MapEdits.Edit(p,rotateMaterial(e.getValue(),frame.turns()),air));changed=true;}
            }if(!origins.isEmpty())ROOMS.rememberOrigins(frame.key(),origins);if(complete)appliedLayouts.add(id);
        }if(changed){DATA.replace(copy);refresh();}
    }
    public static void undo(){var before=DATA.snapshot();DATA.undo();remember(before);dirty(before.keySet());refresh();}
    public static void clear(){var before=DATA.snapshot();DATA.clear();remember(before);dirty(before.keySet());refresh();}
    public static void clearRoom(){var frame=currentRoom();if(frame==null)throw new IllegalArgumentException("No recognized room here");var before=DATA.snapshot();var copy=new LinkedHashMap<>(before);copy.keySet().removeIf(p->{var at=DungeonRooms.at(p);return at!=null&&at.key().equals(frame.key())&&at.origin().equals(frame.origin());});DATA.replace(copy);remember(before);ROOMS.clear(frame.key());dirty(before.keySet());refresh();}
    public static List<String> presets(){return List.copyOf(PRESETS.keySet());}
    public static MapEdits.Position anchor(){var client=Minecraft.getInstance();if(anchor==null&&client.player!=null){var p=client.player.blockPosition();anchor=new MapEdits.Position(Math.round((p.getX()+185)/32f)*32-185,0,Math.round((p.getZ()+185)/32f)*32-185);}return anchor;}
    public static void setAnchor(){var client=Minecraft.getInstance();if(client.player!=null){var p=client.player.blockPosition();anchor=new MapEdits.Position(p.getX(),0,p.getZ());notice("Map anchor: "+anchor);}}
    public static void savePreset(){if(anchor()==null)throw new IllegalStateException("Join a world first");var preset=DATA.preset(presetName,anchor(),fingerprint(anchor(),0));PRESETS.put(preset.name(),preset);save();notice("Saved map preset: "+preset.name());}
    public static void loadPreset(int turns){var preset=PRESETS.get(presetName);if(preset==null)throw new IllegalArgumentException("Preset not found");if(anchor()==null)throw new IllegalStateException("Join a world first");var old=DATA.snapshot().keySet();DATA.replace(preset.place(anchor(),turns));dirty(old);refresh();}
    public static void deletePreset(){PRESETS.remove(presetName);save();}
    private static String fingerprint(MapEdits.Position origin,int turns){
        if(level==null)return "";return RoomFingerprint.compute(origin,turns,p->{var at=blockPos(p);if(!level.hasChunkAt(at))return null;var s=level.getBlockState(at);return s.isAir()?"minecraft:air":BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();});
    }
    private static final Set<String> appliedRooms=new HashSet<>();
    private static void scanRooms(Minecraft client){if(PRESETS.isEmpty()||!inDungeon(client))return;
        var player=client.player.blockPosition();for(int x=-185;x<=-25;x+=32)for(int z=-185;z<=-25;z+=32){
            if(Math.abs(x-player.getX())>64||Math.abs(z-player.getZ())>64)continue;
            var origin=new MapEdits.Position(x,0,z);String key=x+":"+z;if(appliedRooms.contains(key))continue;
            for(int turns=0;turns<4;turns++){String hash=fingerprint(origin,turns);if(hash.isEmpty())break;
                var preset=PRESETS.values().stream().filter(p->!p.fingerprint().isEmpty()&&p.fingerprint().equals(hash)).findFirst();if(preset.isEmpty())continue;
                var copy=new LinkedHashMap<>(DATA.snapshot());copy.putAll(preset.get().place(origin,turns));DATA.replace(copy);appliedRooms.add(key);refresh();break;
            }
        }
    }
    public static void mark(boolean end){var hit=target();if(hit==null)throw new IllegalStateException("Look at a block");if(end)second=hit.getBlockPos();else first=hit.getBlockPos();notice((end?"Second":"First")+" corner: "+hit.getBlockPos().toShortString());}
    public static void fill(){
        if(first==null||second==null)throw new IllegalStateException("Set pos1 and pos2 first");validateMaterial(material);
        var copy=new LinkedHashMap<>(DATA.snapshot());var client=Minecraft.getInstance();
        for(var p:BlockPos.betweenClosed(first,second)){
            var state=client.level.getBlockState(p);var old=copy.get(position(p));
            copy.put(position(p),new MapEdits.Edit(position(p),material,old==null?state.isAir():old.originallyAir()));
        }var before=DATA.snapshot();DATA.replace(copy);remember(before);refresh();
    }
    public static String status(){var client=Minecraft.getInstance();var hit=client.level==null?null:target();return "Map Editor: "+(enabled?"ON":"OFF")+"; Edit Mode: "+(editing?"ON":"OFF")+"; dungeon: "+(forceDungeon?"forced":automaticDungeon(client)?"detected":"not detected")+"; floor: "+(floor().isEmpty()?"unknown":floor())+"; room: "+roomLabel()+"; editing: "+(allowed(client)?"allowed":"blocked")+"; target: "+(hit==null?"none within 6 blocks":hit.getBlockPos().toShortString());}
    public static void notice(String s){MessageUtils.sendMessage(s);}
}
