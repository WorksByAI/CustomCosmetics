// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.*;
import java.util.function.Consumer;

/** Render-thread spatial index. Ordinary replacements only mark chunks that may contain containers. */
public final class GhostIndex {
    private static final class Section {
        final int x,y,z;final List<MapEdits.Edit> edits=new ArrayList<>();double distance;
        Section(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
    }
    private final Long2ObjectOpenHashMap<Section> sections=new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap ordinaryChunks=new Long2IntOpenHashMap();
    private final ObjectArrayList<Section> nearby=new ObjectArrayList<>();
    private boolean queried;
    private int previousMinX,previousMinY,previousMinZ,previousMaxX,previousMaxY,previousMaxZ;
    private double sortedX,sortedY,sortedZ;
    private final Comparator<Section> distanceOrder=(a,b)->{
        int order=Double.compare(a.distance,b.distance);
        if(order==0)order=Integer.compare(a.x,b.x);
        if(order==0)order=Integer.compare(a.y,b.y);
        return order==0?Integer.compare(a.z,b.z):order;
    };
    private int size;
    public GhostIndex(Collection<MapEdits.Edit> edits){
        for(var edit:edits){var p=edit.position();if(edit.originallyAir())addGhost(edit);else ordinaryChunks.addTo(chunk(p.x()>>4,p.z()>>4),1);}
    }
    private static long section(int x,int y,int z){return ((long)x&0x3fffffL)<<42|((long)z&0x3fffffL)<<20|((long)y&0xfffffL);}
    private static long chunk(int x,int z){return (long)x&0xffffffffL|(long)z<<32;}
    private void addGhost(MapEdits.Edit edit){
        var p=edit.position();int x=p.x()>>4,y=p.y()>>4,z=p.z()>>4;long key=section(x,y,z);
        var bucket=sections.get(key);if(bucket==null){bucket=new Section(x,y,z);sections.put(key,bucket);}
        bucket.edits.add(edit);size++;
    }
    public int size(){return size;}
    public boolean hasOrdinaryEdits(int chunkX,int chunkZ){return ordinaryChunks.containsKey(chunk(chunkX,chunkZ));}
    public void update(MapEdits.Position p,MapEdits.Edit previous,MapEdits.Edit edit){
        long chunk=chunk(p.x()>>4,p.z()>>4);
        if(previous!=null&&!previous.originallyAir()){int count=ordinaryChunks.get(chunk)-1;if(count<=0)ordinaryChunks.remove(chunk);else ordinaryChunks.put(chunk,count);}
        if(edit!=null&&!edit.originallyAir())ordinaryChunks.addTo(chunk,1);
        long key=section(p.x()>>4,p.y()>>4,p.z()>>4);var bucket=sections.get(key);
        if(bucket!=null){int before=bucket.edits.size();bucket.edits.removeIf(e->e.position().equals(p));size-=before-bucket.edits.size();if(bucket.edits.isEmpty())sections.remove(key);}
        if(edit!=null&&edit.originallyAir())addGhost(edit);
        nearby.clear();queried=false;
    }
    public void nearby(double x,double y,double z,int radius,Consumer<MapEdits.Edit> consumer){
        nearby(x,y,z,radius,Integer.MAX_VALUE,consumer);
    }
    public void nearby(double x,double y,double z,int radius,int limit,Consumer<MapEdits.Edit> consumer){
        if(size==0||limit<=0)return;
        int minX=(int)Math.floor(x-radius)>>4,maxX=(int)Math.floor(x+radius)>>4;
        int minY=(int)Math.floor(y-radius)>>4,maxY=(int)Math.floor(y+radius)>>4;
        int minZ=(int)Math.floor(z-radius)>>4,maxZ=(int)Math.floor(z+radius)>>4;
        boolean sort=!queried||x!=sortedX||y!=sortedY||z!=sortedZ;
        if(!queried||minX!=previousMinX||minY!=previousMinY||minZ!=previousMinZ||maxX!=previousMaxX||maxY!=previousMaxY||maxZ!=previousMaxZ){
            sort=true;
            nearby.clear();
            for(int sx=minX;sx<=maxX;sx++)for(int sy=minY;sy<=maxY;sy++)for(int sz=minZ;sz<=maxZ;sz++){
                var bucket=sections.get(section(sx,sy,sz));if(bucket!=null)nearby.add(bucket);
            }
            previousMinX=minX;previousMinY=minY;previousMinZ=minZ;previousMaxX=maxX;previousMaxY=maxY;previousMaxZ=maxZ;queried=true;
        }
        if(sort){
            for(int i=0;i<nearby.size();i++){var bucket=nearby.get(i);double dx=bucket.x*16+8-x,dy=bucket.y*16+8-y,dz=bucket.z*16+8-z;bucket.distance=dx*dx+dy*dy+dz*dz;}
            nearby.unstableSort(distanceOrder);sortedX=x;sortedY=y;sortedZ=z;
        }
        int found=0;double squaredRadius=radius*(double)radius;
        for(int i=0;i<nearby.size();i++){
            var entries=nearby.get(i).edits;
            for(int j=0;j<entries.size();j++){var edit=entries.get(j);var p=edit.position();double dx=p.x()+.5-x,dy=p.y()+.5-y,dz=p.z()+.5-z;
                if(dx*dx+dy*dy+dz*dz<=squaredRadius){consumer.accept(edit);if(++found>=limit)return;}
            }
        }
    }
}
