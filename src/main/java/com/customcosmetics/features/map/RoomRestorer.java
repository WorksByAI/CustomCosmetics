// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Index immutable layouts off-thread, then resolve only loaded sections within a client-tick budget. */
final class RoomRestorer {
    private record Section(MapEdits.Position position,List<Map.Entry<MapEdits.Position,String>> edits){}
    private static final class Work {
        final RoomLayouts.Frame frame;final Map<MapEdits.Position,String> source;final CompletableFuture<List<Section>> sections;
        final Set<MapEdits.Position> completed=new HashSet<>(),preloaded=new HashSet<>();
        Work(RoomLayouts.Frame frame,Map<MapEdits.Position,String> source){this.frame=frame;this.source=source;sections=source.size()<8192?CompletableFuture.completedFuture(index(frame,source)):CompletableFuture.supplyAsync(()->index(frame,source),MapWork.EXECUTOR);}
    }
    private record Selected(Work work,Section section,boolean knownOnly){}
    private final Map<String,Work> works=new HashMap<>();private Job job;private long nextSectionCheck;
    void reset(){works.clear();job=null;nextSectionCheck=0;}
    void cancel(){job=null;}
    void request(Collection<RoomLayouts.Frame> frames){
        var wanted=new HashSet<String>();for(var frame:frames){var source=MapEditor.ROOMS.edits(frame.key());String id=frame.key()+"@"+frame.origin()+":"+frame.turns();wanted.add(id);var old=works.get(id);
            if(source.isEmpty())works.remove(id);else if(old==null||old.source!=source){works.put(id,new Work(frame,source));nextSectionCheck=0;}}
        works.keySet().retainAll(wanted);
    }
    private static List<Section> index(RoomLayouts.Frame frame,Map<MapEdits.Position,String> source){
        var sections=new HashMap<MapEdits.Position,List<Map.Entry<MapEdits.Position,String>>>();
        for(var entry:source.entrySet()){var p=frame.world(entry.getKey());var key=new MapEdits.Position(p.x()>>4,p.y()>>4,p.z()>>4);sections.computeIfAbsent(key,k->new ArrayList<>()).add(entry);}
        return sections.entrySet().stream().map(e->new Section(e.getKey(),e.getValue())).toList();
    }
    void tick(){
        var client=Minecraft.getInstance();if(client.level==null)return;
        works.values().removeIf(w->MapEditor.ROOMS.edits(w.frame.key())!=w.source);
        if(job!=null&&(MapEditor.DATA.snapshot()!=job.before||job.selected.stream().anyMatch(s->!works.containsValue(s.work)||MapEditor.ROOMS.edits(s.work.frame.key())!=s.work.source)))job=null;
        if(job==null){
            long now=System.nanoTime();if(now<nextSectionCheck)return;
            var selected=new ArrayList<Selected>();
            for(var work:works.values())if(work.sections.isDone())for(var section:work.sections.join())if(!work.completed.contains(section.position)){
                boolean loaded=MapEditor.loaded(MapEditor.blockPos(section.position).multiply(16));
                if(loaded||!work.preloaded.contains(section.position))selected.add(new Selected(work,section,!loaded));
            }
            if(selected.isEmpty()){nextSectionCheck=now+100_000_000;return;}job=new Job(selected);
        }
        job.step();
    }
    private record Unknown(Work work,MapEdits.Position relative,MapEdits.Position position,String material){}
    private record Prepared(Map<MapEdits.Position,MapEdits.Edit> builder,List<Unknown> unknown,Set<MapEdits.Position> dirty,Set<Selected> deferred){}
    private final class Job {
        final List<Selected> selected;final Map<MapEdits.Position,MapEdits.Edit> before=MapEditor.DATA.snapshot();
        final CompletableFuture<Prepared> preparing;final Map<Work,Map<MapEdits.Position,Boolean>> origins=new HashMap<>();
        Prepared prepared;int unknown;
        Job(List<Selected> selected){
            this.selected=selected;var flags=new HashMap<Work,Map<MapEdits.Position,Boolean>>();for(var s:selected)flags.putIfAbsent(s.work,MapEditor.ROOMS.originSnapshot(s.work.frame.key()));
            if(selected.stream().mapToInt(s->s.section.edits.size()).sum()<8192&&before.size()<8192)preparing=CompletableFuture.completedFuture(prepare(flags));
            else preparing=CompletableFuture.supplyAsync(()->prepare(flags),MapWork.EXECUTOR);
        }
        Prepared prepare(Map<Work,Map<MapEdits.Position,Boolean>> flags){
            var builder=new HashMap<>(before);var unknown=new ArrayList<Unknown>();var dirty=new HashSet<MapEdits.Position>();var materials=new HashMap<String,String>();var deferred=new HashSet<Selected>();
            for(var s:selected)for(var entry:s.section.edits){var work=s.work;var p=work.frame.world(entry.getKey());if(before.containsKey(p))continue;
                String material=materials.computeIfAbsent(work.frame.turns()+":"+entry.getValue(),key->MapEditor.state(entry.getValue())==null?"":MapEditor.rotateMaterial(entry.getValue(),work.frame.turns()));if(material.isEmpty())continue;
                Boolean air=flags.get(work).get(entry.getKey());if(air==null){if(s.knownOnly){deferred.add(s);continue;}unknown.add(new Unknown(work,entry.getKey(),p,material));}else builder.put(p,new MapEdits.Edit(p,material,air));dirty.add(s.section.position);
            }
            return new Prepared(builder,unknown,dirty,deferred);
        }
        void step(){
            if(!preparing.isDone())return;if(prepared==null)prepared=preparing.join();long end=System.nanoTime()+2_000_000;int steps=0;
            while(unknown<prepared.unknown.size()){
                var next=prepared.unknown.get(unknown++);if(!MapEditor.loaded(MapEditor.blockPos(next.position))){job=null;return;}boolean air=MapEditor.realState(MapEditor.blockPos(next.position)).isAir();
                origins.computeIfAbsent(next.work,w->new HashMap<>()).put(next.relative,air);prepared.builder.put(next.position,new MapEdits.Edit(next.position,next.material,air));
                if((++steps&31)==0&&System.nanoTime()>=end)return;
            }
            origins.forEach((work,values)->MapEditor.ROOMS.rememberOrigins(work.frame.key(),values));
            for(var s:selected){s.work.preloaded.add(s.section.position);if(!prepared.deferred.contains(s))s.work.completed.add(s.section.position);}
            if(prepared.builder.size()!=before.size())MapEditor.restored(before,MapEdits.own(prepared.builder),prepared.dirty);
            job=null;
        }
    }
}
