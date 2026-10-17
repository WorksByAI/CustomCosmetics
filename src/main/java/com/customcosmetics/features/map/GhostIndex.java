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
    public void update(MapEdits.Position p,MapEdits.Edit edit){
        var section=new MapEdits.Position(p.x()>>4,p.y()>>4,p.z()>>4);var list=sections.get(section);
        if(list!=null){int before=list.size();list.removeIf(e->e.position().equals(p));size-=before-list.size();if(list.isEmpty())sections.remove(section);}
        if(edit!=null&&edit.originallyAir()){sections.computeIfAbsent(section,ignored->new ArrayList<>()).add(edit);size++;}
    }
    public void nearby(double x,double y,double z,int radius,Consumer<MapEdits.Edit> consumer){
        nearby(x,y,z,radius,Integer.MAX_VALUE,consumer);
    }
    public void nearby(double x,double y,double z,int radius,int limit,Consumer<MapEdits.Edit> consumer){
        if(size==0)return;
        int minX=(int)Math.floor(x-radius)>>4,maxX=(int)Math.floor(x+radius)>>4;
        int minY=(int)Math.floor(y-radius)>>4,maxY=(int)Math.floor(y+radius)>>4;
        int minZ=(int)Math.floor(z-radius)>>4,maxZ=(int)Math.floor(z+radius)>>4;
        var nearby=new ArrayList<MapEdits.Position>();for(int sx=minX;sx<=maxX;sx++)for(int sy=minY;sy<=maxY;sy++)for(int sz=minZ;sz<=maxZ;sz++){var section=new MapEdits.Position(sx,sy,sz);if(sections.containsKey(section))nearby.add(section);}
        nearby.sort(Comparator.comparingDouble(p->{double dx=p.x()*16+8-x,dy=p.y()*16+8-y,dz=p.z()*16+8-z;return dx*dx+dy*dy+dz*dz;}));int found=0;
        for(var section:nearby){
            for(var edit:sections.get(section)){var p=edit.position();double dx=p.x()+.5-x,dy=p.y()+.5-y,dz=p.z()+.5-z;if(dx*dx+dy*dy+dz*dz<=radius*(double)radius){consumer.accept(edit);if(++found>=limit)return;}}
        }
    }
}
