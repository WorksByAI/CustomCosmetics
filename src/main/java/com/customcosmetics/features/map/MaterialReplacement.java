// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** Incremental, client-thread scanning of already loaded chunks. A completed batch is one undo step. */
public final class MaterialReplacement {
    public enum Scope { ROOM, SELECTION }
    private record Section(LevelChunkSection data,int x,int y,int z,boolean[] columns){}
    private static Job job;
    private static String status="Choose the original block and its replacement material.";
    private MaterialReplacement(){}
    public static boolean busy(){return job!=null;}
    public static String status(){return status;}
    public static void cancel(){if(job!=null){job=null;status="Replacement cancelled. No changes were applied.";}}
    public static void start(String from,String to,Scope scope){
        if(job!=null)throw new IllegalStateException("A replacement is already running");var client=Minecraft.getInstance();
        if(!MapEditor.allowed(client))throw new IllegalStateException("Join an allowed world before replacing materials");
        var source=MapEditor.state(from);var target=MapEditor.state(to);if(source==null||source.isAir())throw new IllegalArgumentException("Choose a valid non-air source block");if(target==null)throw new IllegalArgumentException("Choose a valid replacement material");
        String id=BuiltInRegistries.BLOCK.getKey(source.getBlock()).toString();String material=to.contains(":")?to:"minecraft:"+to;
        var bounds=scope==Scope.SELECTION?MapEditor.selection():null;if(scope==Scope.SELECTION&&bounds==null)throw new IllegalStateException("Set both corners with /customcosmetics map pos1 and pos2 first");
        DungeonRooms.context(client);if(scope==Scope.ROOM)DungeonRooms.scan();var room=scope==Scope.ROOM?MapEditor.currentRoom():null;if(scope==Scope.ROOM&&room==null)throw new IllegalStateException("No recognized room here. Use a pos1 / pos2 selection instead.");
        if(id.equals(material)&&MapEditor.DATA.snapshot().isEmpty()){status="Source and replacement are identical. No changes were applied.";return;}
        job=new Job(client,source.getBlock(),id,material,room,bounds);status="Scanning loaded blocks...";
    }
    public static void tick(Minecraft client){
        if(job==null)return;try{job.step(client);}catch(RuntimeException error){job=null;status=error.getMessage()==null?"Replacement failed. No changes were applied.":error.getMessage();MapEditor.notice(status);}
    }
    private static final class Job {
        final ClientLevel world;final Block source;final String sourceId,target;final RoomLayouts.Frame room;final MaterialReplacementPlan.Bounds bounds;
        final Map<MapEdits.Position,MapEdits.Edit> before;final MaterialReplacementPlan plan;
        final List<ChunkPos> chunks;final Deque<Section> sections=new ArrayDeque<>();int cursor;
        Job(Minecraft client,Block source,String sourceId,String target,RoomLayouts.Frame room,MaterialReplacementPlan.Bounds bounds){
            world=client.level;this.source=source;this.sourceId=sourceId;this.target=target;this.room=room;this.bounds=bounds;before=MapEditor.DATA.snapshot();plan=new MaterialReplacementPlan(before,sourceId,target);
            var candidates=new LinkedHashSet<ChunkPos>();int radius=Math.clamp(client.options.getEffectiveRenderDistance()+3,2,67);
            for(var entity:List.of(client.player,client.getCameraEntity()==null?client.player:client.getCameraEntity())){var center=entity.chunkPosition();for(int x=center.x()-radius;x<=center.x()+radius;x++)for(int z=center.z()-radius;z<=center.z()+radius;z++)if(bounds==null||((long)x*16<=bounds.max().x()&&(long)x*16+15>=bounds.min().x()&&(long)z*16<=bounds.max().z()&&(long)z*16+15>=bounds.min().z()))candidates.add(new ChunkPos(x,z));}
            chunks=List.copyOf(candidates);
        }
        void step(Minecraft client){
            if(client.level!=world||!MapEditor.allowed(client))throw new IllegalStateException("Replacement cancelled because the world changed. No changes were applied.");
            if(MapEditor.DATA.snapshot()!=before)throw new IllegalStateException("Replacement cancelled because edits changed. No batch changes were applied.");
            if(room!=null&&!sameRoom(room,MapEditor.currentRoom()))throw new IllegalStateException("Replacement cancelled because you left the room. No changes were applied.");
            long deadline=System.nanoTime()+2_000_000L;int visited=0;
            do{if(!sections.isEmpty())scan(sections.removeFirst());else if(cursor<chunks.size())load(chunks.get(cursor++));else{finish();return;}}while(++visited<512&&System.nanoTime()<deadline);
            status="Scanning loaded blocks: "+(chunks.isEmpty()?100:cursor*100/chunks.size())+"% · "+plan.matched()+" matches";
        }
        void load(ChunkPos p){
            var chunk=world.getChunkSource().getChunk(p.x(),p.z(),ChunkStatus.FULL,false);if(chunk==null)return;
            boolean[] columns=new boolean[256];boolean any=false;for(int x=0;x<16;x++)for(int z=0;z<16;z++){int wx=p.x()*16+x,wz=p.z()*16+z;boolean inside=bounds!=null?bounds.column(wx,wz):sameRoom(room,DungeonRooms.at(new MapEdits.Position(wx,0,wz)));columns[x*16+z]=inside;any|=inside;}if(!any)return;
            var data=chunk.getSections();for(int i=0;i<data.length;i++){int y=chunk.getSectionYFromSectionIndex(i)*16;if(bounds!=null&&(y>bounds.max().y()||(long)y+15<bounds.min().y()))continue;if(data[i].maybeHas(s->s.is(source)))sections.addLast(new Section(data[i],p.x()*16,y,p.z()*16,columns));}
        }
        void scan(Section section){
            for(int y=0;y<16;y++){int wy=section.y+y;if(bounds!=null&&(wy<bounds.min().y()||wy>bounds.max().y()))continue;
                for(int x=0;x<16;x++)for(int z=0;z<16;z++)if(section.columns[x*16+z]&&section.data.getBlockState(x,y,z).is(source))plan.accept(new MapEdits.Position(section.x+x,wy,section.z+z),sourceId);
            }
        }
        void finish(){
            int count=plan.changed();if(count>0)MapEditor.applyBatch(before,plan.result());job=null;
            status=count==0?"No blocks needed replacing ("+plan.matched()+" matches).":"Replaced "+count+" blocks. Use Undo to revert the entire batch.";MapEditor.notice(status);
        }
    }
    private static boolean sameRoom(RoomLayouts.Frame a,RoomLayouts.Frame b){return a!=null&&b!=null&&a.key().equals(b.key())&&a.origin().equals(b.origin());}
}
