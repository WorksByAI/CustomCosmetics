// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.io.*;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.*;

/** Serial background writes of immutable room snapshots. New edits remain dirty until their own save. */
final class RoomSaveQueue {
    private record Saved(RoomLayouts snapshot,Set<String> keys){}
    private final Executor executor;private CompletableFuture<Saved> pending;
    RoomSaveQueue(){this(ForkJoinPool.commonPool());}
    RoomSaveQueue(Executor executor){this.executor=executor;}
    CompletableFuture<?> completion(){return pending==null?CompletableFuture.completedFuture(null):pending;}
    void tick(RoomLayouts rooms,Path path)throws IOException{
        if(pending!=null&&pending.isDone())harvest(rooms);
        if(pending==null&&rooms.hasChanges()){
            var snapshot=rooms.copy();var keys=snapshot.changedRooms();
            pending=CompletableFuture.supplyAsync(()->{try{snapshot.save(path);return new Saved(snapshot,keys);}catch(IOException error){throw new CompletionException(error);}},executor);
        }
    }
    private void harvest(RoomLayouts rooms)throws IOException{
        var task=pending;pending=null;
        try{var saved=task.join();rooms.saved(saved.snapshot,saved.keys);}catch(CompletionException error){throw new IOException("Could not save room edits",error.getCause());}
    }
    void flush(RoomLayouts rooms,Path path)throws IOException{
        if(pending!=null)harvest(rooms);
        if(rooms.hasChanges()){var snapshot=rooms.copy();var keys=snapshot.changedRooms();snapshot.save(path);rooms.saved(snapshot,keys);}
    }
}
