// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.google.gson.*;
import java.util.*;

/** Inclusive, one-based ranges of visible Unicode characters, independent of formatting codes. */
public final class NicknameSegments {
    public static final int MAX_SEGMENTS=16, MAX_POSITION=4096;
    private NicknameSegments(){}
    public record Segment(int from,int to,int start,int end) {
        public Segment {
            if(from<1||to<from||to>MAX_POSITION)throw new IllegalArgumentException("Use character positions from 1 to 4096; End must follow Start.");
            start&=0xFFFFFF;end&=0xFFFFFF;
        }
    }
    public record Paint(NicknameGradient gradient,double from,double to,double sample) {
        public int color(long time){return gradient.color(sample,0.5,time);}
    }
    public static List<Segment> validate(List<Segment> values){
        if(values==null)return List.of();
        if(values.size()>MAX_SEGMENTS)throw new IllegalArgumentException("You can use up to 16 gradient segments.");
        var sorted=new ArrayList<>(values);if(sorted.contains(null))throw new IllegalArgumentException("Invalid gradient segment.");
        sorted.sort(Comparator.comparingInt(Segment::from));int previous=0;
        for(var value:sorted){if(value.from()<=previous)throw new IllegalArgumentException("Gradient segments must not overlap.");previous=value.to();}
        return List.copyOf(sorted);
    }
    public static List<Segment> normalize(List<Segment> values){
        if(values==null)return List.of();
        var sorted=values.stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(Segment::from)).toList();
        var result=new ArrayList<Segment>();int previous=0;
        for(var value:sorted){if(value.from()<=previous)continue;result.add(value);previous=value.to();if(result.size()==MAX_SEGMENTS)break;}
        return List.copyOf(result);
    }
    /** Reuse one gradient per range so actual glyph widths can be measured within each segment. */
    public static List<Paint> paints(int length,int start,int end,List<Segment> segments){
        if(length<=0)return List.of();
        var fallback=new NicknameGradient("smooth",start,end,0);
        var result=new ArrayList<Paint>(length);
        for(int i=0;i<length;i++)result.add(new Paint(fallback,i/(double)length,(i+1)/(double)length,length==1?0:i/(double)(length-1)));
        for(var segment:normalize(segments)){
            int first=segment.from()-1,last=Math.min(length,segment.to()),count=last-first;
            if(count<=0)continue;
            var gradient=new NicknameGradient("smooth",segment.start(),segment.end(),0);
            for(int i=first;i<last;i++){
                int n=i-first;result.set(i,new Paint(gradient,n/(double)count,(n+1)/(double)count,count==1?0:n/(double)(count-1)));
            }
        }
        return List.copyOf(result);
    }
    public static JsonArray toJson(List<Segment> values){
        var result=new JsonArray();
        for(var value:normalize(values)){
            var obj=new JsonObject();obj.addProperty("from",value.from());obj.addProperty("to",value.to());
            obj.addProperty("start",String.format("#%06X",value.start()));obj.addProperty("end",String.format("#%06X",value.end()));result.add(obj);
        }return result;
    }
    public static List<Segment> fromJson(JsonElement json){
        if(json==null||!json.isJsonArray())return List.of();var values=new ArrayList<Segment>();int checked=0;
        for(var entry:json.getAsJsonArray()){
            if(++checked>MAX_SEGMENTS)break;
            try{
                var obj=entry.getAsJsonObject();
                if(!obj.getAsJsonPrimitive("from").isNumber()||!obj.getAsJsonPrimitive("to").isNumber()
                    ||!obj.getAsJsonPrimitive("start").isString()||!obj.getAsJsonPrimitive("end").isString())continue;
                double from=obj.get("from").getAsDouble(),to=obj.get("to").getAsDouble();
                if(!Double.isFinite(from)||!Double.isFinite(to)||from!=Math.floor(from)||to!=Math.floor(to))continue;
                String start=obj.get("start").getAsString(),end=obj.get("end").getAsString();
                if(!start.matches("#[0-9a-fA-F]{6}")||!end.matches("#[0-9a-fA-F]{6}"))continue;
                values.add(new Segment((int)from,(int)to,Integer.parseInt(start.substring(1),16),Integer.parseInt(end.substring(1),16)));
            }catch(RuntimeException ignored){/* Keep other valid ranges. */}
        }return normalize(values);
    }
}
