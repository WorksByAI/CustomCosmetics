// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Palette-compressed local layouts. Runs retain exact coordinates and all three origin states. */
final class MapLayoutCodec {
    private static final int MAGIC=0x43434D50, VERSION=1;
    record Layout(String name,String fingerprint,RoomLayouts.LegacyAnchor anchor,
                  Map<MapEdits.Position,String> edits,Map<MapEdits.Position,Boolean> origins) {}
    private MapLayoutCodec(){}

    static Path path(Path directory,String name){
        try{
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8)));
            String label=name.replaceAll("[^A-Za-z0-9_-]","_");if(label.length()>40)label=label.substring(0,40);
            return directory.resolve("layout-"+label+"-"+hash+".ccmap");
        }catch(NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    static void move(Path source,Path target)throws IOException{
        try{Files.move(source,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException error){Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    static void write(Path path,Layout layout)throws IOException{
        if(layout.name==null||layout.name.isEmpty()||layout.name.length()>160)throw new IOException("Invalid layout name");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"layout-",".tmp");
        try{
            var palette=new LinkedHashMap<String,Integer>();
            for(String material:layout.edits.values())palette.computeIfAbsent(material,key->{new MapEdits.Edit(new MapEdits.Position(0,0,0),key,false);return palette.size();});
            if(palette.size()>65536)throw new IOException("Invalid material palette");
            var rows=new ArrayList<>(layout.edits.entrySet());
            rows.sort(Comparator.comparingInt((Map.Entry<MapEdits.Position,String> e)->e.getKey().x()).thenComparingInt(e->e.getKey().y()).thenComparingInt(e->e.getKey().z()));
            try(var out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp),65536)))){
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(layout.name);out.writeUTF(layout.fingerprint);
                out.writeBoolean(layout.anchor!=null);if(layout.anchor!=null){out.writeInt(layout.anchor.baseCore());out.writeInt(layout.anchor.rotationCore());out.writeInt(layout.anchor.distance());}
                out.writeInt(palette.size());for(String material:palette.keySet())out.writeUTF(material);
                out.writeInt(rows.size());int previousX=0,previousY=0,previousZ=0;
                for(int i=0;i<rows.size();){
                    var row=rows.get(i);var p=row.getKey();Boolean origin=layout.origins.get(p);int end=i+1;
                    while(end<rows.size()){
                        var next=rows.get(end);var q=next.getKey();
                        if(q.x()!=p.x()||q.y()!=p.y()||(long)q.z()!=(long)p.z()+end-i||!row.getValue().equals(next.getValue())||!Objects.equals(origin,layout.origins.get(q)))break;
                        end++;
                    }
                    signed(out,(long)p.x()-previousX);signed(out,(long)p.y()-previousY);signed(out,(long)p.z()-previousZ);
                    unsigned(out,end-i);unsigned(out,palette.get(row.getValue())*3L+(origin==null?0:origin?2:1));
                    previousX=p.x();previousY=p.y();previousZ=p.z();i=end;
                }
            }
            move(temp,path);
        }finally{Files.deleteIfExists(temp);}
    }
    static Layout read(Path path)throws IOException{
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path),65536)))){
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Unknown map layout format: "+path.getFileName());
            String name=in.readUTF(),fingerprint=in.readUTF();if(name.isEmpty()||name.length()>160)throw new IOException("Invalid layout name");
            var anchor=in.readBoolean()?new RoomLayouts.LegacyAnchor(in.readInt(),in.readInt(),in.readInt()):null;
            int paletteSize=in.readInt();if(paletteSize<0||paletteSize>65536)throw new IOException("Invalid material palette");
            var palette=new ArrayList<String>();var seen=new HashSet<String>();
            for(int i=0;i<paletteSize;i++){String material=in.readUTF();new MapEdits.Edit(new MapEdits.Position(0,0,0),material,false);if(!seen.add(material))throw new IOException("Duplicate material");palette.add(material);}
            int count=in.readInt();if(count<0)throw new IOException("Invalid edit count");
            var edits=new HashMap<MapEdits.Position,String>();var origins=new HashMap<MapEdits.Position,Boolean>();int x=0,y=0,z=0;
            for(int i=0;i<count;){
                x=Math.toIntExact(Math.addExact(x,signed(in)));y=Math.toIntExact(Math.addExact(y,signed(in)));z=Math.toIntExact(Math.addExact(z,signed(in)));
                long length=unsigned(in),token=unsigned(in);if(length<1||length>count-i||token/3>=paletteSize||(long)z+length-1>Integer.MAX_VALUE)throw new IOException("Invalid layout run");
                String material=palette.get((int)(token/3));int flag=(int)(token%3);
                for(int offset=0;offset<(int)length;offset++){
                    var p=new MapEdits.Position(x,y,z+offset);if(edits.put(p,material)!=null)throw new IOException("Duplicate layout coordinate");
                    if(flag!=0)origins.put(p,flag==2);
                }
                i+=(int)length;
            }
            if(in.read()!=-1)throw new IOException("Trailing layout data"); // Also verifies gzip CRC and truncation.
            return new Layout(name,fingerprint,anchor,MapEdits.immutable(edits),MapEdits.immutable(origins));
        }catch(RuntimeException error){throw new IOException("Invalid map layout: "+path.getFileName(),error);}
    }
    private static void signed(DataOutput out,long value)throws IOException{unsigned(out,(value<<1)^(value>>63));}
    private static long signed(DataInput in)throws IOException{long value=unsigned(in);return(value>>>1)^-(value&1);}
    private static void unsigned(DataOutput out,long value)throws IOException{while((value&~127L)!=0){out.writeByte((int)value&127|128);value>>>=7;}out.writeByte((int)value);}
    private static long unsigned(DataInput in)throws IOException{
        long result=0;for(int shift=0;shift<=56;shift+=7){int value=in.readUnsignedByte();result|=(long)(value&127)<<shift;if((value&128)==0)return result;}
        throw new IOException("Invalid layout integer");
    }
}
