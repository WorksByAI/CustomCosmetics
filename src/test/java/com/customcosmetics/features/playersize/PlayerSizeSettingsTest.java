// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.playersize;
import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSizeSettingsTest {
    @Test void eachAxisIsIndependentAndInvalidOrExtremeNumbersAreBounded(){
        var value=new PlayerSizeSettings(true,.5,1.5,2);assertEquals(.5,value.x());assertEquals(1.5,value.y());assertEquals(2,value.z());
        var bounded=new PlayerSizeSettings(true,-100,100,Double.NaN);assertEquals(.1,bounded.x());assertEquals(3,bounded.y());assertEquals(1,bounded.z());
        assertEquals(value,PlayerSizeSettings.parse(value.json()));
        assertEquals(PlayerSizeSettings.NORMAL,PlayerSizeSettings.parse(JsonParser.parseString("{\"enabled\":\"true\",\"x\":2}")));
        assertEquals(new PlayerSizeSettings(true,1,1,1),PlayerSizeSettings.parse(JsonParser.parseString("{\"enabled\":true,\"x\":\"2\",\"y\":null,\"z\":{}}")));
    }
    @Test void onlySelfUsesLocalSettingsAndOfflineModeHidesEveryRemoteScale(){
        var self=UUID.randomUUID();var other=UUID.randomUUID();var local=new PlayerSizeSettings(true,.5,.75,1.25);var remote=new PlayerSizeSettings(true,2,1.5,.8);
        assertEquals(local,PlayerSizeSettings.choose(self,self,false,local,remote));assertEquals(local,PlayerSizeSettings.choose(self,self,true,local,remote));
        assertEquals(remote,PlayerSizeSettings.choose(self,other,false,local,remote));assertEquals(PlayerSizeSettings.NORMAL,PlayerSizeSettings.choose(self,other,true,local,remote));
        assertEquals(PlayerSizeSettings.NORMAL,PlayerSizeSettings.choose(self,other,false,local,null));assertEquals(PlayerSizeSettings.NORMAL,PlayerSizeSettings.choose(self,null,false,local,remote));
    }
    @Test void disabledDraftsDoNotEnterTheSyncStateAndNamesFollowVerticalScaleWithSmolOffset(){
        var disabled=new PlayerSizeSettings(false,.5,2,3);assertEquals(PlayerSizeSettings.NORMAL,disabled.active());assertEquals(PlayerSizeSettings.NORMAL.json(),disabled.json());
        var scaled=new PlayerSizeSettings(true,.5,.5,2);assertEquals(-.9,scaled.nameOffset(1.8,0),1e-6);
        assertEquals(-1.1,scaled.nameOffset(1.8,-.4),1e-6);assertEquals(-.4,PlayerSizeSettings.NORMAL.nameOffset(1.8,-.4),1e-6);
    }
}
