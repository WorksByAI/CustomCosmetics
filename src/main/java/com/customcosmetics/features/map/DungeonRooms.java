// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.datafix.fixes.BlockStateData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Named room discovery. Only reads loaded, authoritative blocks; never asks the server for chunks. */
public final class DungeonRooms {
    private record Definition(String name,int base){}
    private record Tile(int x,int z,int core,Definition definition){}
    private static final Map<Integer,Definition> definitions=new HashMap<>();
    private static final Map<String,Integer> legacy=new HashMap<>();
    private static final Map<String,Tile> tiles=new HashMap<>();
    private static final Map<String,RoomLayouts.Frame> frames=new LinkedHashMap<>();
    public static final String KUUDRA_KEY="area:kuudra";
    private static final RoomLayouts.Frame KUUDRA_FRAME=new RoomLayouts.Frame(KUUDRA_KEY,"Kuudra's Hollow",new MapEdits.Position(0,0,0),0);
    private static Object contextLevel;private static boolean dungeon,offlineBoss,kuudra;private static String floor="";private static int scanCursor;
    private DungeonRooms(){}
    public static void init(){
        // Vanilla's flattening table maps legacy block metadata to modern names (no mod registry IDs).
        for(int id=0;id<256;id++)for(int meta=0;meta<16;meta++){
            String name=LegacyMaterials.modernName(BlockStateData.getTag((id<<4)|meta).get("Name").asString(""));
            if(!name.isBlank()&&!name.equals("minecraft:air"))legacy.putIfAbsent(name,id);
        }
        legacy.put("minecraft:air",0);legacy.put("minecraft:cave_air",0);legacy.put("minecraft:void_air",0);
        try(var input=DungeonRooms.class.getResourceAsStream("/assets/customcosmetics/map/rooms.json")){
            if(input==null)throw new IOException("Missing room catalog");var rows=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonArray();
            for(var row:rows){var room=row.getAsJsonObject();var cores=room.getAsJsonArray("cores");var def=new Definition(room.get("name").getAsString(),cores.get(0).getAsInt());for(var core:cores)definitions.put(core.getAsInt(),def);}
        }catch(Exception error){throw new IllegalStateException("Could not read FunnyMap room catalog",error);}
    }
    public static void reset(){tiles.clear();frames.clear();scanCursor=0;contextLevel=null;dungeon=false;offlineBoss=false;kuudra=false;floor="";}
    public static void context(Minecraft client){boolean nextOffline=MapEditor.offlineBoss(client),nextKuudra=MapEditor.inKuudra(client);if(contextLevel!=client.level||offlineBoss!=nextOffline||kuudra!=nextKuudra){tiles.clear();frames.clear();}offlineBoss=nextOffline;kuudra=nextKuudra;contextLevel=client.level;dungeon=MapEditor.inMapArea(client);floor=dungeon&&!kuudra?MapEditor.floor():"";}
    private static String cell(int x,int z){return x+":"+z;}
    private static Collection<MapEdits.Position> scanCenters(){
        var centers=new LinkedHashSet<MapEdits.Position>();for(int x=-185;x<=-25;x+=32)for(int z=-185;z<=-25;z+=32)centers.add(new MapEdits.Position(x,0,z));
        // Recognition must use the same block scan with automatic and forced dungeon context.
        // Only known catalog/imported cores are accepted outside the legacy six-by-six grid.
        var client=Minecraft.getInstance();var viewer=client.getCameraEntity()==null?client.player:client.getCameraEntity();var p=viewer.blockPosition();int x=Math.round((p.getX()+185)/32f)*32-185,z=Math.round((p.getZ()+185)/32f)*32-185;for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)centers.add(new MapEdits.Position(x+dx*32,0,z+dz*32));
        return centers;
    }
    private static Integer core(int x,int z){
        var client=Minecraft.getInstance();if(!MapEditor.loaded(new BlockPos(x,70,z)))return null;
        try{return RoomCores.hash(y->{var state=MapEditor.realState(new BlockPos(x,y,z));String name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();int id=legacy.getOrDefault(name,-1);
            if(state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock){boolean doubled=state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)==net.minecraft.world.level.block.state.properties.SlabType.DOUBLE;id=switch(id){case 43,44->doubled?43:44;case 125,126->doubled?125:126;case 181,182->doubled?181:182;case 204,205->doubled?204:205;default->id;};}
            if(name.equals("minecraft:water"))return state.getFluidState().isSource()?9:8;if(name.equals("minecraft:lava"))return state.getFluidState().isSource()?11:10;return id;
        });}catch(IllegalArgumentException error){return null;}
    }
    public static void scan(){
        context(Minecraft.getInstance());if(dungeon&&!offlineBoss&&!kuudra)scan(scanCenters());
    }
    /** Visit every loaded room from dungeon entry, distributing discovery across ticks. */
    public static void scanStep(){
        var client=Minecraft.getInstance();context(client);if(!dungeon||offlineBoss||kuudra)return;
        var centers=new ArrayList<>(scanCenters());long end=System.nanoTime()+2_000_000;
        for(int visited=0;visited<centers.size();visited++){
            var center=centers.get(Math.floorMod(scanCursor++,centers.size()));
            if(MapEditor.loaded(new BlockPos(center.x(),70,center.z())))scan(List.of(center));
            if(System.nanoTime()>=end)return;
        }
    }
    private static void scan(Collection<MapEdits.Position> centers){
        var client=Minecraft.getInstance();if(!dungeon||offlineBoss||kuudra)return;
        MapEditor.ROOMS.anchors().forEach((key,anchor)->{if(key.startsWith("room:"))definitions.put(anchor.baseCore(),new Definition(key.substring(5),anchor.baseCore()));});
        for(var center:centers){int x=center.x(),z=center.z();String cell=cell(x,z);if(tiles.containsKey(cell))continue;Integer core=core(x,z);if(core!=null&&definitions.containsKey(core))tiles.put(cell,new Tile(x,z,core,definitions.get(core)));}
        for(var tile:tiles.values()){
            if(frames.containsKey(cell(tile.x,tile.z)))continue;
            // A multi-tile room uses the catalog's first core as the common origin, as in FunnyMap.
            var component=new ArrayList<Tile>();var todo=new ArrayDeque<Tile>();todo.add(tile);var seen=new HashSet<String>();
            while(!todo.isEmpty()){var at=todo.remove();if(!seen.add(cell(at.x,at.z)))continue;component.add(at);for(var step:new int[][]{{32,0},{-32,0},{0,32},{0,-32}}){var next=tiles.get(cell(at.x+step[0],at.z+step[1]));if(next!=null&&next.definition.name.equals(tile.definition.name))todo.add(next);}}
            var legacy=MapEditor.ROOMS.anchor("room:"+tile.definition.name);int baseCore=legacy==null?tile.definition.base:legacy.baseCore();
            var anchors=component.stream().filter(t->t.core==baseCore).toList();if(anchors.size()!=1)continue;var base=anchors.getFirst();
            int turns;
            if(legacy!=null){turns=-1;for(int i=0;i<4;i++){var offset=new MapEdits.Position(0,0,-legacy.distance()).rotate(i);Integer hash=core(base.x+offset.x(),base.z+offset.z());if(hash!=null&&hash==legacy.rotationCore()){turns=i;break;}}if(turns<0)continue;}
            else turns=RoomCores.direction(distance->{int[] result=new int[4];for(int i=0;i<4;i++){var offset=new MapEdits.Position(0,0,-distance).rotate(i);Integer hash=core(base.x+offset.x(),base.z+offset.z());result[i]=hash==null?0:hash;}return result;});
            var frame=new RoomLayouts.Frame("room:"+tile.definition.name,tile.definition.name,new MapEdits.Position(base.x,0,base.z),turns);
            for(var at:component)frames.put(cell(at.x,at.z),frame);
        }
        // New or changed rooms absent from the legacy catalog still get rotation-aware local layouts.
        for(var center:centers){int x=center.x(),z=center.z();if(x< -185||x> -25||z< -185||z> -25)continue;String key=cell(x,z);if(frames.containsKey(key)||tiles.containsKey(key))continue;var origin=new MapEdits.Position(x,0,z);String best=null;int turn=0;
            for(int i=0;i<4;i++){String hash=RoomFingerprint.compute(origin,i,p->{var at=MapEditor.blockPos(p);if(!MapEditor.loaded(at))return null;return BuiltInRegistries.BLOCK.getKey(MapEditor.realState(at).getBlock()).toString();});if(hash.isEmpty()){best=null;break;}if(best==null||hash.compareTo(best)<0){best=hash;turn=i;}}
            if(best!=null)frames.put(key,new RoomLayouts.Frame("fingerprint:"+best,"Room "+best.substring(0,8),origin,turn));
        }
    }
    public static RoomLayouts.Frame at(MapEdits.Position p){
        var client=Minecraft.getInstance();if(client.level!=contextLevel||!dungeon)return null;
        if(kuudra)return KUUDRA_FRAME;
        // Explicit offline editing defaults to the shared boss layout, including coordinates in the room grid.
        if(offlineBoss)return new RoomLayouts.Frame("floor:F7+M7","F7 / M7 Boss (offline forced)",new MapEdits.Position(0,0,0),0);
        int x=Math.round((p.x()+185)/32f)*32-185,z=Math.round((p.z()+185)/32f)*32-185;
        var frame=frames.get(cell(x,z));if(frame!=null)return frame;
        if(x>=-185&&x<=-25&&z>=-185&&z<=-25)return null;
        return floor.isEmpty()?null:new RoomLayouts.Frame("floor:"+floor,(floor.equals("F7")||floor.equals("M7")?"F7 / M7 Boss":"Floor "+floor)+" (outside rooms)",new MapEdits.Position(0,0,0),0);
    }
    public static Collection<RoomLayouts.Frame> frames(){
        if(!dungeon)return List.of();var result=new HashSet<>(frames.values());
        if(kuudra)result.add(KUUDRA_FRAME);
        else if(offlineBoss||!floor.isEmpty())result.add(new RoomLayouts.Frame(offlineBoss?"floor:F7+M7":"floor:"+floor,"Boss layout",new MapEdits.Position(0,0,0),0));
        return Set.copyOf(result);
    }
    /** Immutable coordinate resolver for background map work; never reads a world or client off-thread. */
    static java.util.function.Function<MapEdits.Position,RoomLayouts.Frame> resolver(){
        var known=Map.copyOf(frames);boolean active=dungeon,boss=offlineBoss,isKuudra=kuudra;String savedFloor=floor;
        return p->{if(!active)return null;if(isKuudra)return KUUDRA_FRAME;if(boss)return new RoomLayouts.Frame("floor:F7+M7","F7 / M7 Boss",new MapEdits.Position(0,0,0),0);
            int x=Math.round((p.x()+185)/32f)*32-185,z=Math.round((p.z()+185)/32f)*32-185;var frame=known.get(cell(x,z));if(frame!=null)return frame;
            if(x>=-185&&x<=-25&&z>=-185&&z<=-25||savedFloor.isEmpty())return null;
            return new RoomLayouts.Frame("floor:"+savedFloor,"Floor "+savedFloor,new MapEdits.Position(0,0,0),0);};
    }
}
