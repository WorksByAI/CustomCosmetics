package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class SettingsSearchTest {
    @Test void findsChatAudioControlsInChinese(){
        var sounds=List.of(new SettingsSearch.Entry("Sharing","Chat Notification Sound","","PARTY",1),new SettingsSearch.Entry("Sharing","Chat Sound Volume (%)","","PARTY",2),new SettingsSearch.Entry("Sharing","Chat Sound Pitch","","PARTY",3));
        assertEquals(3,SettingsSearch.find(sounds,"提示音").size());assertEquals(3,SettingsSearch.find(sounds,"声音").size());
        assertEquals(2,SettingsSearch.find(sounds,"音量").getFirst().offset());assertEquals(3,SettingsSearch.find(sounds,"音高").getFirst().offset());
    }
    private final List<SettingsSearch.Entry> entries=List.of(
        new SettingsSearch.Entry("Sharing","Location Tracers","Draw lines to shared players","PARTY",420),
        new SettingsSearch.Entry("Sharing","Location Tracer Color","#RRGGBB","PARTY",480),
        new SettingsSearch.Entry("Animations","Swing Speed","Visual swing timing","ANIMATIONS",600),
        new SettingsSearch.Entry("Damage Splash","Size","Local damage number size","DAMAGE",200));
    @Test void matchesTokensCaseInsensitivelyAndPreservesDestination(){
        assertEquals(2,SettingsSearch.find(entries,"LOCATION tracer").size());assertEquals(1,SettingsSearch.find(entries,"sharing color").size());
        assertEquals(600,SettingsSearch.find(entries,"swing speed").getFirst().offset());assertEquals("DAMAGE",SettingsSearch.find(entries,"damage size").getFirst().page());
        assertTrue(SettingsSearch.find(entries,"does not exist").isEmpty());assertTrue(SettingsSearch.find(entries,"").isEmpty());assertTrue(SettingsSearch.find(entries,null).isEmpty());
    }
    @Test void supportsCommonChineseKeywordsAndFullWidthInput(){
        assertEquals(1,SettingsSearch.find(entries,"伤害 大小").size());assertEquals(1,SettingsSearch.find(entries,"挥动速度").size());
        assertEquals(2,SettingsSearch.find(entries,"location 追踪").size());assertEquals(1,SettingsSearch.find(entries,"Ｓｗｉｎｇ　Ｓｐｅｅｄ").size());
        assertTrue(SettingsSearch.find(entries,"...___").isEmpty());
        var nested=List.of(new SettingsSearch.Entry("NickTweaks","Shimmer Size","Requires Shimmer","COLOR",0),new SettingsSearch.Entry("NickTweaks","Save Current Colors","Preset Name","PRESETS",0));
        assertEquals("COLOR",SettingsSearch.find(nested,"鎏金大小").getFirst().page());assertEquals("PRESETS",SettingsSearch.find(nested,"预设").getFirst().page());
    }
    @Test void findsBatchReplacementThroughChineseSearch(){var map=List.of(new SettingsSearch.Entry("Map Editor","Replace Materials","","MAP",500));assertEquals("MAP",SettingsSearch.find(map,"地图替换材质").getFirst().page());assertEquals(1,SettingsSearch.find(map,"替换").size());}
}
