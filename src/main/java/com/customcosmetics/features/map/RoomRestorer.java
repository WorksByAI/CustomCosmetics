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
        volatile boolean cancelled;
        Work(RoomLayouts.Frame frame,Map<MapEdits.Position,String> source){this.frame=frame;this.source=source;sections=source.size()<8192?CompletableFuture.completedFuture(index(frame,source,()->cancelled)):CompletableFuture.supplyAsync(()->index(frame,source,()->cancelled),MapWork.EXECUTOR);}
    }
    private record Selected(Work work,Section section,boolean knownOnly){}
    private final Map<RoomLayouts.Frame,Work> works=new HashMap<>();private Job job;private long nextSectionCheck;
    private volatile String originWorkerThread="";
    String originWorkerThread(){return originWorkerThread;}
    void reset(){for(var work:works.values())work.cancelled=true;works.clear();cancel();nextSectionCheck=0;}
    void cancel(){if(job!=null)job.cancelled=true;job=null;}
    void request(Collection<RoomLayouts.Frame> frames){
        for(var frame:frames){var source=MapEditor.ROOMS.edits(frame.key());var old=works.get(frame);
            if(source.isEmpty()||old!=null&&old.source!=source){if(old!=null)old.cancelled=true;works.remove(frame);}
            if(!source.isEmpty()&&(old==null||old.source!=source)){works.put(frame,new Work(frame,source));nextSectionCheck=0;}}
        works.entrySet().removeIf(entry->{if(frames.contains(entry.getKey()))return false;entry.getValue().cancelled=true;return true;});
    }
    private static List<Section> index(RoomLayouts.Frame frame,Map<MapEdits.Position,String> source,java.util.function.BooleanSupplier cancelled){
        var sections=new HashMap<MapEdits.Position,List<Map.Entry<MapEdits.Position,String>>>();
        int checked=0;for(var entry:source.entrySet()){if((checked++&255)==0&&cancelled.getAsBoolean())return List.of();var p=frame.world(entry.getKey());var key=new MapEdits.Position(p.x()>>4,p.y()>>4,p.z()>>4);sections.computeIfAbsent(key,k->new ArrayList<>()).add(entry);}
        if(cancelled.getAsBoolean())return List.of();
        var result=new ArrayList<Section>(sections.size());
        for(var entry:sections.entrySet()){if((checked++&255)==0&&cancelled.getAsBoolean())return List.of();result.add(new Section(entry.getKey(),entry.getValue()));}
        return Collections.unmodifiableList(result);
    }
    void tick(){
        var client=Minecraft.getInstance();if(client.level==null)return;
        works.values().removeIf(w->{if(MapEditor.ROOMS.edits(w.frame.key())==w.source)return false;w.cancelled=true;return true;});
        if(job!=null&&(MapEditor.DATA.snapshot()!=job.before||job.selected.stream().anyMatch(s->works.get(s.work.frame)!=s.work||MapEditor.ROOMS.edits(s.work.frame.key())!=s.work.source)))cancel();
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
        volatile boolean cancelled;
        RoomLayouts originBaseline;CompletableFuture<RoomLayouts> committingOrigins;
        Job(List<Selected> selected){
            this.selected=selected;var flags=new HashMap<Work,Map<MapEdits.Position,Boolean>>();for(var s:selected)flags.putIfAbsent(s.work,MapEditor.ROOMS.originSnapshot(s.work.frame.key()));
            if(selected.stream().mapToInt(s->s.section.edits.size()).sum()<8192&&before.size()<8192)preparing=CompletableFuture.completedFuture(prepare(flags));
            else preparing=CompletableFuture.supplyAsync(()->prepare(flags),MapWork.EXECUTOR);
        }
        Prepared prepare(Map<Work,Map<MapEdits.Position,Boolean>> flags){
            if(cancelled)return null;
            var builder=HashMap.<MapEdits.Position,MapEdits.Edit>newHashMap(before.size());int checked=0;
            for(var entry:before.entrySet()){if((checked++&255)==0&&cancelled)return null;builder.put(entry.getKey(),entry.getValue());}
            var unknown=new ArrayList<Unknown>();var dirty=new HashSet<MapEdits.Position>();var materials=new HashMap<String,String>();var deferred=new HashSet<Selected>();
            for(var s:selected)for(var entry:s.section.edits){if((checked++&255)==0&&cancelled)return null;var work=s.work;var p=work.frame.world(entry.getKey());if(before.containsKey(p))continue;
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
            // A first automatic restore can discover millions of original-air flags.
            // Normalizing those immutable maps is batch work, just like preparation.
            if(!origins.isEmpty()){
                if(committingOrigins==null){
                    originBaseline=MapEditor.ROOMS.copy();var candidate=originBaseline.copy();
                    committingOrigins=CompletableFuture.supplyAsync(()->{
                        originWorkerThread=Thread.currentThread().getName();
                        for(var entry:origins.entrySet()){if(cancelled)return null;candidate.rememberOrigins(entry.getKey().frame.key(),entry.getValue(),()->cancelled);}
                        return candidate;
                    },MapWork.EXECUTOR);return;
                }
                if(!committingOrigins.isDone())return;
                var candidate=committingOrigins.join();
                if(cancelled||!MapEditor.ROOMS.sameData(originBaseline)){cancel();return;}
                MapEditor.ROOMS.adopt(candidate);
            }
            for(var s:selected){s.work.preloaded.add(s.section.position);if(!prepared.deferred.contains(s))s.work.completed.add(s.section.position);}
            if(prepared.builder.size()!=before.size())MapEditor.restored(before,MapEdits.own(prepared.builder),prepared.dirty);
            job=null;
        }
    }
}
