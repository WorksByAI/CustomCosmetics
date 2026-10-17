// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;
import java.util.function.*;

/** Prepare expensive bulk diffs and canonical room snapshots entirely from immutable inputs. */
record MapChange(Map<MapEdits.Position,MapEdits.Edit> edits,RoomLayouts rooms,Map<MapEdits.Position,Optional<String>> unknown){
    static MapChange prepare(Map<MapEdits.Position,MapEdits.Edit> before,Map<MapEdits.Position,MapEdits.Edit> after,RoomLayouts rooms,Function<MapEdits.Position,RoomLayouts.Frame> resolve,BiFunction<String,Integer,String> rotate){
        var points=MapEdits.differences(before,after);if(points==null){points=new HashSet<>(before.keySet());points.addAll(after.keySet());}
        var grouped=new HashMap<RoomLayouts.Frame,Map<MapEdits.Position,Optional<String>>>();var origins=new HashMap<MapEdits.Position,Boolean>();var materials=new HashMap<String,String>();var unknown=new HashMap<MapEdits.Position,Optional<String>>();
        for(var p:points){var edit=after.get(p);if(Objects.equals(before.get(p),edit))continue;var frame=resolve.apply(p);var value=Optional.ofNullable(edit==null?null:edit.material());
            if(frame==null)unknown.put(p,value);else grouped.computeIfAbsent(frame,k->new HashMap<>()).put(p,value.map(id->materials.computeIfAbsent(frame.turns()+":"+id,k->rotate.apply(id,-frame.turns()))));if(edit!=null)origins.put(p,edit.originallyAir());}
        grouped.forEach((frame,updates)->rooms.changeAll(frame,updates,origins));return new MapChange(after,rooms,unknown);
    }
}
