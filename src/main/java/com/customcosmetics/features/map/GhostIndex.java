// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;
import java.util.function.Consumer;

/** Only air additions need standalone geometry. Ordinary edits stay in the baked chunk mesh. */
public final class GhostIndex {
    private final Map<MapEdits.Position,List<MapEdits.Edit>> sections=new HashMap<>();
    private int size;
    public GhostIndex(Collection<MapEdits.Edit> edits){
        for(var edit:edits)if(edit.originallyAir()){
            var p=edit.position();sections.computeIfAbsent(new MapEdits.Position(p.x()>>4,p.y()>>4,p.z()>>4),ignored->new ArrayList<>()).add(edit);size++;
        }
    }
    public int size(){return size;}
    public void nearby(double x,double y,double z,int radius,Consumer<MapEdits.Edit> consumer){
        if(size==0)return;
        int minX=(int)Math.floor(x-radius)>>4,maxX=(int)Math.floor(x+radius)>>4;
        int minY=(int)Math.floor(y-radius)>>4,maxY=(int)Math.floor(y+radius)>>4;
        int minZ=(int)Math.floor(z-radius)>>4,maxZ=(int)Math.floor(z+radius)>>4;
        for(int sx=minX;sx<=maxX;sx++)for(int sy=minY;sy<=maxY;sy++)for(int sz=minZ;sz<=maxZ;sz++){
            var list=sections.get(new MapEdits.Position(sx,sy,sz));if(list==null)continue;
            for(var edit:list){var p=edit.position();double dx=p.x()+.5-x,dy=p.y()+.5-y,dz=p.z()+.5-z;if(dx*dx+dy*dy+dz*dz<=radius*(double)radius)consumer.accept(edit);}
        }
    }
}
