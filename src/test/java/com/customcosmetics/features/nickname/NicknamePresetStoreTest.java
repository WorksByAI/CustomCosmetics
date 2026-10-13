// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.ConfigManager;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NicknamePresetStoreTest {
    @TempDir Path directory;
    private final Map<Field,Object> original = new HashMap<>();
    @BeforeEach void isolate() throws Exception {
        for (var field : ConfigManager.class.getFields()) if (Modifier.isStatic(field.getModifiers())) original.put(field,field.get(null));
    }
    @AfterEach void restore() throws Exception { for (var entry : original.entrySet()) entry.getKey().set(null,entry.getValue()); }
    private NicknameColors sample() {
        return new NicknameColors(0x123456,0x654321,"shimmer",1.7,0x59D9E8,0xC6F5FF,true,2,4,.3,.9);
    }
    @Test void namedPresetsSurviveRestartAndCaseInsensitiveUpdateAndDeletion() throws Exception {
        var path = directory.resolve("presets.json"); var store = new NicknamePresetStore(path);
        store.put("  ToryHi  ",sample());
        var reloaded = new NicknamePresetStore(path); reloaded.load();
        assertEquals(Map.of("ToryHi",sample()),reloaded.entries());
        var other = new NicknameColors(1,2,"smooth",0,3,4,false,1,2,.1,.6);
        reloaded.put("toryhi",other); assertEquals(Map.of("ToryHi",other),reloaded.entries());
        store.load(); assertEquals(other,store.entries().get("ToryHi"));
        reloaded.remove("TORYHI"); store.load(); assertTrue(store.entries().isEmpty());
        assertFalse(Files.readString(path).contains("nickChanger"));
        try (var files = Files.list(directory)) { assertEquals(1,files.count()); }
    }
    @Test void applyingColorsPreservesNicknameBoldAndOtherModules() {
        ConfigManager.nickTweaksNickChanger = "&dToryHi"; ConfigManager.nickTweaksBoldSelf = true;
        ConfigManager.smolpeopleMode = true; ConfigManager.animationItemScale = 1.7;
        ConfigManager.syncEnabled = false; ConfigManager.nickTweaksEnabled = false;
        sample().apply(); assertEquals(sample(),NicknameColors.capture());
        assertTrue(ConfigManager.nickTweaksEnabled); assertTrue(ConfigManager.nickTweaksCustomNickColorEnabled);
        assertEquals("&dToryHi",ConfigManager.nickTweaksNickChanger); assertTrue(ConfigManager.nickTweaksBoldSelf);
        assertTrue(ConfigManager.smolpeopleMode); assertEquals(1.7,ConfigManager.animationItemScale); assertFalse(ConfigManager.syncEnabled);
    }
    @Test void invalidNamesAndCapacityDoNotOverwriteExistingPresets() throws Exception {
        var store = new NicknamePresetStore(directory.resolve("presets.json"));
        for (String bad : new String[]{"", "  ", "a\nb", "x".repeat(41)})
            assertThrows(IllegalArgumentException.class,()->store.put(bad,sample()));
        for (int i=0;i<64;i++) store.put("Preset "+i,sample());
        assertThrows(IllegalArgumentException.class,()->store.put("overflow",sample()));
        store.put("Preset 0",sample()); assertEquals(64,store.entries().size());
    }
    @Test void corruptRootIsNotOverwrittenButMalformedIndividualEntriesAreSkipped() throws Exception {
        var path = directory.resolve("presets.json"); var store = new NicknamePresetStore(path);
        store.put("Valid",sample()); var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        root.getAsJsonObject("presets").addProperty("Broken", "wrong type"); Files.writeString(path,root.toString());
        store.load(); assertEquals(Map.of("Valid",sample()),store.entries());
        Files.writeString(path,"broken"); assertThrows(IOException.class,store::load);
        assertThrows(IOException.class,()->store.put("New",sample())); assertEquals("broken",Files.readString(path));
        assertEquals(sample(),store.entries().get("Valid"));
    }
    @Test void failedDiskWriteLeavesThePreviousInMemoryPresetIntact() throws Exception {
        var path = directory.resolve("presets.json"); var store = new NicknamePresetStore(path);
        store.put("Saved",sample()); Files.delete(path); Files.createDirectory(path); Files.writeString(path.resolve("occupied"),"x");
        assertThrows(IOException.class,()->store.put("Unsaved",sample())); assertEquals(Map.of("Saved",sample()),store.entries());
    }
    @Test void untrustedColorsAndSpeedsNormalizeBeforeApplication() {
        var colors = new NicknameColors(0xFF123456,-1,"unknown",Double.NaN,-1,0xFF112233,true,999,0,Double.NaN,-3);
        assertEquals(0x123456,colors.start()); assertEquals(0xFFFFFF,colors.end());
        assertEquals(0xFFFFFF,colors.shimmerColor()); assertEquals(0x112233,colors.shimmerHighlightColor());
        assertEquals("character",colors.style()); assertEquals(1,colors.shimmerSpeed());
        assertEquals(8,colors.chromaSpeed()); assertEquals(.5,colors.chromaSize()); assertEquals(.2,colors.chromaAmount()); assertEquals(.2,colors.chromaLightness());
    }
}
