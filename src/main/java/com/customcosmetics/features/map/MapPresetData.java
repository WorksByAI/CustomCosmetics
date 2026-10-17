// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.google.gson.*;
import java.util.*;
import java.util.function.BiFunction;

/** Room context travels with the preset in the existing version-1 CCMap metadata field. */
public final class MapPresetData {
    private static final String PREFIX="ccpreset:1:";
    private MapPresetData(){}
    public static String roomKey(MapEdits.Preset preset){
        if(!preset.fingerprint().startsWith(PREFIX))return "";
        try{return RoomLayouts.canonicalKey(JsonParser.parseString(preset.fingerprint().substring(PREFIX.length())).getAsJsonObject().get("room").getAsString());}
        catch(RuntimeException error){throw new IllegalArgumentException("Invalid preset room metadata",error);}
    }
    public static String roomLabel(MapEdits.Preset preset){String key=roomKey(preset);return key.equals(DungeonRooms.KUUDRA_KEY)?"Kuudra's Hollow":key.equals("floor:F7+M7")?"F7 / M7 Boss":key.startsWith("room:")?key.substring(5):key.startsWith("fingerprint:")?"Room "+key.substring(12,Math.min(20,key.length())):key.isEmpty()?"Legacy / manual anchor":key;}
    public static boolean sameRoom(RoomLayouts.Frame a,RoomLayouts.Frame b){return a!=null&&b!=null&&a.key().equals(b.key())&&a.origin().equals(b.origin());}
    public static MapEdits.Preset capture(String name,RoomLayouts.Frame frame,Collection<MapEdits.Edit> edits,BiFunction<String,Integer,String> rotate){
        var metadata=new JsonObject();metadata.addProperty("room",frame.key());
        var materials=new HashMap<String,String>();var result=new ArrayList<MapEdits.Edit>(edits.size());
        for(var edit:edits)result.add(new MapEdits.Edit(frame.relative(edit.position()),materials.computeIfAbsent(edit.material(),id->rotate.apply(id,-frame.turns())),edit.originallyAir()));
        return new MapEdits.Preset(name,result,PREFIX+metadata);
    }
    public static Map<MapEdits.Position,MapEdits.Edit> place(MapEdits.Preset preset,RoomLayouts.Frame frame,int extraTurns,BiFunction<String,Integer,String> rotate){
        if(!roomKey(preset).equals(frame.key()))throw new IllegalArgumentException("This preset belongs to "+roomLabel(preset)+". Enter that room first.");
        int turns=frame.turns()+extraTurns;var materials=new HashMap<String,String>();var result=new HashMap<MapEdits.Position,MapEdits.Edit>();
        for(var edit:preset.edits()){var pos=edit.position().rotate(turns).add(frame.origin());result.put(pos,new MapEdits.Edit(pos,materials.computeIfAbsent(edit.material(),id->rotate.apply(id,turns)),edit.originallyAir()));}
        return MapEdits.immutable(result);
    }
}
