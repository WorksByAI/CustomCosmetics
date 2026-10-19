// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled original designs. Network payloads select known IDs, never URLs or executable models. */
public final class AccessoryCatalog {
    public enum Collection { PIXEL("pixel","Pixel"),DETAILED("detailed","Detailed");public final String id,label;Collection(String id,String label){this.id=id;this.label=label;} }
    public record Style(String id,String label,String kind,int variant,int color,int accent,Collection collection){}
    public record Slot(String id,String label,List<Style> styles){public Slot{styles=List.copyOf(styles);}}
    private static final List<Slot> SLOTS=load();
    private static final Map<String,Slot> BY_ID=index();
    private static Map<String,Slot> index(){var result=new HashMap<String,Slot>();for(var slot:SLOTS)result.put(slot.id,slot);return Map.copyOf(result);}
    private AccessoryCatalog(){}
    public static List<Slot> slots(){return SLOTS;}
    public static List<Slot> slots(Collection collection){return SLOTS.stream().map(slot->new Slot(slot.id,slot.label,styles(slot.id,collection))).filter(slot->!slot.styles.isEmpty()).toList();}
    public static List<Style> styles(String key,Collection collection){return slot(key).styles.stream().filter(style->style.collection==collection).toList();}
    public static int count(Collection collection){return SLOTS.stream().flatMap(slot->slot.styles.stream()).mapToInt(style->style.collection==collection?1:0).sum();}
    public static Slot slot(String key){var slot=BY_ID.get(key);if(slot==null)throw new IllegalArgumentException(key);return slot;}
    public static Style style(String key,String id){var slot=slot(key);for(var style:slot.styles)if(style.id.equals(id))return style;return slot.styles.getFirst();}
    public static int count(){return SLOTS.stream().mapToInt(s->s.styles.size()).sum();}
    public static boolean known(String key){return key!=null&&BY_ID.containsKey(key);}
    private static List<Slot> load(){
        try(var stream=AccessoryCatalog.class.getResourceAsStream("/assets/customcosmetics/accessories/catalog.json")){
            if(stream==null)throw new IOException("Missing accessory catalog");var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();var slots=new ArrayList<Slot>();
            for(var value:root.getAsJsonArray("slots")){var slot=value.getAsJsonObject();var styles=new ArrayList<Style>();for(var element:slot.getAsJsonArray("styles")){var style=element.getAsJsonObject();var collection=style.has("collection")&&style.get("collection").getAsString().equals("detailed")?Collection.DETAILED:Collection.PIXEL;styles.add(new Style(style.get("id").getAsString(),style.get("label").getAsString(),style.get("kind").getAsString(),style.get("variant").getAsInt(),color(style,"color"),color(style,"accent"),collection));}slots.add(new Slot(slot.get("id").getAsString(),slot.get("label").getAsString(),styles));}
            return List.copyOf(slots);
        }catch(IOException|RuntimeException invalid){throw new ExceptionInInitializerError(invalid);}
    }
    private static int color(JsonObject j,String key){return Integer.parseInt(j.get(key).getAsString().substring(1),16);}
}
