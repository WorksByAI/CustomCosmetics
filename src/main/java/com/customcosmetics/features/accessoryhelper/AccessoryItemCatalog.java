// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;
import com.customcosmetics.features.items.HeadTexture;
import com.google.gson.JsonParser;
import java.util.*;

/** Display metadata from Hypixel's public resource API; never an ownership source. */
public final class AccessoryItemCatalog {
    public record Item(String id,String name,String material,int damage,String texture,String rarity){}
    private AccessoryItemCatalog(){}
    public static Map<String,Item> parse(String json,AccessoryCatalog catalog){
        var root=JsonParser.parseString(json).getAsJsonObject();
        if(root.has("success")&&!root.get("success").getAsBoolean())throw new IllegalArgumentException("Item feed failed");
        var items=root.getAsJsonArray("items");if(items==null||items.size()>30000)throw new IllegalArgumentException("Invalid item feed");
        var result=new HashMap<String,Item>();
        for(var raw:items)try{
            var value=raw.getAsJsonObject();String id=value.get("id").getAsString();if(catalog.get(id)==null)continue;
            String name=value.get("name").getAsString().replaceAll("§.","").strip();if(name.isEmpty()||name.length()>160)continue;
            String material=value.get("material").getAsString(),texture="";
            if(value.has("skin")){var skin=value.get("skin");texture=HeadTexture.normalize(skin.isJsonObject()?skin.getAsJsonObject().get("value").getAsString():skin.getAsString());}
            result.put(id,new Item(id,name,material,value.has("durability")?value.get("durability").getAsInt():0,texture,value.has("tier")?value.get("tier").getAsString():"COMMON"));
        }catch(RuntimeException invalid){/* Keep independent valid items available. */}
        return Map.copyOf(result);
    }
}
