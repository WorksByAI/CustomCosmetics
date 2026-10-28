package com.customcosmetics.gui;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
class UiTextTest {
    @AfterEach void reset(){UiText.configure("auto","en_us");}
    @Test void independentOverrideFollowsGameOnlyWhenRequested(){
        UiText.configure("auto","zh_cn");assertEquals("设置",UiText.tr("Settings"));
        UiText.configure("en_us","zh_cn");assertEquals("Settings",UiText.tr("Settings"));
        UiText.configure("zh_cn","en_us");assertEquals("设置",UiText.tr("Settings"));
        UiText.configure("unsupported","en_us");assertEquals("en_us",UiText.language());
    }
    @Test void ownedTemplatesTranslateWithoutEditingTheirUserValues(){
        UiText.configure("zh_cn","en_us");
        assertEquals("应用：Settings",UiText.tr("Apply: Settings"));
        assertEquals("Settings",UiText.display("Settings",true));
        assertEquals("设置",UiText.display("Settings",false));
        assertEquals("minecraft:leather",UiText.tr("minecraft:leather"));
        assertEquals("/warp home",UiText.tr("/warp home"));
        assertEquals("模式：深色",UiText.tr("Mode: Dark"));
        assertEquals("✓ 绵羊",UiText.tr("✓ Sheep"));
        assertEquals("快速切换视角 · 设置",UiText.tr("Snaplook · Settings"));
    }
    @Test void languageRevisionChangesOnlyWhenEffectiveLanguageChanges(){
        UiText.configure("en_us","en_us");int start=UiText.revision();
        for(int i=0;i<1000;i++)UiText.configure("auto","en_us");assertEquals(start,UiText.revision());
        UiText.configure("zh_cn","en_us");assertEquals(start+1,UiText.revision());
    }
    @Test void bothCataloguesHaveMatchingKeysAndNonblankEntries()throws Exception{
        var en=read("en_us");var zh=read("zh_cn");assertEquals(en.keySet(),zh.keySet());
        assertTrue(en.size()>800);for(String key:en.keySet()){assertFalse(en.get(key).getAsString().isBlank(),key);assertFalse(zh.get(key).getAsString().isBlank(),key);}
    }
    @Test void translatedSearchWorksInBothInterfaceLanguages(){
        for(String language:new String[]{"en_us","zh_cn"}){
            UiText.configure(language,"en_us");assertTrue(SettingsCatalog.find("语言").stream().anyMatch(e->e.title().equals("Language")));
            assertTrue(SettingsCatalog.find("快速切换视角").stream().anyMatch(e->e.title().equals("Snaplook")));
            assertTrue(SettingsCatalog.find("护符").stream().anyMatch(e->e.title().equals("Accessory Helper Tooltip")));
            assertTrue(SettingsCatalog.find("Snaplook").stream().anyMatch(e->e.title().equals("Snaplook")));
        }
    }
    private static JsonObject read(String name)throws Exception{try(var in=UiTextTest.class.getResourceAsStream("/assets/customcosmetics/lang/"+name+".json")){return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}}
}
