// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.camera;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.Freecam;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/** Hold a key for a temporary vanilla perspective; never rotates or moves the player. */
public final class Snaplook {
    private static KeyMapping key;
    private static CameraType previous;
    private static LocalPlayer owner;
    private static Object level;
    private static boolean blocked;
    private Snaplook(){}
    public static void init(){key=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.snaplook",InputConstants.UNKNOWN.getValue(),KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics","camera"))));}
    public static KeyMapping binding(){return key;}
    public static boolean active(){return previous!=null;}
    public static String normalize(String mode){return mode!=null&&java.util.Set.of("first","back","front").contains(mode)?mode:"front";}
    public static CameraType perspective(String mode){return switch(normalize(mode)){case "first"->CameraType.FIRST_PERSON;case "back"->CameraType.THIRD_PERSON_BACK;default->CameraType.THIRD_PERSON_FRONT;};}
    public static void tick(Minecraft client){
        if(key==null)return;while(key.consumeClick()){}
        if(!key.isDown()){stop(client);blocked=false;return;}
        if(!ConfigManager.snaplookEnabled||client.player==null||client.level==null||!client.player.isAlive()||client.player.isSleeping()
                ||ClientUiAccess.screen(client)!=null||!client.isWindowActive()||Freecam.active()||client.getCameraEntity()!=client.player){stop(client);return;}
        if(blocked)return;
        if(active()&&(owner!=client.player||level!=client.level)){stop(client);return;}
        if(!active()){previous=client.options.getCameraType();owner=client.player;level=client.level;}
        client.options.setCameraType(perspective(ConfigManager.snaplookPerspective));
    }
    public static void stop(Minecraft client){
        if(previous!=null){client.options.setCameraType(previous);previous=null;owner=null;level=null;}
        blocked=key!=null&&key.isDown();
    }
}
