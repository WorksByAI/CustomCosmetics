// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Sample real states in short client ticks; prepare the immutable batch on the map worker. */
final class MapRegionFill {
    private static Job job;
    private MapRegionFill(){}
    static boolean busy(){return job!=null;}
    static void start(BlockPos first,BlockPos second,String material){
        var client=Minecraft.getInstance();
        if(!MapEditor.allowed(client))throw new IllegalStateException("Join an allowed world first");
        if(busy()||MapEditor.busy()||MaterialReplacement.busy())throw new IllegalStateException("Wait for the current map operation");
        job=new Job(client.level,first,second,material);MapEditor.notice("Preparing region fill. You can keep playing.");
    }
    static void tick(Minecraft client){
        if(job==null)return;var current=job;
        try{current.step(client);}catch(RuntimeException error){job=null;MapEditor.notice(error.getMessage()==null?"Region fill failed":error.getMessage());}
    }
    private static final class Job {
        final ClientLevel world;final String material;final Map<MapEdits.Position,MapEdits.Edit> before=MapEditor.DATA.snapshot();
        final Map<MapEdits.Position,MapEdits.Edit> changes=new HashMap<>();final Iterator<BlockPos> positions;
        CompletableFuture<Void> completing;
        Job(ClientLevel world,BlockPos first,BlockPos second,String material){this.world=world;this.material=material;positions=BlockPos.betweenClosed(first,second).iterator();}
        void step(Minecraft client){
            if(completing!=null){if(completing.isDone()){completing.join();job=null;MapEditor.notice("Region fill complete. Use Undo to revert the batch.");}return;}
            if(client.level!=world||!MapEditor.allowed(client)||MapEditor.DATA.snapshot()!=before)throw new IllegalStateException("Region fill cancelled because the world or edits changed. No changes were applied.");
            long end=System.nanoTime()+2_000_000L;int count=0;
            while(positions.hasNext()){
                var p=positions.next();if(world.hasChunkAt(p)){var key=MapEditor.position(p);var old=before.get(key);
                    changes.put(key,new MapEdits.Edit(key,material,old==null?world.getBlockState(p).isAir():old.originallyAir()));}
                if((++count&31)==0&&System.nanoTime()>=end)return;
            }
            completing=MapEditor.applyBatchInteractive(before,()->MapEdits.change(before,changes),()->job==this);
        }
    }
}
