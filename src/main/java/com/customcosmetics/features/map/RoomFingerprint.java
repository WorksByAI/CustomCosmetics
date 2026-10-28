// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;
import java.util.HexFormat;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;
/** Stable columns sampled from authoritative states; null means an unloaded chunk, never air. */
public final class RoomFingerprint {
    private RoomFingerprint(){}
    /** Every rotation samples the same nine columns; read each real block only once. */
    public static String[] rotations(MapEdits.Position origin,Function<MapEdits.Position,String> blocks){
        var samples=new java.util.HashMap<MapEdits.Position,String>();
        Function<MapEdits.Position,String> cached=p->{if(samples.containsKey(p))return samples.get(p);var value=blocks.apply(p);samples.put(p,value);return value;};
        String[] hashes={"","","",""};hashes[0]=compute(origin,0,cached);
        if(hashes[0].isEmpty())return hashes;
        for(int turns=1;turns<4;turns++)hashes[turns]=compute(origin,turns,cached);
        return hashes;
    }
    public static String compute(MapEdits.Position origin,int turns,Function<MapEdits.Position,String> blocks){
        var text=new StringBuilder();int solid=0;
        for(int dx:new int[]{-8,0,8})for(int dz:new int[]{-8,0,8})for(int y=50;y<141;y++){
            String block=blocks.apply(new MapEdits.Position(dx,y,dz).rotate(turns).add(origin));if(block==null)return "";if(!block.equals("minecraft:air"))solid++;text.append(block).append(';');
        }
        if(solid<12)return "";
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
