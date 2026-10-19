// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.playersize;

import com.customcosmetics.config.ConfigManager;
import com.google.gson.*;
import java.util.UUID;

/** Validated cosmetic proportions. This never changes an entity's physical dimensions. */
public record PlayerSizeSettings(boolean enabled,double x,double y,double z) {
    public static final PlayerSizeSettings NORMAL=new PlayerSizeSettings(false,1,1,1);
    public PlayerSizeSettings {x=bounded(x);y=bounded(y);z=bounded(z);}
    public static double bounded(double value){return Double.isFinite(value)?Math.clamp(value,.1,3):1;}
    public static PlayerSizeSettings local(){if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.PLAYER_SIZE))return NORMAL;return new PlayerSizeSettings(ConfigManager.playerSizeEnabled,ConfigManager.playerSizeX,ConfigManager.playerSizeY,ConfigManager.playerSizeZ);}
    public PlayerSizeSettings active(){return enabled?this:NORMAL;}
    public static PlayerSizeSettings choose(UUID self,UUID target,boolean offline,PlayerSizeSettings local,PlayerSizeSettings remote){
        if(target==null)return NORMAL;
        if(target.equals(self))return local==null?NORMAL:local.active();
        return offline||remote==null?NORMAL:remote.active();
    }
    public double nameOffset(double height,double existingOffset){return existingOffset+(height+existingOffset)*(active().y()-1);}
    public JsonObject json(){
        var active=active();var result=new JsonObject();result.addProperty("enabled",active.enabled());result.addProperty("x",active.x());result.addProperty("y",active.y());result.addProperty("z",active.z());return result;
    }
    public static PlayerSizeSettings parse(JsonElement value){
        if(value==null||!value.isJsonObject())return NORMAL;var object=value.getAsJsonObject();
        var enabled=object.get("enabled");boolean on=enabled!=null&&enabled.isJsonPrimitive()&&enabled.getAsJsonPrimitive().isBoolean()&&enabled.getAsBoolean();
        return new PlayerSizeSettings(on,number(object,"x"),number(object,"y"),number(object,"z")).active();
    }
    private static double number(JsonObject object,String name){try{var value=object.getAsJsonPrimitive(name);return value.isNumber()?value.getAsDouble():1;}catch(RuntimeException malformed){return 1;}}
}
