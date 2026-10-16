// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.features.animations.PresenceWorld;
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

/** Local map editor. Legit edits are consumed only by renderers; authoritative states stay untouched. */
public final class MapEditor {
    public static boolean enabled,editing,dungeonOnly=true,autoRooms=true;
    public static MapEdits.Mode mode=MapEdits.Mode.LEGIT;
    public static String material="minecraft:stone",presetName="My room";
    public static final MapEdits DATA=new MapEdits();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String,MapEdits.Preset> PRESETS=new TreeMap<>();
    private static final Map<BlockPos,BlockState> originals=new HashMap<>(),fakeStates=new HashMap<>();
    private static volatile Map<MapEdits.Position,MapEdits.Edit> visible=Map.of();
    private static ClientLevel level;private static Path file;private static boolean applying;private static long nextScan;
    private static MapEdits.Position anchor;private static BlockPos first,second;
    private MapEditor(){}
    public static MapEdits.Position position(BlockPos p){return new MapEdits.Position(p.getX(),p.getY(),p.getZ());}
    public static BlockPos blockPos(MapEdits.Position p){return new BlockPos(p.x(),p.y(),p.z());}
    public static void init(){file=CosmeticsConfigDir.getCosmeticsConfigDir().resolve("map-editor.json");if(Files.isRegularFile(file))try{
        var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        enabled=root.has("enabled")&&root.get("enabled").getAsBoolean();dungeonOnly=!root.has("dungeonOnly")||root.get("dungeonOnly").getAsBoolean();autoRooms=!root.has("autoRooms")||root.get("autoRooms").getAsBoolean();
        if(root.has("presets"))for(var item:root.getAsJsonArray("presets")){try{var p=GSON.fromJson(item,MapEdits.Preset.class);if(p!=null)PRESETS.put(p.name(),p);}catch(RuntimeException ignored){}}
    }catch(Exception error){notice("Could not load map presets: "+error.getMessage());}
        GhostBlocks.init();
    }
    public static void save(){if(file==null)return;try{var root=new JsonObject();root.addProperty("enabled",enabled);root.addProperty("dungeonOnly",dungeonOnly);root.addProperty("autoRooms",autoRooms);root.add("presets",GSON.toJsonTree(PRESETS.values()));Files.createDirectories(file.getParent());var temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(root));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}catch(Exception error){notice("Could not save map presets: "+error.getMessage());}}
    public static boolean allowed(Minecraft client){return client.level!=null&&client.player!=null&&(!dungeonOnly||client.hasSingleplayerServer()||PresenceWorld.isHypixel(client)&&inDungeon(client));}
    private static boolean inDungeon(Minecraft client){
        var board=client.level.getScoreboard();for(var objective:board.getObjectives()){
            if(objective.getDisplayName().getString().toLowerCase(Locale.ROOT).contains("catacombs"))return true;
            for(var row:board.listPlayerScores(objective)){String s=net.minecraft.world.scores.PlayerTeam.formatNameForTeam(board.getPlayersTeam(row.owner()),row.ownerName()).getString().toLowerCase(Locale.ROOT);if(s.contains("catacombs")||s.contains("dungeon cleared"))return true;}
        }return false;
    }
    public static void tick(Minecraft client){
        if(level!=client.level){restore();level=client.level;visible=Map.of();DATA.reset();appliedRooms.clear();anchor=null;first=second=null;editing=false;nextScan=0;}
        boolean active=enabled&&allowed(client);
        if(!active){if(!visible.isEmpty()||!originals.isEmpty()){restore();visible=Map.of();dirty(DATA.snapshot().keySet());}return;}
        if(autoRooms&&!editing&&System.currentTimeMillis()>=nextScan){nextScan=System.currentTimeMillis()+2000;scanRooms(client);}
        if(mode==MapEdits.Mode.CHEATING)applyCheating();else if(!originals.isEmpty()){restore();dirty(DATA.snapshot().keySet());}
        visible=DATA.snapshot();
    }
    public static void refresh(){restore();visible=enabled&&allowed(Minecraft.getInstance())?DATA.snapshot():Map.of();dirty(DATA.snapshot().keySet());save();}
    private static void dirty(Collection<MapEdits.Position> positions){if(level==null)return;var sections=new HashSet<MapEdits.Position>();for(var p:positions)sections.add(new MapEdits.Position(p.x()>>4,p.y()>>4,p.z()>>4));for(var p:sections)level.setSectionDirtyWithNeighbors(p.x(),p.y(),p.z());}
    private static void restore(){if(level!=null){applying=true;try{originals.forEach((p,state)->{if(level.hasChunkAt(p)&&Objects.equals(level.getBlockState(p),fakeStates.get(p)))level.setBlock(p,state,2);});}finally{applying=false;}}originals.clear();fakeStates.clear();}
    private static void applyCheating(){if(level==null)return;applying=true;try{for(var e:DATA.snapshot().values()){
        var p=blockPos(e.position());if(!level.hasChunkAt(p))continue;var target=state(e.material());if(target==null||target.hasBlockEntity()||level.getBlockState(p).hasBlockEntity())continue;
        originals.putIfAbsent(p,level.getBlockState(p));fakeStates.put(p,target);if(level.getBlockState(p)!=target)level.setBlock(p,target,2);
    }}finally{applying=false;}}
    public static void serverUpdate(BlockPos p,BlockState state){if(!applying&&originals.containsKey(p))originals.put(p.immutable(),state);}
    public static Map<MapEdits.Position,MapEdits.Edit> visible(){return visible;}
    public static BlockState state(String id){var key=Identifier.tryParse(id);return key==null?null:BuiltInRegistries.BLOCK.getOptional(key).map(Block::defaultBlockState).orElse(null);}
    public static BlockStateModel wrap(BlockStateModel model,BlockPos p){
        if(model instanceof MaterialModel)return model;
        if(mode!=MapEdits.Mode.LEGIT)return model;var edit=visible.get(position(p));if(edit==null)return model;
        var target=state(edit.material());if(target==null)return model;
        return new MaterialModel(model,Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(target),target.isAir());
    }
    public static BlockHitResult target(){
        var client=Minecraft.getInstance();if(client.player==null)return null;
        var start=client.player.getEyePosition();var end=start.add(client.player.getLookAngle().scale(6));
        BlockHitResult best=client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK?hit:null;double distance=best==null?36:start.distanceToSqr(best.getLocation());
        for(var e:visible.values())if(e.ghost(client.level.getBlockState(blockPos(e.position())).isAir())){
            var box=new AABB(blockPos(e.position()));var point=box.clip(start,end);if(point.isPresent()&&start.distanceToSqr(point.get())<distance){var at=point.get();var d=Direction.getApproximateNearest(start.x-at.x,start.y-at.y,start.z-at.z);best=new BlockHitResult(at,d,blockPos(e.position()),false);distance=start.distanceToSqr(at);}
        }return best;
    }
    public static boolean click(int button){var client=Minecraft.getInstance();if(!editing||!enabled||!allowed(client))return false;var hit=target();if(hit==null)return true;
        if(button==2){material=BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(hit.getBlockPos()).getBlock()).toString();notice("Map material: "+material);return true;}
        var p=button==1&&client.player.isShiftKeyDown()?hit.getBlockPos().relative(hit.getDirection()):hit.getBlockPos();
        MapCommands.run(()->{restore();if(button==0)DATA.remove(position(p));else put(p,material);refresh();});return true;
    }
    private static void validateMaterial(String id){var target=state(id);if(target==null)throw new IllegalArgumentException("Unknown block: "+id);if(target.hasBlockEntity()||!target.getFluidState().isEmpty())throw new IllegalArgumentException("Choose a normal block material (no fluids or block entities)");}
    public static void put(BlockPos p,String id){var client=Minecraft.getInstance();validateMaterial(id);if(client.level.getBlockState(p).hasBlockEntity())throw new IllegalArgumentException("Block entities cannot be edited");var old=DATA.snapshot().get(position(p));DATA.put(new MapEdits.Edit(position(p),id,old==null?client.level.getBlockState(p).isAir():old.originallyAir()));}
    public static void undo(){restore();var old=DATA.snapshot().keySet();DATA.undo();dirty(old);refresh();}
    public static void clear(){restore();var old=DATA.snapshot().keySet();DATA.clear();dirty(old);refresh();}
    public static List<String> presets(){return List.copyOf(PRESETS.keySet());}
    public static MapEdits.Position anchor(){var client=Minecraft.getInstance();if(anchor==null&&client.player!=null){var p=client.player.blockPosition();anchor=new MapEdits.Position(Math.round((p.getX()+185)/32f)*32-185,0,Math.round((p.getZ()+185)/32f)*32-185);}return anchor;}
    public static void setAnchor(){var client=Minecraft.getInstance();if(client.player!=null){var p=client.player.blockPosition();anchor=new MapEdits.Position(p.getX(),0,p.getZ());notice("Map anchor: "+anchor);}}
    public static void savePreset(){if(anchor()==null)throw new IllegalStateException("Join a world first");restore();var preset=DATA.preset(presetName,anchor(),fingerprint(anchor(),0));PRESETS.put(preset.name(),preset);save();notice("Saved map preset: "+preset.name());}
    public static void loadPreset(int turns){var preset=PRESETS.get(presetName);if(preset==null)throw new IllegalArgumentException("Preset not found");if(anchor()==null)throw new IllegalStateException("Join a world first");restore();var old=DATA.snapshot().keySet();DATA.replace(preset.place(anchor(),turns));dirty(old);refresh();}
    public static void deletePreset(){PRESETS.remove(presetName);save();}
    private static String fingerprint(MapEdits.Position origin,int turns){
        if(level==null)return "";return RoomFingerprint.compute(origin,turns,p->{var at=blockPos(p);if(!level.hasChunkAt(at))return null;var s=originals.getOrDefault(at,level.getBlockState(at));return s.isAir()?"minecraft:air":BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();});
    }
    private static final Set<String> appliedRooms=new HashSet<>();
    private static void scanRooms(Minecraft client){if(PRESETS.isEmpty()||!PresenceWorld.isHypixel(client))return;
        var player=client.player.blockPosition();for(int x=-185;x<=-25;x+=32)for(int z=-185;z<=-25;z+=32){
            if(Math.abs(x-player.getX())>64||Math.abs(z-player.getZ())>64)continue;
            var origin=new MapEdits.Position(x,0,z);String key=x+":"+z;if(appliedRooms.contains(key))continue;
            for(int turns=0;turns<4;turns++){String hash=fingerprint(origin,turns);if(hash.isEmpty())break;
                var preset=PRESETS.values().stream().filter(p->!p.fingerprint().isEmpty()&&p.fingerprint().equals(hash)).findFirst();if(preset.isEmpty())continue;
                var copy=new LinkedHashMap<>(DATA.snapshot());copy.putAll(preset.get().place(origin,turns));if(copy.size()<=MapEdits.LIMIT){DATA.replace(copy);appliedRooms.add(key);refresh();}break;
            }
        }
    }
    public static void mark(boolean end){var hit=target();if(hit==null)throw new IllegalStateException("Look at a block");if(end)second=hit.getBlockPos();else first=hit.getBlockPos();notice((end?"Second":"First")+" corner: "+hit.getBlockPos().toShortString());}
    public static void fill(){
        if(first==null||second==null)throw new IllegalStateException("Set pos1 and pos2 first");validateMaterial(material);
        long count=(long)(Math.abs(first.getX()-second.getX())+1)*(Math.abs(first.getY()-second.getY())+1)*(Math.abs(first.getZ()-second.getZ())+1);
        if(count>MapEdits.LIMIT)throw new IllegalArgumentException("Selection exceeds "+MapEdits.LIMIT+" blocks");
        restore();var copy=new LinkedHashMap<>(DATA.snapshot());var client=Minecraft.getInstance();
        for(var p:BlockPos.betweenClosed(first,second)){
            var state=client.level.getBlockState(p);if(state.hasBlockEntity())continue;var old=copy.get(position(p));
            copy.put(position(p),new MapEdits.Edit(position(p),material,old==null?state.isAir():old.originallyAir()));
        }DATA.replace(copy);refresh();
    }
    public static void notice(String s){MessageUtils.sendMessage(s);}
}
