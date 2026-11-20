package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.gameplay.dungeonapi.DeviceConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheatingConfigsTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private Object previousDirectory;
    @org.junit.jupiter.api.BeforeEach void initDirectory() throws Exception {
        var field=com.customcosmetics.config.CosmeticsConfigDir.class.getDeclaredField("cosmeticsConfigDir");
        field.setAccessible(true);previousDirectory=field.get(null);field.set(null,directory);
    }
    @org.junit.jupiter.api.AfterEach void restoreDirectory() throws Exception {
        var field=com.customcosmetics.config.CosmeticsConfigDir.class.getDeclaredField("cosmeticsConfigDir");
        field.setAccessible(true);field.set(null,previousDirectory);
    }
    @Test void savedFileCanBeLoadedAndRemoved() throws Exception {
        int old=ConfigManager.experimentMinDelay;
        try {
            ConfigManager.experimentMinDelay=123;
            CheatingConfigs.run("save","f7");
            ConfigManager.experimentMinDelay=456;
            CheatingConfigs.run("load","f7");
            assertEquals(123,ConfigManager.experimentMinDelay);
            assertEquals(java.util.List.of("f7"),CheatingConfigs.list());
            CheatingConfigs.run("remove","f7");assertTrue(CheatingConfigs.list().isEmpty());
        } finally {ConfigManager.experimentMinDelay=old;}
    }
    @Test void profilesIncludeEveryLocalSettingWithoutAppearanceOrConnections() {
        var profile = CheatingConfigs.capture().getAsJsonObject("local");
        assertTrue(profile.has("autoClickerLeftItems"));
        assertTrue(profile.has("scannerOptions"));
        assertTrue(profile.has("vanguardCorpseColor"));
        assertTrue(profile.has("enabledModulesColor"));
        assertTrue(profile.has("enabledModulesChroma"));
        assertTrue(profile.has("gameplayKeybinds"));
        assertFalse(profile.has("gameplayPanelConfirmed"));
        assertFalse(profile.has("nickTweaksEnabled"));
        assertFalse(profile.has("syncP2pPort"));
    }

    @Test void loadingLegacyProfileRewritesDeviceSettingsInPlace() throws Exception {
        var before = CheatingConfigs.capture();
        var file = CheatingConfigs.path("old");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            {"format":1,"local":{"autoI4RotationTime":114,"autoI4Rod":true,
              "gameplayModules":["AUTO_I4","AUTO_GFS"],
              "gameplayKeybinds":{"key.keyboard.g":"AUTO_I4","key.keyboard.h":"AUTO_GFS"}}}
            """);
        try {
            CheatingConfigs.run("load", "old");
            var migrated = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals(2, migrated.get("format").getAsInt());
            var local = migrated.getAsJsonObject("local");
            assertFalse(local.has("autoI4RotationTime"));
            assertEquals("[\"AUTO_GFS\"]", local.get("gameplayModules").toString());
            assertFalse(local.getAsJsonObject("gameplayKeybinds").has("key.keyboard.g"));
            var device = migrated.getAsJsonObject("device");
            assertTrue(device.get("enabled").getAsBoolean());
            var i4 = device.getAsJsonObject("Auto I4");
            assertTrue(i4.get("enabled").getAsBoolean());
            assertEquals("key.keyboard.g", i4.get("keybind").getAsString());
            assertEquals(114, ConfigManager.autoI4RotationTime);
            assertTrue(ConfigManager.autoI4Rod);
            ConfigManager.autoI4RotationTime = 20;
            CheatingConfigs.run("load", "old");
            assertEquals(114, ConfigManager.autoI4RotationTime);
            assertEquals(GameplayModule.AUTO_I4, ConfigManager.gameplayKeybinds.get("key.keyboard.g"));
            assertEquals(GameplayModule.AUTO_GFS, ConfigManager.gameplayKeybinds.get("key.keyboard.h"));
            ConfigManager.gameplayDeviceEnabled = false;
            ConfigManager.gameplayKeybinds = java.util.Map.of("key.keyboard.g", GameplayModule.AUTO_I4,
                "key.keyboard.j", GameplayModule.DEVICE);
            CheatingConfigs.run("save", "old");
            ConfigManager.gameplayDeviceEnabled = true;
            CheatingConfigs.run("load", "old");
            assertFalse(ConfigManager.gameplayDeviceEnabled);
            assertTrue(GameplayModule.AUTO_I4.configured());
            assertEquals(GameplayModule.DEVICE, ConfigManager.gameplayKeybinds.get("key.keyboard.j"));
        } finally {
            CheatingConfigs.apply(before);
        }
    }

    @Test void malformedProfileIsRejectedBeforeChangingAnyFields() {
        int old = ConfigManager.experimentMinDelay;
        var profile = new JsonObject(); profile.addProperty("format",1);
        var local = new JsonObject(); local.addProperty("experimentMinDelay",17);
        local.addProperty("autoI4RotationTime","invalid"); profile.add("local",local);
        assertThrows(RuntimeException.class,()->CheatingConfigs.apply(profile));
        assertEquals(old,ConfigManager.experimentMinDelay);
    }

    @Test void startupInfersDeviceFromLegacyDungeonAndHonorsAnExplicitOffSetting() throws Exception {
        var legacy = directory.resolve("dungeon/settings.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, """
            [{"name":"Lights Device","enabled":true,"settings":{}}]
            """);
        var local = new JsonObject();
        assertTrue(DeviceConfig.restoreStartup(directory, local, new JsonObject()));
        assertTrue(local.get("gameplayDeviceEnabled").getAsBoolean());

        var device = JsonParser.parseString("""
            {"enabled":false,"Light Device":{"enabled":true}}
            """).getAsJsonObject();
        assertFalse(DeviceConfig.restoreStartup(directory, new JsonObject(), device));
        assertFalse(device.get("enabled").getAsBoolean());
        assertTrue(device.getAsJsonObject("Light Device").get("enabled").getAsBoolean());
    }

    @Test void filenameValidationRejectsPathsAndAcceptsJsonSuffix() {
        assertThrows(IllegalArgumentException.class,()->CheatingConfigs.path("../outside"));
        assertThrows(IllegalArgumentException.class,()->CheatingConfigs.path("C:\\outside"));
        assertThrows(IllegalArgumentException.class,()->CheatingConfigs.path("nested/file"));
        assertEquals(CheatingConfigs.path("Dungeon F7"),CheatingConfigs.path("Dungeon F7.json"));
    }
}
