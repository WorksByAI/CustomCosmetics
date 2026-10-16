// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;
import com.google.gson.*;
import com.mojang.serialization.*;
import net.minecraft.util.datafix.fixes.BlockStateData;
import java.util.*;

/** Converts serialized 1.8 block states via vanilla's flattening data, never legacy mod registry IDs. */
public final class LegacyMaterials {
    private LegacyMaterials(){}
    public static String convert(String text){
        if(text==null||text.length()>512)throw new IllegalArgumentException("Invalid legacy block state");
        text=text.strip();int bracket=text.indexOf('[');String name=bracket<0?text:text.substring(0,bracket);Dynamic<?> tag;
        if(name.matches("[0-9]+(?::[0-9]+)?")){String[] parts=name.split(":");int id=Integer.parseInt(parts[0]),meta=parts.length==2?Integer.parseInt(parts[1]):0;if(id<0||id>255||meta<0||meta>15)throw new IllegalArgumentException("Invalid legacy block number");tag=BlockStateData.getTag((id<<4)|meta);if(id!=0&&tag.get("Name").asString("").equals("minecraft:air"))throw new IllegalArgumentException("Unknown legacy block number");}
        else{
            var input=new JsonObject();input.addProperty("Name",name.contains(":")?name:"minecraft:"+name);var props=new JsonObject();
            if(bracket>=0){if(!text.endsWith("]"))throw new IllegalArgumentException("Invalid legacy properties");for(String field:text.substring(bracket+1,text.length()-1).split(",")){String[] pair=field.strip().split("=",-1);if(pair.length!=2||props.has(pair[0]))throw new IllegalArgumentException("Invalid legacy property");props.addProperty(pair[0],pair[1]);}}if(!props.isEmpty())input.add("Properties",props);tag=BlockStateData.upgradeBlockStateTag(new Dynamic<>(JsonOps.INSTANCE,input).convert(net.minecraft.nbt.NbtOps.INSTANCE));
        }
        var json=tag.convert(JsonOps.INSTANCE).getValue().getAsJsonObject();String modern=json.get("Name").getAsString();if(bracket<0&&!name.matches("[0-9]+(?::[0-9]+)?"))modern=BlockStateData.upgradeBlock(name.contains(":")?name:"minecraft:"+name);
        // Names changed after the 1.13 flattening table.
        modern=modernName(modern);
        var properties=new TreeMap<String,String>();if(json.has("Properties"))json.getAsJsonObject("Properties").entrySet().forEach(e->properties.put(e.getKey(),e.getValue().getAsString()));
        String result=modern+(properties.isEmpty()?"":"["+String.join(",",properties.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"]");new MapEdits.Edit(new MapEdits.Position(0,0,0),result,false);return result;
    }
    public static String modernName(String name){if(name.endsWith("_bark"))return name.substring(0,name.length()-5)+"_wood";return switch(name){case "minecraft:stone_slab"->"minecraft:smooth_stone_slab";case "minecraft:grass_path"->"minecraft:dirt_path";case "minecraft:grass"->"minecraft:short_grass";case "minecraft:sign"->"minecraft:oak_sign";case "minecraft:wall_sign"->"minecraft:oak_wall_sign";default->name;};}
    /** FME favorites concatenate a registry name and metadata, e.g. minecraft:wool14. */
    public static String favorite(String text){
        var match=java.util.regex.Pattern.compile("^(.+?)([0-9]{1,2})$").matcher(text);if(!match.matches())return convert(text);
        String name=match.group(1);int meta=Integer.parseInt(match.group(2));if(meta>15)throw new IllegalArgumentException("Invalid favorite metadata");
        // Resolve the old registry name to its flattened default, then apply its original metadata.
        String normal=convert(name);for(int id=0;id<256;id++){String base=modernName(BlockStateData.getTag(id<<4).get("Name").asString(""));if(base.equals(normal))return convert(id+":"+meta);}
        throw new IllegalArgumentException("Unknown legacy favorite: "+text);
    }
}
