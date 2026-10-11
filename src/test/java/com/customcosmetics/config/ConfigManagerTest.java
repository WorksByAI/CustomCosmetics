package com.customcosmetics.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @Test void swingSpeedDefaultAndResetMatchNoammsNeutralZeroRatherThanTheAxesUnitMultiplier() {
        ConfigManager.resetAnimations(); assertEquals(0,ConfigManager.animationSwingSpeed);
        assertEquals(1,ConfigManager.animationSwingX); assertEquals(1,ConfigManager.animationSwingY); assertEquals(1,ConfigManager.animationSwingZ);
        ConfigManager.animationSwingSpeed = 1; ConfigManager.resetAnimations(); assertEquals(0,ConfigManager.animationSwingSpeed);
    }
    @TempDir Path directory;
    @Test void nicknameIsLimitedWhenReadingAndSavingConfiguration() throws Exception {
        var path = directory.resolve("config.json");
        String value = "&d" + "昵称".repeat(30);
        ConfigManager.nickTweaksNickChanger = value; ConfigManager.save(path);
        assertEquals(value.substring(0,40), ConfigManager.nickTweaksNickChanger);
        assertEquals(value.substring(0,40), JsonParser.parseString(Files.readString(path)).getAsJsonObject()
            .getAsJsonObject("settings").get("nickTweaksNickChanger").getAsString());
        Files.writeString(path,"{\"settings\":{\"nickTweaksNickChanger\":\"" + value + "\"}}");
        ConfigManager.load(path); assertEquals(value.substring(0,40), ConfigManager.nickTweaksNickChanger);
    }
    private final Map<Field,Object> original = new HashMap<>();
    @BeforeEach void isolate() throws Exception {
        for (var field : ConfigManager.class.getFields()) if (Modifier.isStatic(field.getModifiers())) original.put(field,field.get(null));
    }
    @AfterEach void restore() throws Exception { for (var entry : original.entrySet()) entry.getKey().set(null,entry.getValue()); }
    @Test void freshDefaultsDisableRemoteListFilteringAndNotificationsAndUseThePublicDomain() {
        assertFalse(ConfigManager.swordBlockingListsApplyToOthers); assertFalse(ConfigManager.syncNotifications);
        assertEquals("cc.toryhi.com",ConfigManager.syncServerIp); assertEquals("cc.toryhi.com",ConfigManager.syncUdpHost);
        assertEquals(443,ConfigManager.syncServerPort); assertEquals(25578,ConfigManager.syncUdpPort);
        assertTrue(ConfigManager.syncServerHttps); assertTrue(ConfigManager.syncServerSni); assertEquals("wss",ConfigManager.syncBlockingTransport);
    }
    @Test void mainConfigurationDoesNotSaveConnectionsOrDuplicateFriends() throws Exception {
        var path = directory.resolve("config.json"); ConfigManager.save(path);
        var settings = JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("settings");
        for (String key : new String[]{"syncServerIp","syncServerPort","syncServerHttps","syncServerSni","syncUdpHost","syncUdpPort","smolFriendList","smolFriendsEnabled"})
            assertFalse(settings.has(key),key);
        for (String key : new String[]{"syncEnabled","syncNotifications","syncProxyHost","syncProxyPort","syncProxyAuth","syncProxyFallbackDirect","syncProxySource"})
            assertTrue(settings.has(key),key);
        assertFalse(Files.readString(path).toLowerCase().contains("baity"));
    }
    @Test void mainConfigurationCannotOverrideTheRuntimeService() throws Exception {
        var path = directory.resolve("config.json");
        Files.writeString(path,"{\"settings\":{\"syncServerIp\":\"unexpected.example\",\"syncServerPort\":1,\"syncServerHttps\":false,\"syncServerSni\":true,\"syncUdpHost\":\"unexpected.example\",\"syncUdpPort\":2}}");
        var before = RemoteServerConfig.current(); ConfigManager.load(path); assertEquals(before,RemoteServerConfig.current());
    }
    @Test void cosmeticAnimationListsAndDeveloperSettingsRoundTrip() throws Exception {
        var path = directory.resolve("config.json");
        ConfigManager.swordBlockingListsApplyToOthers = true; ConfigManager.syncNotifications = true;
        ConfigManager.animationItemScale = 1.7; ConfigManager.animationX = .3;
        ConfigManager.animationDisableHandMove = true; ConfigManager.animationTerminatorOnly = true;
        ConfigManager.swordBlockingListMode = "whitelist";
        ConfigManager.swordBlockingBlacklist = new String[]{"skyblock:ASPECT_OF_THE_VOID"};
        ConfigManager.swordBlockingWhitelist = new String[]{"minecraft:bow","skyblock:TERMINATOR"};
        ConfigManager.syncProxyHost = "127.0.0.1"; ConfigManager.syncProxyPort = 7890;
        ConfigManager.syncProxyAuth = "test:fixture"; ConfigManager.syncProxyFallbackDirect = false; ConfigManager.syncProxySource = "manual";
        ConfigManager.save(path); ConfigManager.resetAnimations();
        ConfigManager.swordBlockingListsApplyToOthers = false; ConfigManager.syncNotifications = false;
        ConfigManager.swordBlockingListMode = "off"; ConfigManager.swordBlockingWhitelist = new String[0];
        ConfigManager.syncProxyHost = ""; ConfigManager.syncProxyPort = 0; ConfigManager.syncProxyAuth = "";
        ConfigManager.syncProxyFallbackDirect = true; ConfigManager.syncProxySource = "none";
        ConfigManager.load(path);
        assertTrue(ConfigManager.swordBlockingListsApplyToOthers); assertTrue(ConfigManager.syncNotifications);
        assertEquals(1.7,ConfigManager.animationItemScale); assertEquals(.3,ConfigManager.animationX);
        assertTrue(ConfigManager.animationDisableHandMove); assertTrue(ConfigManager.animationTerminatorOnly);
        assertEquals("whitelist",ConfigManager.swordBlockingListMode);
        assertArrayEquals(new String[]{"minecraft:bow","skyblock:TERMINATOR"},ConfigManager.swordBlockingWhitelist);
        assertEquals("127.0.0.1",ConfigManager.syncProxyHost); assertEquals(7890,ConfigManager.syncProxyPort);
        assertEquals("test:fixture",ConfigManager.syncProxyAuth); assertFalse(ConfigManager.syncProxyFallbackDirect); assertEquals("manual",ConfigManager.syncProxySource);
    }
    @Test void invalidFeatureValuesAreClampedWithoutChangingConnections() throws Exception {
        var path = directory.resolve("config.json"); var endpoint = RemoteServerConfig.current();
        Files.writeString(path,"{\"settings\":{\"animationItemScale\":-3,\"animationX\":12,\"animationSwingSpeed\":10,\"smolLimbSwingSpeed\":99,\"syncRefreshIntervalSeconds\":2}}");
        ConfigManager.load(path); assertEquals(.1,ConfigManager.animationItemScale); assertEquals(2,ConfigManager.animationX);
        assertEquals(1,ConfigManager.animationSwingSpeed); assertEquals(5,ConfigManager.smolLimbSwingSpeed);
        assertEquals(30,ConfigManager.syncRefreshIntervalSeconds); assertEquals(endpoint,RemoteServerConfig.current());
    }
    @Test void blockingPreferencesAreIndependent() throws Exception {
        var path = directory.resolve("config.json"); ConfigManager.swordBlockingEnabled = true; ConfigManager.swordBlockingSyncEnabled = false;
        ConfigManager.save(path); ConfigManager.swordBlockingEnabled = false; ConfigManager.swordBlockingSyncEnabled = true;
        ConfigManager.load(path); assertTrue(ConfigManager.swordBlockingEnabled); assertFalse(ConfigManager.swordBlockingSyncEnabled);
    }
    @Test void firstPersonStylePersistsLocallyAndUnknownValuesFallBackToDefault() throws Exception {
        var path = directory.resolve("config.json");
        ConfigManager.swordBlockingStyle = "jigsaw"; ConfigManager.save(path);
        ConfigManager.swordBlockingStyle = "default"; ConfigManager.load(path); assertEquals("jigsaw",ConfigManager.swordBlockingStyle);
        Files.writeString(path,"{\"settings\":{\"swordBlockingStyle\":\"nonexistent\"}}");
        ConfigManager.load(path); assertEquals("default",ConfigManager.swordBlockingStyle);
    }
}
