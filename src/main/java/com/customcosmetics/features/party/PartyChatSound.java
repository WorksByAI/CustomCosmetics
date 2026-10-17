// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.RemoteAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import java.util.*;

/** Local notification for accepted IRC messages. Preview also works while offline. */
public final class PartyChatSound {
    public static final String DEFAULT="minecraft:block.note_block.pling";
    public static final Map<String,String> PRESETS;
    static {
        var sounds=new LinkedHashMap<String,String>();
        sounds.put(DEFAULT,"Pling");sounds.put("minecraft:entity.experience_orb.pickup","Experience Orb");
        sounds.put("minecraft:block.bell.use","Bell");sounds.put("minecraft:block.amethyst_block.chime","Amethyst Chime");
        sounds.put("minecraft:entity.player.levelup","Level Up");sounds.put("minecraft:ui.button.click","Click");
        PRESETS=Collections.unmodifiableMap(sounds);
    }
    private PartyChatSound(){}
    public static String sound(String value){var id=value==null||value.isBlank()||value.length()>128||value.contains("://")?null:Identifier.tryParse(value.strip());return id==null?DEFAULT:id.toString();}
    public static double volume(double value){return Double.isFinite(value)?Math.clamp(value,0,1):.5;}
    public static double pitch(double value){return Double.isFinite(value)?Math.clamp(value,.5,2):1;}
    public static String label(String id){return PRESETS.getOrDefault(id,id);}
    public static boolean shouldNotify(UUID self,UUID sender){
        return ConfigManager.partyChatSoundEnabled&&ConfigManager.partyChatEnabled&&RemoteAccess.online()
            &&self!=null&&sender!=null&&!self.equals(sender);
    }
    public static void received(Minecraft client,UUID sender){if(shouldNotify(client.player==null?null:client.player.getUUID(),sender))preview(client);}
    public static boolean preview(Minecraft client){
        var id=Identifier.parse(sound(ConfigManager.partyChatSound));var manager=client.getSoundManager();
        if(manager.getSoundEvent(id)==null)return false;
        float volume=(float)volume(ConfigManager.partyChatSoundVolume);
        if(volume>0)manager.play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id),(float)pitch(ConfigManager.partyChatSoundPitch),volume));
        return true;
    }
}
