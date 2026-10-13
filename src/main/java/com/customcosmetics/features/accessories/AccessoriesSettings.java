// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;

import com.google.gson.*;
import java.util.*;

/** Independent cosmetic parts, with no gameplay or collision changes. */
public record AccessoriesSettings(Part wings, Part halo, Part ears, Map<String,Part> extra) {
    public static final Part WINGS = new Part(false,1,0xFFF0F7,0xFFA8CA,1,"feather");
    public static final Part HALO = new Part(false,1,0xFFD979,0xFFF5CD,0,"ring");
    public static final Part EARS = new Part(false,1,0xF6D7EC,0xFFA8CA,0,"cat");
    public static final AccessoriesSettings NONE = new AccessoriesSettings(WINGS,HALO,EARS);
    public AccessoriesSettings(Part wings,Part halo,Part ears){this(wings,halo,ears,Map.of());}
    public AccessoriesSettings {
        wings=normalize("wings",wings);halo=normalize("halo",halo);ears=normalize("ears",ears);
        var known=new TreeMap<String,Part>();for(var slot:AccessoryCatalog.slots())if(!Set.of("wings","halo","ears").contains(slot.id()))known.put(slot.id(),normalize(slot.id(),extra==null?null:extra.get(slot.id())));extra=Collections.unmodifiableMap(known);
    }
    private static Part normalize(String key,Part part){var style=AccessoryCatalog.style(key,part==null?null:part.style());return part==null?new Part(false,1,style.color(),style.accent(),key.equals("wings")?1:0,style.id()):part.style(style.id());}
    public record Part(boolean enabled,double scale,int color,int accent,double speed,String style) {
        public Part(boolean enabled,double scale,int color,int accent,double speed){this(enabled,scale,color,accent,speed,null);}
        public Part { scale=Double.isFinite(scale)?Math.clamp(scale,.5,2):1;speed=Double.isFinite(speed)?Math.clamp(speed,0,3):1;color&=0xFFFFFF;accent&=0xFFFFFF; }
        public Part enabled(boolean value){return new Part(value,scale,color,accent,speed,style);}
        public Part scale(double value){return new Part(enabled,value,color,accent,speed,style);}
        public Part color(int value){return new Part(enabled,scale,value,accent,speed,style);}
        public Part accent(int value){return new Part(enabled,scale,color,value,speed,style);}
        public Part speed(double value){return new Part(enabled,scale,color,accent,value,style);}
        public Part style(String value){return new Part(enabled,scale,color,accent,speed,value);}
        JsonObject json(){var j=new JsonObject();j.addProperty("enabled",enabled);j.addProperty("scale",scale);j.addProperty("color",hex(color));j.addProperty("accent",hex(accent));j.addProperty("speed",speed);j.addProperty("style",style);return j;}
    }
    public Part part(String key){return switch(key){case "wings"->wings;case "halo"->halo;case "ears"->ears;default->{if(!AccessoryCatalog.known(key))throw new IllegalArgumentException(key);yield extra.get(key);}};}
    public AccessoriesSettings with(String key,Part part){return switch(key){case "wings"->new AccessoriesSettings(part,halo,ears,extra);case "halo"->new AccessoriesSettings(wings,part,ears,extra);case "ears"->new AccessoriesSettings(wings,halo,part,extra);default->{if(!AccessoryCatalog.known(key))throw new IllegalArgumentException(key);var copy=new TreeMap<>(extra);copy.put(key,part);yield new AccessoriesSettings(wings,halo,ears,copy);}};}
    public AccessoriesSettings active(){
        var w=wings.enabled?wings:NONE.wings;var h=halo.enabled?halo:NONE.halo;var e=ears.enabled?ears:NONE.ears;
        var copy=new TreeMap<String,Part>();extra.forEach((key,part)->copy.put(key,part.enabled?part:NONE.extra.get(key)));
        return w.equals(wings)&&h.equals(halo)&&e.equals(ears)&&copy.equals(extra)?this:new AccessoriesSettings(w,h,e,copy);
    }
    public boolean any(){return wings.enabled||halo.enabled||ears.enabled||extra.values().stream().anyMatch(Part::enabled);}
    public static AccessoriesSettings choose(UUID self,UUID target,boolean offline,AccessoriesSettings local,AccessoriesSettings remote){
        if(target==null)return NONE;if(target.equals(self))return local==null?NONE:local.active();return offline||remote==null?NONE:remote.active();
    }
    public JsonObject json(){var active=active();var j=new JsonObject();for(var slot:AccessoryCatalog.slots())j.add(slot.id(),active.part(slot.id()).json());return j;}
    public static AccessoriesSettings parse(JsonElement value){
        if(value==null||!value.isJsonObject())return NONE;var j=value.getAsJsonObject();var parsed=NONE;for(var slot:AccessoryCatalog.slots())parsed=parsed.with(slot.id(),parse(j.get(slot.id()),normalize(slot.id(),null)));return parsed.active();
    }
    private static Part parse(JsonElement value,Part fallback){
        if(value==null||!value.isJsonObject())return fallback;var j=value.getAsJsonObject();var enabled=j.get("enabled");
        if(enabled==null||!enabled.isJsonPrimitive()||!enabled.getAsJsonPrimitive().isBoolean()||!enabled.getAsBoolean())return fallback;
        String style=null;try{var raw=j.getAsJsonPrimitive("style");if(raw!=null&&raw.isString())style=raw.getAsString();}catch(RuntimeException ignored){}
        return new Part(true,number(j,"scale",1),color(j,"color",fallback.color),color(j,"accent",fallback.accent),number(j,"speed",fallback.speed),style);
    }
    private static double number(JsonObject j,String key,double fallback){try{var v=j.getAsJsonPrimitive(key);return v.isNumber()&&Double.isFinite(v.getAsDouble())?v.getAsDouble():fallback;}catch(RuntimeException invalid){return fallback;}}
    private static int color(JsonObject j,String key,int fallback){try{var v=j.getAsJsonPrimitive(key);return v.isString()&&v.getAsString().matches("#[0-9a-fA-F]{6}")?Integer.parseInt(v.getAsString().substring(1),16):fallback;}catch(RuntimeException invalid){return fallback;}}
    public static String hex(int value){return String.format(java.util.Locale.ROOT,"#%06X",value&0xFFFFFF);}
}
