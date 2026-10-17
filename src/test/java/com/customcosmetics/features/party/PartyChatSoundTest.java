package com.customcosmetics.features.party;

import com.customcosmetics.config.ConfigManager;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PartyChatSoundTest {
    @Test void onlyOtherPlayersNotifyWhenBothChatAndSoundAreEnabledAndOnline(){
        boolean enabled=ConfigManager.partyChatSoundEnabled,chat=ConfigManager.partyChatEnabled,offline=ConfigManager.offlineMode;
        var self=UUID.randomUUID();var other=UUID.randomUUID();
        try{
            ConfigManager.partyChatSoundEnabled=true;ConfigManager.partyChatEnabled=true;ConfigManager.offlineMode=false;
            assertTrue(PartyChatSound.shouldNotify(self,other));assertFalse(PartyChatSound.shouldNotify(self,self));
            assertFalse(PartyChatSound.shouldNotify(null,other));assertFalse(PartyChatSound.shouldNotify(self,null));
            ConfigManager.partyChatSoundEnabled=false;assertFalse(PartyChatSound.shouldNotify(self,other));
            ConfigManager.partyChatSoundEnabled=true;ConfigManager.partyChatEnabled=false;assertFalse(PartyChatSound.shouldNotify(self,other));
            ConfigManager.partyChatEnabled=true;ConfigManager.offlineMode=true;assertFalse(PartyChatSound.shouldNotify(self,other));
        }finally{ConfigManager.partyChatSoundEnabled=enabled;ConfigManager.partyChatEnabled=chat;ConfigManager.offlineMode=offline;}
    }
    @Test void acceptsResourcePackIdsAndBoundsPlaybackSettings(){
        assertEquals("custom:party/ding",PartyChatSound.sound(" custom:party/ding "));
        assertEquals("minecraft:block.bell.use",PartyChatSound.sound("block.bell.use"));
        for(String value:new String[]{null,"","https://a/sound","invalid id","X".repeat(200)})assertEquals(PartyChatSound.DEFAULT,PartyChatSound.sound(value));
        assertEquals(.5,PartyChatSound.volume(Double.NaN));assertEquals(.5,PartyChatSound.volume(Double.POSITIVE_INFINITY));
        assertEquals(0,PartyChatSound.volume(-1));assertEquals(1,PartyChatSound.volume(2));assertEquals(.35,PartyChatSound.volume(.35));
        assertEquals(1,PartyChatSound.pitch(Double.NaN));assertEquals(.5,PartyChatSound.pitch(-1));assertEquals(2,PartyChatSound.pitch(9));
    }
}
