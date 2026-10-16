// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;

/** An isolated batch: building or rejecting a plan never changes editor data or its undo history. */
public final class MaterialReplacementPlan {
    public record Bounds(MapEdits.Position min,MapEdits.Position max){
        public static Bounds between(MapEdits.Position a,MapEdits.Position b){return new Bounds(new MapEdits.Position(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z())),new MapEdits.Position(Math.max(a.x(),b.x()),Math.max(a.y(),b.y()),Math.max(a.z(),b.z())));}
        public boolean column(int x,int z){return x>=min.x()&&x<=max.x()&&z>=min.z()&&z<=max.z();}
    }
    private final Map<MapEdits.Position,MapEdits.Edit> before;
    private final Map<MapEdits.Position,MapEdits.Edit> next;
    private final Set<MapEdits.Position> matched=new HashSet<>();
    private final String source,target;
    private int changed,matchedCount;
    public MaterialReplacementPlan(Map<MapEdits.Position,MapEdits.Edit> before,String source,String target){
        this.before=MapEdits.immutable(before);next=new LinkedHashMap<>(before);this.source=source;this.target=target;
        new MapEdits.Edit(new MapEdits.Position(0,0,0),source,false);new MapEdits.Edit(new MapEdits.Position(0,0,0),target,false);
        if(source.contains("[")||Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air").contains(source))throw new IllegalArgumentException("Choose a non-air source block type");
    }
    public void accept(MapEdits.Position p,String originalBlock){
        if(!source.equals(originalBlock))return;var old=before.get(p);
        // Untouched source-to-source matches need no stored positions, even in a large stone region.
        if(old==null&&source.equals(target)){matchedCount++;return;}
        if(!matched.add(p))return;matchedCount++;
        var edit=new MapEdits.Edit(p,target,old!=null&&old.originallyAir());if(edit.equals(old))return;
        next.put(p,edit);changed++;
    }
    public int matched(){return matchedCount;}
    public int changed(){return changed;}
    public Map<MapEdits.Position,MapEdits.Edit> result(){return MapEdits.immutable(next);}
}
