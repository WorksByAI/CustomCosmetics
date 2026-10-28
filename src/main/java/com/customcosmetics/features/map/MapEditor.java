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
    public static boolean enabled,editing,dungeonOnly=true,autoRooms=true,showHud=true,forceDungeon,forceKuudra;
    public static String material="minecraft:stone",presetName="My room";
    public static final MapEdits DATA=new MapEdits();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String,MapEdits.Preset> PRESETS=new TreeMap<>();
    public static final RoomLayouts ROOMS=new RoomLayouts();
    private static final RoomSaveQueue roomSaves=new RoomSaveQueue();
    private static final RoomRestorer restorer=new RoomRestorer();
    private static final SectionUpdateQueue sectionUpdates=new SectionUpdateQueue();
    private static final Direction[] LIGHT_DIRECTIONS=Direction.values();
    private static java.util.concurrent.CompletableFuture<Void> operation;
    private static java.util.concurrent.CompletableFuture<?> workerFence,pendingImport;
    public static boolean busy(){return operation!=null&&!operation.isDone();}
    private static Path roomsFile;private static MapStorage storage;private static long nextRoomSave;
    private static final Set<String> appliedLayouts=new HashSet<>();
    private static final Map<MapEdits.Position,Optional<String>> pendingSaves=new LinkedHashMap<>();
    public static final Set<String> favorites=new LinkedHashSet<>();
    public static String lastRoom="None";
    private static volatile Map<MapEdits.Position,MapEdits.Edit> visible=Map.of();
    private static final java.util.concurrent.ConcurrentMap<String,BlockState> materialStates=new java.util.concurrent.ConcurrentHashMap<>();
    private static ClientLevel level;private static Path file;private static long nextScan,nextRestrictionNotice;
    private static boolean kuudraContext;
    private static MapEdits.Position anchor;private static BlockPos first,second;
    private MapEditor(){}
    public static MapEdits.Position position(BlockPos p){return new MapEdits.Position(p.getX(),p.getY(),p.getZ());}
    public static BlockPos blockPos(MapEdits.Position p){return new BlockPos(p.x(),p.y(),p.z());}
    public static Path roomsPath(){return roomsFile;}
    public static Path settingsPath(){return file;}
    public static String roomOriginWorkerThread(){return restorer.originWorkerThread();}
    public static void init(){try{
        var candidate=new MapStorage(CosmeticsConfigDir.getCosmeticsConfigDir());var root=candidate.initialize();
        var loadedPresets=candidate.loadPresets();ROOMS.load(candidate.rooms);
        storage=candidate;file=candidate.settings;roomsFile=candidate.rooms;PRESETS.clear();PRESETS.putAll(loadedPresets);
        enabled=root.has("enabled")&&root.get("enabled").getAsBoolean();dungeonOnly=!root.has("dungeonOnly")||root.get("dungeonOnly").getAsBoolean();autoRooms=!root.has("autoRooms")||root.get("autoRooms").getAsBoolean();
        forceDungeon=root.has("forceDungeon")&&root.get("forceDungeon").getAsBoolean();forceKuudra=root.has("forceKuudra")&&root.get("forceKuudra").getAsBoolean();if(forceKuudra)forceDungeon=false;
        if(root.has("freecamSpeed"))Freecam.speed=Freecam.validSpeed(root.get("freecamSpeed").getAsDouble());
        if(root.has("showHud"))showHud=root.get("showHud").getAsBoolean();
        if(root.has("material")&&state(root.get("material").getAsString())!=null)material=root.get("material").getAsString();
        if(root.has("favorites"))for(var item:root.getAsJsonArray("favorites"))if(favorites.size()<256&&selectable(item.getAsString()))favorites.add(item.getAsString());
    }catch(Exception error){storage=null;file=null;roomsFile=null;notice("Could not load map storage; originals retained: "+error.getMessage());}
        DungeonRooms.init();MapEditorUi.init();GhostBlocks.init();
        Freecam.init();
    }
    public static void save(){if(file==null)return;try{var root=new JsonObject();root.addProperty("enabled",enabled);root.addProperty("dungeonOnly",dungeonOnly);root.addProperty("forceDungeon",forceDungeon);root.addProperty("forceKuudra",forceKuudra);root.addProperty("autoRooms",autoRooms);root.addProperty("showHud",showHud);root.addProperty("material",material);root.add("favorites",GSON.toJsonTree(favorites));root.addProperty("freecamSpeed",Freecam.validSpeed(Freecam.speed));storage.saveSettings(root);}catch(Exception error){notice("Could not save map settings: "+error.getMessage());}}
    public static void flushRooms(){if(roomsFile!=null)try{if(workerFence!=null)try{workerFence.join();}catch(java.util.concurrent.CompletionException ignored){}if(pendingImport!=null&&!pendingImport.isCompletedExceptionally()){pendingImport.join();return;}roomSaves.flush(ROOMS,roomsFile);}catch(Exception error){notice("Could not save room edits: "+error.getMessage());}}
    private static void saveRoomEdits(){if(busy())return;if(roomsFile!=null)try{roomSaves.tick(ROOMS,roomsFile);}catch(Exception error){notice("Could not save room edits: "+error.getMessage());}}
    public static String importFme(FmeImport.Plan plan,boolean replace)throws java.io.IOException{
        if(roomsFile==null)throw new java.io.IOException("Map Editor is not initialized");flushRooms();ROOMS.save(roomsFile);save();
        var backup=file.getParent().resolve("backups").resolve("fme-imports").resolve(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")));Files.createDirectories(backup);
        if(Files.isRegularFile(file))Files.copy(file,backup.resolve(file.getFileName()));
        ROOMS.importTo(roomsFile,backup.resolve("rooms"),plan.layouts(),replace);
        for(var id:plan.favorites())if(favorites.size()<256)favorites.add(id);save();
        var before=DATA.snapshot();var kept=new LinkedHashMap<>(before);if(replace)kept.keySet().removeIf(p->{var frame=DungeonRooms.at(p);return frame!=null&&plan.layouts().containsKey(frame.key());});DATA.replace(kept);dirty(before.keySet());appliedLayouts.clear();DungeonRooms.reset();nextScan=0;refresh();
        return "Imported FME config. Backup: "+backup;
    }
    public static boolean allowed(Minecraft client){return com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.MAP)&&client.level!=null&&client.player!=null&&(!dungeonOnly||client.hasSingleplayerServer()||inMapArea(client));}
    public static boolean inDungeon(Minecraft client){return client.level!=null&&client.player!=null&&(forceDungeon||automaticDungeon(client));}
    public static boolean automaticKuudra(Minecraft client){return DungeonSidebarReader.read(client).kuudra();}
    public static boolean inKuudra(Minecraft client){return client.level!=null&&client.player!=null&&(forceKuudra||automaticKuudra(client));}
    public static boolean inMapArea(Minecraft client){return inKuudra(client)||inDungeon(client);}
    public static boolean automaticDungeon(Minecraft client){return DungeonSidebarReader.read(client).detected();}
    public static List<String> sidebarRows(){return DungeonSidebarReader.read(Minecraft.getInstance()).rows();}
    static boolean offlineBoss(Minecraft client){return client.level!=null&&forceDungeon&&!inKuudra(client)&&client.hasSingleplayerServer()&&DungeonSidebarReader.read(client).floor().isEmpty();}
    public static String floor(){var client=Minecraft.getInstance();return offlineBoss(client)?"F7":DungeonSidebarReader.read(client).floor();}
    public static void forceDungeon(boolean value){forceDungeon=value;if(value)forceKuudra=false;updateArea();}
    public static void forceKuudra(boolean value){forceKuudra=value;if(value)forceDungeon=false;updateArea();}
    private static void updateArea(){
        var client=Minecraft.getInstance();syncArea(client);nextScan=0;DungeonRooms.context(client);refresh();
        if(enabled&&inMapArea(client)){DungeonRooms.scan();savePending();if(autoRooms)applyRooms();}
    }
    /** Arena switches can happen without a new ClientLevel. Session edits/undo must never cross arenas. */
    private static void syncArea(Minecraft client){
        boolean next=inKuudra(client);if(next==kuudraContext)return;
        saveRoomEdits();dirty(DATA.snapshot().keySet());visible=Map.of();DATA.reset();GhostBlocks.reset();pendingSaves.clear();appliedRooms.clear();appliedLayouts.clear();DungeonRooms.reset();restorer.reset();MaterialReplacement.cancel();anchor=null;first=second=null;editing=false;lastRoom="None";nextScan=0;kuudraContext=next;
    }
    public static void reportStatus(){
        notice(status());var sidebar=DungeonSidebarReader.read(Minecraft.getInstance(),true);
        com.customcosmetics.client.CustomCosmeticsClient.LOGGER.info("Map area detector: forcedDungeon={}; forcedKuudra={}; dungeon={}; kuudra={}; objective={}; title={}; floor={}; rows={}",forceDungeon,forceKuudra,sidebar.detected(),sidebar.kuudra(),sidebar.objective(),sidebar.title(),sidebar.floor(),sidebar.raw());
    }
    public static void tick(Minecraft client){
        // Cleanup still runs after disabling the module so already-built visual sections are restored.
        sectionUpdates.context(client.level);
        drainSectionUpdates(client.level);
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.MAP)){
            if(level!=null){saveRoomEdits();if(!visible.isEmpty()){visible=Map.of();dirty(DATA.snapshot().keySet());}GhostBlocks.reset();DungeonRooms.reset();DungeonSidebarReader.clearCache();restorer.reset();MaterialReplacement.cancel();MapRegionFill.cancel();level=null;}
            return;
        }
        if(level!=client.level){saveRoomEdits();level=client.level;visible=Map.of();DATA.reset();GhostBlocks.reset();pendingSaves.clear();appliedRooms.clear();appliedLayouts.clear();DungeonRooms.reset();restorer.reset();anchor=null;first=second=null;editing=false;nextScan=0;lastRoom="None";kuudraContext=inKuudra(client);}
        syncArea(client);DungeonRooms.context(client);MapEditorUi.tick(client);long now=System.currentTimeMillis();if(now>=nextRoomSave){nextRoomSave=now+750;saveRoomEdits();}
        MaterialReplacement.tick(client);MapRegionFill.tick(client);boolean active=enabled&&allowed(client);
        if(!active){restorer.reset();if(!visible.isEmpty()){visible=Map.of();dirty(DATA.snapshot().keySet());}return;}
        // With no saved layouts or active editing, discovery has no consumer.
        // In particular, merely enabling the module must not scan an empty run.
        if(editing||ROOMS.count()!=0||hasLegacyPresets())DungeonRooms.scanStep();
        if(now>=nextScan){nextScan=now+2000;savePending();}
        if(autoRooms&&!busy()&&!MapRegionFill.busy()){if(ROOMS.count()!=0)applyRooms();else restorer.reset();if(!editing)scanRooms(client);}else restorer.cancel();visible=DATA.snapshot();
    }
    public static void refresh(){var next=enabled&&allowed(Minecraft.getInstance())?DATA.snapshot():Map.<MapEdits.Position,MapEdits.Edit>of();if(next==visible){save();return;}refresh(DATA.snapshot().keySet());}
    private static void refresh(Collection<MapEdits.Position> changes){visible=enabled&&allowed(Minecraft.getInstance())?DATA.snapshot():Map.of();dirty(changes);save();}
    private static void dirty(Collection<MapEdits.Position> positions){
        var target=level;if(target==null||positions.isEmpty())return;
        if(positions.size()>4096){java.util.concurrent.CompletableFuture.supplyAsync(()->sections(positions),MapWork.EXECUTOR).thenAccept(changed->Minecraft.getInstance().execute(()->queueSectionUpdates(target,changed)));return;}
        queueSectionUpdates(target,sections(positions));
    }
    private static it.unimi.dsi.fastutil.longs.LongSet sections(Collection<MapEdits.Position> positions){
        var sections=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for(var p:positions)sections.add(SectionPos.asLong(p.x()>>4,p.y()>>4,p.z()>>4));
        return sections;
    }
    private static void queueSectionUpdates(ClientLevel target,it.unimi.dsi.fastutil.longs.LongCollection sections){
        if(Minecraft.getInstance().level!=target)return;
        // Local edits must not wait behind a large restore that is already queued.
        if(sections.size()<=8){var iterator=sections.iterator();while(iterator.hasNext())updateSection(target,iterator.nextLong());return;}
        sectionUpdates.context(target);sectionUpdates.add(target,sections);
    }
    private static void drainSectionUpdates(ClientLevel target){
        if(target==null||sectionUpdates.size()==0)return;
        sectionUpdates.drain(64,1_000_000,System::nanoTime,section->updateSection(target,section));
    }
    private static void updateSection(ClientLevel target,long section){
        int x=SectionPos.x(section),y=SectionPos.y(section),z=SectionPos.z(section);
        if(target.getChunkSource().getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null)
            target.setSectionDirtyWithNeighbors(x,y,z);
    }
    public static BlockState realState(BlockPos p){return Minecraft.getInstance().level.getBlockState(p);}
    /** ClientLevel.hasChunkAt can accept an absent chunk; never substitute its empty placeholder. */
    public static boolean loaded(BlockPos p){var world=Minecraft.getInstance().level;return world!=null&&world.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null;}
    public static Map<MapEdits.Position,MapEdits.Edit> visible(){return visible;}
    /** Renderer-only neighbor/section culling. Never change collision, mining or world state. */
    public static boolean revealsFace(BlockPos p){if(visible.isEmpty())return false;var edit=visible.get(position(p));if(edit==null)return false;var target=state(edit.material());return target!=null&&!target.canOcclude();}
    /** Approximate light at a newly exposed render surface without relighting or changing the world. */
    public static int visualLight(net.minecraft.world.level.BlockAndLightGetter world,BlockPos p){int block=world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,p),sky=world.getBrightness(net.minecraft.world.level.LightLayer.SKY,p);for(var face:LIGHT_DIRECTIONS){var q=p.relative(face);block=Math.max(block,world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,q));sky=Math.max(sky,world.getBrightness(net.minecraft.world.level.LightLayer.SKY,q));}return block<<4|sky<<20;}
    public static BlockState state(String id){if(id==null)return null;var cached=materialStates.get(id);if(cached!=null)return cached;var result=parseState(id);if(result!=null)materialStates.putIfAbsent(id,result);return result;}
    private static BlockState parseState(String id){try{int bracket=id.indexOf('[');String name=bracket<0?id:id.substring(0,bracket);var key=Identifier.tryParse(name);var result=key==null?null:BuiltInRegistries.BLOCK.getOptional(key).map(Block::defaultBlockState).orElse(null);if(result==null)return null;if(bracket>=0){if(!id.endsWith("]"))return null;var seen=new HashSet<String>();for(String part:id.substring(bracket+1,id.length()-1).split(",")){String[] pair=part.split("=",-1);if(pair.length!=2||!seen.add(pair[0]))return null;var property=result.getBlock().getStateDefinition().getProperty(pair[0]);if(property==null)return null;result=withProperty(result,property,pair[1]);if(result==null)return null;}}return result;}catch(RuntimeException error){return null;}}
    private static <T extends Comparable<T>> BlockState withProperty(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value){return property.getValue(value).map(v->state.setValue(property,v)).orElse(null);}
    public static String rotateMaterial(String id,int turns){var state=state(id);if(state==null)return id;var rotated=state.rotate(Rotation.values()[Math.floorMod(turns,4)]);String name=BuiltInRegistries.BLOCK.getKey(rotated.getBlock()).toString();if(rotated.getProperties().isEmpty())return name;var props=new TreeMap<String,String>();rotated.getProperties().forEach(p->props.put(p.getName(),propertyName(p,rotated.getValue(p))));return name+"["+String.join(",",props.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"]";}
    @SuppressWarnings({"rawtypes","unchecked"}) private static String propertyName(net.minecraft.world.level.block.state.properties.Property property,Comparable value){return property.getName(value);}
    public static BlockStateModel wrap(BlockStateModel model,BlockPos p){
        if(visible.isEmpty())return model;
        if(model instanceof MaterialModel)return model;
        var edit=visible.get(position(p));if(edit==null)return model;
        var target=state(edit.material());if(target==null)return model;
        var models=Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        return MaterialModel.cached(models,model,models.get(target),target.isAir());
    }
    public static BlockHitResult target(){
        var client=Minecraft.getInstance();if(client.player==null)return null;
        var viewer=client.getCameraEntity()==null?client.player:client.getCameraEntity();
        var start=viewer.getEyePosition();var end=start.add(viewer.getLookAngle().scale(6));
        var ray=client.level.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,viewer){
            @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos){var edit=visible.get(position(pos));return edit!=null&&edit.material().equals("minecraft:air")?net.minecraft.world.phys.shapes.Shapes.empty():super.getBlockShape(state,world,pos);}
        });
        BlockHitResult best=ray.getType()==HitResult.Type.BLOCK?ray:null;double distance=best==null?36:start.distanceToSqr(best.getLocation());
        for(var e:GhostBlocks.nearby(start.x,start.y,start.z,8))if(e.originallyAir()&&e.ghost(client.level.getBlockState(blockPos(e.position())).isAir())){
            var box=new AABB(blockPos(e.position()));var point=box.clip(start,end);if(point.isPresent()&&start.distanceToSqr(point.get())<distance){var at=point.get();var d=Direction.getApproximateNearest(start.x-at.x,start.y-at.y,start.z-at.z);best=new BlockHitResult(at,d,blockPos(e.position()),false);distance=start.distanceToSqr(at);}
        }return best;
    }
    public static boolean click(int button){var client=Minecraft.getInstance();if(!editing||!enabled)return false;if(busy()||MapRegionFill.busy())return true;
        if(!allowed(client)){long now=System.currentTimeMillis();if(now>=nextRestrictionNotice){nextRestrictionNotice=now+3000;notice("Map Editor: Catacombs or Kuudra not detected. Enable Force Dungeon / Force Kuudra in Map Editor, or use /customcosmetics map status.");}return true;}
        var hit=target();if(hit==null)return true;
        if(button==2){var edit=DATA.snapshot().get(position(hit.getBlockPos()));String id=edit==null?BuiltInRegistries.BLOCK.getKey(realState(hit.getBlockPos()).getBlock()).toString():edit.material();if(selectable(id)){material=id;save();}return true;}
        // Keyboard events precede the next body input tick; detached cameras can also suppress body sneak.
        var p=button==1?hit.getBlockPos().relative(hit.getDirection()):hit.getBlockPos();
        boolean force=client.hasShiftDown()||client.options.keyShift.isDown();
        MapCommands.run(()->{var before=DATA.snapshot();var old=before.get(position(p));
            if(button==0){if(old!=null)DATA.remove(position(p));else put(p,"minecraft:air");}
            else if(old!=null&&old.material().equals("minecraft:air")&&!force)DATA.remove(position(p));else put(p,material);
            var pos=position(p);var after=DATA.snapshot().get(pos);persist(Map.of(pos,Optional.ofNullable(after==null?null:after.material())),true);refresh(Set.of(pos));});return true;
    }
    private static void validateMaterial(String id){if(state(id)==null)throw new IllegalArgumentException("Unknown block: "+id);}
    public static boolean selectable(String id){var state=state(id);return state!=null&&!state.isAir();}
    public static net.minecraft.world.item.ItemStack icon(BlockState state){if(state.is(Blocks.WATER))return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);if(state.is(Blocks.LAVA))return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);return new net.minecraft.world.item.ItemStack(state.getBlock());}
    public static boolean hideBlockEntity(net.minecraft.world.level.block.entity.BlockEntity entity){if(visible.isEmpty())return false;var client=Minecraft.getInstance();var p=entity.getBlockPos();return client.level!=null&&visible.containsKey(position(p))&&client.level.getBlockEntity(p)==entity;}
    public static void favorite(String id){if(!favorites.remove(id)&&selectable(id)&&favorites.size()<256)favorites.add(id);save();}
    public static void put(BlockPos p,String id){var client=Minecraft.getInstance();validateMaterial(id);var old=DATA.snapshot().get(position(p));DATA.put(new MapEdits.Edit(position(p),id,old==null?client.level.getBlockState(p).isAir():old.originallyAir()));}
    static void applyBatch(Map<MapEdits.Position,MapEdits.Edit> before,Map<MapEdits.Position,MapEdits.Edit> next){
        if(DATA.snapshot()!=before)throw new IllegalStateException("Edits changed while scanning. No batch changes were applied.");
        var grouped=new LinkedHashMap<RoomLayouts.Frame,Map<MapEdits.Position,Optional<String>>>();
        var origins=new HashMap<MapEdits.Position,Boolean>();var rotations=new HashMap<String,String>();
        var unknown=new LinkedHashMap<MapEdits.Position,Optional<String>>();boolean dungeon=inMapArea(Minecraft.getInstance());
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
        boolean dungeon=inMapArea(Minecraft.getInstance());var rotations=new HashMap<String,String>();var grouped=new LinkedHashMap<RoomLayouts.Frame,Map<MapEdits.Position,Optional<String>>>();
        for(var e:changes.entrySet()){var frame=DungeonRooms.at(e.getKey());if(frame==null){if(queueUnknown&&dungeon)pendingSaves.put(e.getKey(),e.getValue());continue;}grouped.computeIfAbsent(frame,f->new LinkedHashMap<>()).put(e.getKey(),e.getValue().map(id->rotations.computeIfAbsent(frame.turns()+":"+id,key->rotateMaterial(id,-frame.turns()))));}
        grouped.forEach((frame,edits)->{var origins=new HashMap<MapEdits.Position,Boolean>();for(var p:edits.keySet()){var edit=DATA.snapshot().get(p);if(edit!=null)origins.put(p,edit.originallyAir());}ROOMS.changeAll(frame,edits,origins);edits.keySet().forEach(pendingSaves::remove);lastRoom=frame.label();});
    }
    private static void savePending(){if(!pendingSaves.isEmpty())persist(new LinkedHashMap<>(pendingSaves),false);}
    public static RoomLayouts.Frame currentRoom(){var client=Minecraft.getInstance();if(client.player==null)return null;var camera=client.getCameraEntity();return DungeonRooms.at(position((camera==null?client.player:camera).blockPosition()));}
    public static String roomLabel(){var frame=currentRoom();return frame==null?(inMapArea(Minecraft.getInstance())?"Scanning / unrecognized room (session edits)":"Outside dungeon (session edits)"):frame.label();}
    private static void applyRooms(){if(busy())return;var frames=new HashSet<>(DungeonRooms.frames());var current=currentRoom();if(current!=null)frames.add(current);restorer.request(frames);restorer.tick();}
    static void restored(Map<MapEdits.Position,MapEdits.Edit> before,Map<MapEdits.Position,MapEdits.Edit> result,Set<MapEdits.Position> sections){
        if(DATA.snapshot()!=before)return;DATA.restore(result);visible=enabled&&allowed(Minecraft.getInstance())?DATA.snapshot():Map.of();
        if(level!=null){var packed=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();for(var p:sections)packed.add(SectionPos.asLong(p.x(),p.y(),p.z()));queueSectionUpdates(level,packed);}
    }
    public static void undo(){var before=DATA.snapshot();var changes=DATA.undoPositions();if(!DATA.undo())return;
        if(changes==null){remember(before);dirty(before.keySet());refresh();}else{var updates=new HashMap<MapEdits.Position,Optional<String>>();for(var p:changes){var edit=DATA.snapshot().get(p);updates.put(p,Optional.ofNullable(edit==null?null:edit.material()));}persist(updates,true);refresh(changes);}
    }
    public static void clear(){var before=DATA.snapshot();DATA.clear();remember(before);dirty(before.keySet());refresh();}
    public static void clearRoom(){var frame=currentRoom();if(frame==null)throw new IllegalArgumentException("No recognized room here");var before=DATA.snapshot();var copy=new LinkedHashMap<>(before);copy.keySet().removeIf(p->{var at=DungeonRooms.at(p);return at!=null&&at.key().equals(frame.key())&&at.origin().equals(frame.origin());});DATA.replace(copy);remember(before);ROOMS.clear(frame.key());dirty(before.keySet());refresh();}
    public static List<String> presets(){return List.copyOf(PRESETS.keySet());}
    public static MapEdits.Position anchor(){var client=Minecraft.getInstance();if(anchor==null&&client.player!=null){var p=client.player.blockPosition();anchor=new MapEdits.Position(Math.round((p.getX()+185)/32f)*32-185,0,Math.round((p.getZ()+185)/32f)*32-185);}return anchor;}
    public static void setAnchor(){var client=Minecraft.getInstance();if(client.player!=null){var p=client.player.blockPosition();anchor=new MapEdits.Position(p.getX(),0,p.getZ());notice("Map anchor: "+anchor);}}
    public record PresetInfo(String name,int blocks,String room){}
    public static List<PresetInfo> presetEntries(){return PRESETS.values().stream().map(p->new PresetInfo(p.name(),p.edits().size(),MapPresetData.roomLabel(p))).toList();}
    public static java.util.concurrent.CompletableFuture<Void> refreshPresetsInteractive(){
        if(busy())throw new IllegalStateException("Wait for the current map operation");
        var target=storage;if(target==null)throw new IllegalStateException("Map storage unavailable");
        var before=new TreeMap<>(PRESETS);
        var result=background(target::loadPresets,loaded->{
            if(PRESETS.size()!=before.size()||before.entrySet().stream().anyMatch(e->PRESETS.get(e.getKey())!=e.getValue()))throw new IllegalStateException("Presets changed while refreshing. Please refresh again.");
            PRESETS.clear();PRESETS.putAll(loaded);
        }).thenApply(value->(Void)null);operation=result;return result;
    }
    public static void savePreset(){
        if(anchor()==null)throw new IllegalStateException("Join a world first");
        var frame=currentRoom();MapEdits.Preset preset;
        if(frame==null)preset=DATA.preset(presetName,anchor(),fingerprint(anchor(),0));
        else{var edits=new ArrayList<MapEdits.Edit>();for(var edit:DATA.snapshot().values())if(MapPresetData.sameRoom(frame,DungeonRooms.at(edit.position())))edits.add(edit);preset=MapPresetData.capture(presetName,frame,edits,MapEditor::rotateMaterial);}
        if(preset.edits().isEmpty())throw new IllegalArgumentException("No edits to save in this room");
        try{if(storage==null)throw new java.io.IOException("Map storage unavailable");storage.savePreset(preset);}catch(java.io.IOException error){throw new IllegalStateException("Could not save map preset: "+error.getMessage(),error);}
        PRESETS.put(preset.name(),preset);save();notice("Saved map preset: "+preset.name());
    }
    /** Default load follows the detected room frame. Old fingerprints are matched against all four orientations. */
    public static void loadPresetAuto(){
        var preset=requiredPreset();if(!MapPresetData.roomKey(preset).isEmpty()){loadPreset(0);return;}
        if(anchor()==null)throw new IllegalStateException("Join a world first");
        var frame=currentRoom();var origins=new LinkedHashSet<MapEdits.Position>();if(frame!=null)origins.add(frame.origin());origins.add(anchor());
        if(!preset.fingerprint().isEmpty())for(var origin:origins)for(int turns=0;turns<4;turns++)if(preset.fingerprint().equals(fingerprint(origin,turns))){loadLegacyPreset(preset,origin,turns);return;}
        throw new IllegalArgumentException("Legacy preset: no matching block fingerprint here. Use Advanced only if you know its original anchor.");
    }
    private static MapEdits.Preset requiredPreset(){var preset=PRESETS.get(presetName);if(preset==null)throw new IllegalArgumentException("Select a saved preset first");return preset;}
    public static void loadPreset(int turns){
        var preset=requiredPreset();if(anchor()==null)throw new IllegalStateException("Join a world first");
        if(MapPresetData.roomKey(preset).isEmpty()){loadLegacyPreset(preset,anchor(),turns);return;}
        var frame=currentRoom();if(frame==null)throw new IllegalArgumentException("Wait for room recognition, or enable Force Dungeon when editing offline");
        var placed=MapPresetData.place(preset,frame,turns,MapEditor::rotateMaterial);
        var before=DATA.snapshot();var copy=new HashMap<>(before);
        copy.keySet().removeIf(p->MapPresetData.sameRoom(frame,DungeonRooms.at(p)));copy.putAll(placed);
        DATA.replace(copy);remember(before);dirty(before.keySet());enabled=true;refresh();flushRooms();
    }
    private static void loadLegacyPreset(MapEdits.Preset preset,MapEdits.Position origin,int turns){
        var before=DATA.snapshot();var copy=new HashMap<MapEdits.Position,MapEdits.Edit>();var materials=new HashMap<String,String>();
        for(var edit:preset.edits()){var pos=edit.position().rotate(turns).add(origin);copy.put(pos,new MapEdits.Edit(pos,materials.computeIfAbsent(edit.material(),id->rotateMaterial(id,turns)),edit.originallyAir()));}
        DATA.replace(copy);remember(before);dirty(before.keySet());enabled=true;refresh();flushRooms();
    }
    public static void renamePreset(String name){
        var old=requiredPreset();if(PRESETS.containsKey(name)&&!name.equals(old.name()))throw new IllegalArgumentException("A preset with that name already exists");
        var replacement=new MapEdits.Preset(name,old.edits(),old.fingerprint());
        try{if(storage==null)throw new java.io.IOException("Map storage unavailable");storage.renamePreset(old,name);}catch(java.io.IOException error){throw new IllegalStateException("Could not rename preset: "+error.getMessage(),error);}
        PRESETS.remove(old.name());PRESETS.put(name,replacement);presetName=name;
    }
    public static void deletePreset(){if(busy())throw new IllegalStateException("Wait for the current map operation");try{if(storage==null)throw new java.io.IOException("Map storage unavailable");storage.deletePreset(presetName);}catch(java.io.IOException error){throw new IllegalStateException("Could not delete map preset: "+error.getMessage(),error);}PRESETS.remove(presetName);}
    private static <T> java.util.concurrent.CompletableFuture<T> background(java.util.concurrent.Callable<T> prepare,java.util.function.Consumer<T> apply){
        var result=new java.util.concurrent.CompletableFuture<T>();
        var worker=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return prepare.call();}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}},MapWork.EXECUTOR);workerFence=worker;worker.whenComplete((value,error)->Minecraft.getInstance().execute(()->{
            if(error!=null)result.completeExceptionally(error);else try{apply.accept(value);result.complete(value);}catch(Exception e){result.completeExceptionally(e);}
        }));return result;
    }
    public static java.util.concurrent.CompletableFuture<Void> savePresetInteractive(){
        if(busy())throw new IllegalStateException("Wait for the current map operation");
        if(DATA.snapshot().size()<8192){savePreset();return java.util.concurrent.CompletableFuture.completedFuture(null);}
        var before=DATA.snapshot();var frame=currentRoom();var resolve=DungeonRooms.resolver();var origin=anchor();if(origin==null)throw new IllegalStateException("Join a world first");String name=presetName,hash=frame==null?fingerprint(origin,0):"";var target=storage;
        return background(()->{MapEdits.Preset preset;
            if(frame==null){var edits=new ArrayList<MapEdits.Edit>();for(var e:before.values())edits.add(new MapEdits.Edit(e.position().subtract(origin),e.material(),e.originallyAir()));preset=new MapEdits.Preset(name,edits,hash);}
            else{var edits=new ArrayList<MapEdits.Edit>();for(var e:before.values())if(MapPresetData.sameRoom(frame,resolve.apply(e.position())))edits.add(e);preset=MapPresetData.capture(name,frame,edits,MapEditor::rotateMaterial);}
            if(preset.edits().isEmpty())throw new IllegalArgumentException("No edits to save in this room");target.savePreset(preset);return preset;
        },preset->{PRESETS.put(preset.name(),preset);notice("Saved map preset: "+preset.name());}).thenApply(p->null);
    }
    private static java.util.concurrent.CompletableFuture<Void> changeInteractive(java.util.function.Supplier<Map<MapEdits.Position,MapEdits.Edit>> build,boolean undo){return changeInteractive(build,undo,()->true);}
    static java.util.concurrent.CompletableFuture<Void> applyBatchInteractive(Map<MapEdits.Position,MapEdits.Edit> before,java.util.function.Supplier<Map<MapEdits.Position,MapEdits.Edit>> build,java.util.function.BooleanSupplier valid){if(DATA.snapshot()!=before)throw new IllegalStateException("Edits changed while preparing the batch");return changeInteractive(build,false,valid);}
    static void cancelBatchOperation(java.util.concurrent.CompletableFuture<Void> future){if(operation==future)operation=null;}
    private static java.util.concurrent.CompletableFuture<Void> changeInteractive(java.util.function.Supplier<Map<MapEdits.Position,MapEdits.Edit>> build,boolean undo,java.util.function.BooleanSupplier valid){
        if(busy())throw new IllegalStateException("A map operation is already running");
        var client=Minecraft.getInstance();var world=client.level;var before=DATA.snapshot();var baseline=ROOMS.copy();var resolve=DungeonRooms.resolver();boolean dungeon=inMapArea(client);restorer.cancel();
        var result=background(()->MapChange.prepare(before,build.get(),baseline.copy(),resolve,MapEditor::rotateMaterial),prepared->{
            if(!valid.getAsBoolean()||client.level!=world||DATA.snapshot()!=before||!ROOMS.sameData(baseline))throw new IllegalStateException("Map edits changed while preparing. No changes were applied.");
            if(undo)DATA.finishUndo(prepared.edits());else DATA.replace(prepared.edits());ROOMS.adopt(prepared.rooms());if(dungeon)pendingSaves.putAll(prepared.unknown());dirty(before.keySet());enabled=true;refresh();saveRoomEdits();
        }).thenApply(p->(Void)null);operation=result;return result;
    }
    public static void report(java.util.concurrent.CompletableFuture<Void> future){future.whenComplete((v,error)->{if(error!=null){var cause=error;while(cause.getCause()!=null)cause=cause.getCause();notice(cause.getMessage()==null?"Map operation failed.":cause.getMessage());}});}
    public static java.util.concurrent.CompletableFuture<Void> loadPresetInteractive(Integer turns){
        var preset=requiredPreset();if(preset.edits().size()<8192&&DATA.snapshot().size()<8192){if(turns==null)loadPresetAuto();else loadPreset(turns);return java.util.concurrent.CompletableFuture.completedFuture(null);}
        var before=DATA.snapshot();var frame=currentRoom();var resolve=DungeonRooms.resolver();boolean scoped=!MapPresetData.roomKey(preset).isEmpty();var origin=anchor();if(origin==null)throw new IllegalStateException("Join a world first");int selected=turns==null?0:turns;
        if(scoped&&(frame==null||!frame.key().equals(MapPresetData.roomKey(preset))))throw new IllegalArgumentException("Enter "+MapPresetData.roomLabel(preset)+" first");
        if(!scoped&&turns==null){boolean matched=false;for(int i=0;i<4;i++)if(!preset.fingerprint().isEmpty()&&preset.fingerprint().equals(fingerprint(origin,i))){selected=i;matched=true;break;}if(!matched)throw new IllegalArgumentException("Legacy fingerprint not found. Use Advanced with its original anchor.");}
        int rotation=selected;return changeInteractive(()->{
            var copy=new HashMap<MapEdits.Position,MapEdits.Edit>();if(scoped){for(var e:before.entrySet())if(!MapPresetData.sameRoom(frame,resolve.apply(e.getKey())))copy.put(e.getKey(),e.getValue());copy.putAll(MapPresetData.place(preset,frame,rotation,MapEditor::rotateMaterial));}
            else{var materials=new HashMap<String,String>();for(var edit:preset.edits()){var p=edit.position().rotate(rotation).add(origin);copy.put(p,new MapEdits.Edit(p,materials.computeIfAbsent(edit.material(),id->rotateMaterial(id,rotation)),edit.originallyAir()));}}return MapEdits.own(copy);
        },false);
    }
    public static void undoInteractive(){var target=DATA.undoTarget();if(target==null)return;var changes=DATA.undoPositions();if((changes!=null&&changes.size()<=1024)||DATA.snapshot().size()<8192){undo();return;}report(changeInteractive(target,true));}
    public static void clearInteractive(){if(DATA.snapshot().size()<8192){clear();return;}report(changeInteractive(Map::of,false));}
    public static void clearRoomInteractive(){if(DATA.snapshot().size()<8192){clearRoom();return;}var frame=currentRoom();if(frame==null)throw new IllegalArgumentException("No recognized room here");var before=DATA.snapshot();var resolve=DungeonRooms.resolver();report(changeInteractive(()->{var copy=new HashMap<MapEdits.Position,MapEdits.Edit>();for(var e:before.entrySet())if(!MapPresetData.sameRoom(frame,resolve.apply(e.getKey())))copy.put(e.getKey(),e.getValue());return MapEdits.own(copy);},false));}
    public static java.util.concurrent.CompletableFuture<Void> renamePresetInteractive(String name){
        if(busy())throw new IllegalStateException("Wait for the current map operation");
        var old=requiredPreset();if(old.edits().size()<8192){renamePreset(name);return java.util.concurrent.CompletableFuture.completedFuture(null);}
        if(PRESETS.containsKey(name)&&!name.equals(old.name()))throw new IllegalArgumentException("A preset with that name already exists");var replacement=new MapEdits.Preset(name,old.edits(),old.fingerprint());var target=storage;
        return background(()->{target.renamePreset(old,name);return replacement;},preset->{PRESETS.remove(old.name());PRESETS.put(name,preset);presetName=name;}).thenApply(p->null);
    }
    public static java.util.concurrent.CompletableFuture<String> importFmeInteractive(FmeImport.Plan plan,boolean replace){
        if(DATA.snapshot().size()<8192&&plan.blocks()<8192)try{return java.util.concurrent.CompletableFuture.completedFuture(importFme(plan,replace));}catch(java.io.IOException e){return java.util.concurrent.CompletableFuture.failedFuture(e);}
        if(busy())throw new IllegalStateException("Wait for the current map operation");
        var before=DATA.snapshot();var baseline=ROOMS.copy();var resolve=DungeonRooms.resolver();var target=roomsFile;var settings=file;var pendingSave=roomSaves.completion();var world=Minecraft.getInstance().level;
        var candidate=baseline.copy();var kept=new java.util.concurrent.atomic.AtomicReference<Map<MapEdits.Position,MapEdits.Edit>>();
        var result=background(()->{
            pendingSave.join();candidate.save(target);var backup=settings.getParent().resolve("backups/fme-imports").resolve(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")));Files.createDirectories(backup);if(Files.isRegularFile(settings))Files.copy(settings,backup.resolve(settings.getFileName()));
            candidate.importTo(target,backup.resolve("rooms"),plan.layouts(),replace);
            if(replace){var copy=new HashMap<MapEdits.Position,MapEdits.Edit>();for(var e:before.entrySet()){var frame=resolve.apply(e.getKey());if(frame==null||!plan.layouts().containsKey(frame.key()))copy.put(e.getKey(),e.getValue());}kept.set(MapEdits.own(copy));}else kept.set(before);
            return "Imported FME config. Backup: "+backup;
        },message->{
            if(!ROOMS.sameData(baseline))throw new IllegalStateException("Room layouts changed during import");ROOMS.adopt(candidate);
            if(Minecraft.getInstance().level==world&&DATA.snapshot()==before)DATA.replace(kept.get());for(var id:plan.favorites())if(favorites.size()<256)favorites.add(id);dirty(before.keySet());restorer.reset();DungeonRooms.reset();nextScan=0;refresh();
        });pendingImport=workerFence;result.whenComplete((value,error)->pendingImport=null);operation=result.thenApply(v->null);return result;
    }
    private static String fingerprint(MapEdits.Position origin,int turns){
        if(level==null)return "";return RoomFingerprint.compute(origin,turns,p->{var at=blockPos(p);if(!loaded(at))return null;var s=level.getBlockState(at);return s.isAir()?"minecraft:air":BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();});
    }
    private static final Set<String> appliedRooms=new HashSet<>();
    private static int presetScanCursor;
    private static boolean hasLegacyPresets(){
        for(var preset:PRESETS.values()){String hash=preset.fingerprint();if(hash.length()!=64)continue;boolean valid=true;for(int i=0;i<64;i++){char c=hash.charAt(i);if(!(c>='0'&&c<='9'||c>='a'&&c<='f')){valid=false;break;}}if(valid)return true;}return false;
    }
    private static void scanRooms(Minecraft client){if(!hasLegacyPresets()||!inDungeon(client))return;
        int cell=Math.floorMod(presetScanCursor++,36),x=-185+(cell/6)*32,z=-185+(cell%6)*32;{
            var origin=new MapEdits.Position(x,0,z);String key=x+":"+z;if(appliedRooms.contains(key))return;
            for(int turns=0;turns<4;turns++){String hash=fingerprint(origin,turns);if(hash.isEmpty())break;
                var preset=PRESETS.values().stream().filter(p->!p.fingerprint().isEmpty()&&p.fingerprint().equals(hash)).findFirst();if(preset.isEmpty())continue;
                var before=DATA.snapshot();var selected=preset.get();int rotation=turns;
                report(changeInteractive(()->{var copy=new HashMap<>(before);copy.putAll(selected.place(origin,rotation));return MapEdits.own(copy);},false));appliedRooms.add(key);return;
            }
        }
    }
    public static void mark(boolean end){var hit=target();if(hit==null)throw new IllegalStateException("Look at a block");if(end)second=hit.getBlockPos();else first=hit.getBlockPos();notice((end?"Second":"First")+" corner: "+hit.getBlockPos().toShortString());}
    public static void fill(){
        if(first==null||second==null)throw new IllegalStateException("Set pos1 and pos2 first");validateMaterial(material);
        MapRegionFill.start(first,second,material);
    }
    public static String status(){var client=Minecraft.getInstance();var hit=client.level==null?null:target();return "Map Editor: "+(enabled?"ON":"OFF")+"; Edit Mode: "+(editing?"ON":"OFF")+"; dungeon: "+(forceDungeon?"forced":automaticDungeon(client)?"detected":"not detected")+"; Kuudra: "+(forceKuudra?"forced":automaticKuudra(client)?"detected":"not detected")+"; floor: "+(floor().isEmpty()?"unknown":floor())+"; room: "+roomLabel()+"; editing: "+(allowed(client)?"allowed":"blocked")+"; target: "+(hit==null?"none within 6 blocks":hit.getBlockPos().toShortString());}
    public static void notice(String s){MessageUtils.sendMessage(s);}
}
