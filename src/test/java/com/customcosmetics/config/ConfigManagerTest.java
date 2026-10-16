package com.customcosmetics.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @Test void nearbyNametagHidingDefaultsToEightAndPersistsIndependentlyOfOtherPartySettings()throws Exception{
        assertEquals(8,ConfigManager.partyNametagHideDistance);var path=directory.resolve("nearby-nametags.json");
        ConfigManager.partyNametagHideDistance=12;ConfigManager.save(path);ConfigManager.partyNametagHideDistance=0;ConfigManager.load(path);
        assertEquals(12,ConfigManager.partyNametagHideDistance);assertTrue(ConfigManager.partyShowNames);assertTrue(ConfigManager.partyEspEnabled);assertEquals(0,ConfigManager.partyMaxDisplayDistance);
        ConfigManager.partyNametagHideDistance=0;ConfigManager.save(path);ConfigManager.partyNametagHideDistance=8;ConfigManager.load(path);assertEquals(0,ConfigManager.partyNametagHideDistance);
        Files.writeString(path,"{\"settings\":{\"partyNametagHideDistance\":-10}}");ConfigManager.load(path);assertEquals(0,ConfigManager.partyNametagHideDistance);
        Files.writeString(path,"{\"settings\":{\"partyNametagHideDistance\":99999999}}");ConfigManager.load(path);assertEquals(30000000,ConfigManager.partyNametagHideDistance);
    }
    @Test void localP2pPortDefaultsToAutomaticPersistsAndRejectsOutOfRangeValues()throws Exception{
        assertEquals(0,ConfigManager.getSyncP2pPort());var path=directory.resolve("p2p-port.json");
        for(int port:new int[]{1,45566,65535,0}){
            ConfigManager.syncP2pPort=port;ConfigManager.save(path);ConfigManager.syncP2pPort=12345;ConfigManager.load(path);
            assertEquals(port,ConfigManager.getSyncP2pPort());
        }
        for(int port:new int[]{-1,65536,Integer.MAX_VALUE}){
            Files.writeString(path,"{\"settings\":{\"syncP2pPort\":"+port+"}}");ConfigManager.load(path);assertEquals(0,ConfigManager.syncP2pPort);
        }
    }
    @Test void locationSharingDefaultsOffAndSelectedRecipientsSurviveRestart() throws Exception {
        assertFalse(ConfigManager.positionSharingEnabled);assertFalse(ConfigManager.positionSharingReceive);
        var path=directory.resolve("location-sharing.json");var id=UUID.randomUUID();
        ConfigManager.positionSharingEnabled=true;ConfigManager.positionSharingReceive=true;ConfigManager.positionSharingMode="selected";
        ConfigManager.positionSharingTargets=List.of(new PlayerBlocklist.Entry(id,"Alice"),new PlayerBlocklist.Entry(null,"Bob"));ConfigManager.save(path);
        ConfigManager.positionSharingEnabled=false;ConfigManager.positionSharingReceive=false;ConfigManager.positionSharingMode="public";ConfigManager.positionSharingTargets=List.of();
        ConfigManager.load(path);assertTrue(ConfigManager.positionSharingEnabled);assertTrue(ConfigManager.positionSharingReceive);assertEquals("selected",ConfigManager.positionSharingMode);
        assertEquals(id,ConfigManager.positionSharingTargets.getFirst().uuid());assertEquals("Bob",ConfigManager.positionSharingTargets.getLast().account());
        Files.writeString(path,"{\"settings\":{\"positionSharingMode\":\"invalid\",\"positionSharingTargets\":[{\"account\":\"ab\"},{\"account\":\"bad name\"}]}}");
        ConfigManager.load(path);assertEquals("selected",ConfigManager.positionSharingMode);assertTrue(ConfigManager.positionSharingTargets.isEmpty());
    }
    @Test void nicknameBlocksRoundTripAsLocalAccountNamesAndStableIdentities() throws Exception {
        var path=directory.resolve("nickname-blocks.json");
        var uuid=UUID.fromString("12345678-1234-4234-8234-123456789abc");
        var expected=List.of(new com.customcosmetics.config.PlayerBlocklist.Entry(null,"Alice"),new com.customcosmetics.config.PlayerBlocklist.Entry(uuid,"Bob"));
        ConfigManager.nickTweaksBlockedPlayers=expected;ConfigManager.accessoriesBlockedPlayers=List.of(expected.getLast());ConfigManager.capeBlockedPlayers=List.of(expected.getFirst());ConfigManager.save(path);ConfigManager.nickTweaksBlockedPlayers=List.of();ConfigManager.accessoriesBlockedPlayers=List.of();ConfigManager.capeBlockedPlayers=List.of();ConfigManager.load(path);
        assertEquals(expected,ConfigManager.nickTweaksBlockedPlayers);
        assertEquals(List.of(expected.getLast()),ConfigManager.accessoriesBlockedPlayers);
        assertEquals(List.of(expected.getFirst()),ConfigManager.capeBlockedPlayers);
        Files.writeString(path,"{\"settings\":{\"nickTweaksBlockedPlayers\":[null,{\"account\":\"Bad Name\"},{\"account\":\" Alice \"},{\"account\":\"ALICE\"}]}}");
        ConfigManager.load(path);assertEquals(List.of(expected.getFirst()),ConfigManager.nickTweaksBlockedPlayers);
    }
    @Test void namesOnlyEspStylePersistsWithoutDisablingPositionSharing() throws Exception {
        var path=directory.resolve("party-names-only.json");
        ConfigManager.partyEspEnabled=true;ConfigManager.partyEspStyle="none";ConfigManager.save(path);
        ConfigManager.partyEspStyle="outline";ConfigManager.load(path);
        assertEquals("none",ConfigManager.partyEspStyle);assertTrue(ConfigManager.partyEspEnabled);
        assertTrue(com.customcosmetics.features.party.PartyFeatures.sharingPositions());
        assertTrue(ConfigManager.partyShowNames);assertTrue(ConfigManager.partyFarMarkers);
    }
    @Test void partyP2pDefaultsOnAndExplicitOptOutPersists() throws Exception {
        assertTrue(ConfigManager.partyP2pEnabled);var path=directory.resolve("party-p2p.json");
        ConfigManager.partyP2pEnabled=false;ConfigManager.save(path);ConfigManager.partyP2pEnabled=true;ConfigManager.load(path);assertFalse(ConfigManager.partyP2pEnabled);
    }
    @Test void tracersAreOptionalAndSaveTheirOwnColor() throws Exception {
        assertFalse(ConfigManager.partyTracersEnabled);var path=directory.resolve("party-tracers.json");
        ConfigManager.partyTracersEnabled=true;ConfigManager.partyTracerColor=0x12ABEF;ConfigManager.save(path);
        ConfigManager.partyTracersEnabled=false;ConfigManager.partyTracerColor=0;ConfigManager.load(path);
        assertTrue(ConfigManager.partyTracersEnabled);assertEquals(0x12ABEF,ConfigManager.partyTracerColor);
        assertEquals(0xFFA6CF,ConfigManager.partyEspColor);
    }
    @Test void partySettingsDefaultOnAndRoundTripWithValidatedRenderingBounds() throws Exception {
        assertTrue(ConfigManager.partyEspEnabled);assertTrue(ConfigManager.partyChatEnabled);
        assertEquals("`",ConfigManager.partyChatPrefix);assertEquals("fixed",ConfigManager.partyNametagMode);assertEquals(1,ConfigManager.partyNametagSize);
        var path=directory.resolve("party.json");
        ConfigManager.partyEspEnabled=true;ConfigManager.partyChatEnabled=true;ConfigManager.partyEspStyle="both";
        ConfigManager.partyEspColor=0x12ABEF;ConfigManager.partyEspOpacity=.35;ConfigManager.partyEspLineWidth=2.5;
        ConfigManager.partyFarMarkers=false;ConfigManager.partyShowNames=false;ConfigManager.partyChatPrefix="!!";
        ConfigManager.partyNametagMode="distance";ConfigManager.partyNametagSize=1.7;ConfigManager.save(path);
        ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;ConfigManager.partyEspStyle="fill";
        ConfigManager.partyEspColor=0;ConfigManager.partyEspOpacity=.1;ConfigManager.partyEspLineWidth=1;
        ConfigManager.partyFarMarkers=true;ConfigManager.partyShowNames=true;ConfigManager.partyChatPrefix="`";
        ConfigManager.partyNametagMode="fixed";ConfigManager.partyNametagSize=1;ConfigManager.load(path);
        assertTrue(ConfigManager.partyEspEnabled);assertTrue(ConfigManager.partyChatEnabled);assertEquals("both",ConfigManager.partyEspStyle);
        assertEquals(0x12ABEF,ConfigManager.partyEspColor);assertEquals(.35,ConfigManager.partyEspOpacity);assertEquals(2.5,ConfigManager.partyEspLineWidth);
        assertFalse(ConfigManager.partyFarMarkers);assertFalse(ConfigManager.partyShowNames);
        assertEquals("!!",ConfigManager.partyChatPrefix);assertEquals("distance",ConfigManager.partyNametagMode);assertEquals(1.7,ConfigManager.partyNametagSize);
        Files.writeString(path,"{\"settings\":{\"partyEspStyle\":\"invalid\",\"partyEspOpacity\":-2,\"partyEspLineWidth\":100,\"partyNametagMode\":\"invalid\",\"partyNametagSize\":100}}");ConfigManager.load(path);
        assertEquals("outline",ConfigManager.partyEspStyle);assertEquals(.05,ConfigManager.partyEspOpacity);assertEquals(4,ConfigManager.partyEspLineWidth);
        assertEquals("fixed",ConfigManager.partyNametagMode);assertEquals(3,ConfigManager.partyNametagSize);
    }
    @Test void explicitPartyOptOutAndEmptyChatPrefixRemainSaved() throws Exception {
        var path=directory.resolve("party-opt-out.json");ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;ConfigManager.partyChatPrefix="";ConfigManager.save(path);
        ConfigManager.partyEspEnabled=true;ConfigManager.partyChatEnabled=true;ConfigManager.partyChatPrefix="`";ConfigManager.load(path);
        assertFalse(ConfigManager.partyEspEnabled);assertFalse(ConfigManager.partyChatEnabled);assertEquals("",ConfigManager.partyChatPrefix);
    }
    @Test void partyConnectionNoticesHaveTheirOwnSavedSwitch() throws Exception {
        assertTrue(ConfigManager.partyConnectionNotifications);var path=directory.resolve("party-notices.json");
        ConfigManager.partyConnectionNotifications=false;ConfigManager.save(path);ConfigManager.partyConnectionNotifications=true;ConfigManager.load(path);
        assertFalse(ConfigManager.partyConnectionNotifications);assertTrue(ConfigManager.partyEspEnabled);assertTrue(ConfigManager.partyChatEnabled);
    }
    @Test void partyDisplayDistanceDefaultsToUnlimitedAndRoundTripsIndependently() throws Exception {
        assertEquals(0,ConfigManager.partyMaxDisplayDistance);var path=directory.resolve("party-distance.json");
        ConfigManager.partyMaxDisplayDistance=128;ConfigManager.save(path);ConfigManager.partyMaxDisplayDistance=0;ConfigManager.load(path);assertEquals(128,ConfigManager.partyMaxDisplayDistance);
        Files.writeString(path,"{\"settings\":{\"partyMaxDisplayDistance\":-1}}");ConfigManager.load(path);assertEquals(0,ConfigManager.partyMaxDisplayDistance);
        Files.writeString(path,"{\"settings\":{\"partyMaxDisplayDistance\":99999999}}");ConfigManager.load(path);assertEquals(30000000,ConfigManager.partyMaxDisplayDistance);
    }
    @Test void accessoriesRoundTripPreservesDisabledDraftsAndIndependentColors() throws Exception {
        var path=directory.resolve("accessories.json");
        var expected=com.customcosmetics.features.accessories.AccessoriesSettings.NONE.with("wings",com.customcosmetics.features.accessories.AccessoriesSettings.WINGS.enabled(true).color(0x123456)).with("ears",com.customcosmetics.features.accessories.AccessoriesSettings.EARS.scale(1.5));
        ConfigManager.accessories=expected;ConfigManager.save(path);ConfigManager.accessories=com.customcosmetics.features.accessories.AccessoriesSettings.NONE;ConfigManager.load(path);assertEquals(expected,ConfigManager.accessories);
        var extra=expected.with("hat",expected.part("hat").enabled(true).style("witch").color(0xDDBBCC)).with("tail",expected.part("tail").enabled(true).style("kitsune"));
        ConfigManager.accessories=extra;ConfigManager.save(path);ConfigManager.accessories=com.customcosmetics.features.accessories.AccessoriesSettings.NONE;ConfigManager.load(path);assertEquals(extra,ConfigManager.accessories);
    }
    @Test void playerSizeSettingsRoundTripAndInvalidAxesCannotEnterRendering() throws Exception {
        var path=directory.resolve("player-size.json");ConfigManager.playerSizeEnabled=true;ConfigManager.playerSizeX=.5;ConfigManager.playerSizeY=.75;ConfigManager.playerSizeZ=1.25;
        ConfigManager.save(path);ConfigManager.playerSizeEnabled=false;ConfigManager.playerSizeX=ConfigManager.playerSizeY=ConfigManager.playerSizeZ=1;ConfigManager.load(path);
        assertTrue(ConfigManager.playerSizeEnabled);assertEquals(.5,ConfigManager.playerSizeX);assertEquals(.75,ConfigManager.playerSizeY);assertEquals(1.25,ConfigManager.playerSizeZ);
        Files.writeString(path,"{\"settings\":{\"playerSizeX\":-2,\"playerSizeY\":100}}");ConfigManager.load(path);assertEquals(.1,ConfigManager.playerSizeX);assertEquals(3,ConfigManager.playerSizeY);
    }
    @Test void segmentedGradientConfigurationLoadsTypedRangesAndPreservesTheirColors() throws Exception {
        var path=directory.resolve("segments.json");
        var segments=List.of(new com.customcosmetics.features.nickname.NicknameSegments.Segment(1,4,0xED69E8,0x916BE8),new com.customcosmetics.features.nickname.NicknameSegments.Segment(5,6,0x43D6EC,0x549FF5));
        ConfigManager.nickTweaksGradientStyle="segmented";ConfigManager.nickTweaksGradientSegments=segments;ConfigManager.save(path);
        ConfigManager.nickTweaksGradientStyle="smooth";ConfigManager.nickTweaksGradientSegments=List.of();ConfigManager.load(path);
        assertEquals("segmented",ConfigManager.nickTweaksGradientStyle);assertEquals(segments,ConfigManager.nickTweaksGradientSegments);
        assertThrows(UnsupportedOperationException.class,()->ConfigManager.nickTweaksGradientSegments.clear());
    }
    @Test void offlineAndBlockingAdjustmentsRoundTrip() throws Exception {
        var path=directory.resolve("studio.json");
        ConfigManager.offlineMode=true;
        ConfigManager.blockingX=.2;ConfigManager.blockingY=-.3;ConfigManager.blockingZ=.4;
        ConfigManager.blockingRotationX=15;ConfigManager.blockingRotationY=-25;ConfigManager.blockingRotationZ=35;
        ConfigManager.save(path);
        ConfigManager.offlineMode=false;
        com.customcosmetics.features.blocking.BlockingAdjustment.reset();
        ConfigManager.load(path);
        assertTrue(ConfigManager.offlineMode);
        assertEquals(.2,ConfigManager.blockingX);assertEquals(-.3,ConfigManager.blockingY);assertEquals(.4,ConfigManager.blockingZ);
        assertEquals(15,ConfigManager.blockingRotationX);assertEquals(-25,ConfigManager.blockingRotationY);assertEquals(35,ConfigManager.blockingRotationZ);
    }
    @Test void nicknameGradientOptionsRoundTripAndInvalidStylesFallBackSafely() throws Exception {
        var path=directory.resolve("shimmer.json");
        ConfigManager.nickTweaksGradientStyle="shimmer";ConfigManager.nickTweaksShimmerSpeed=1.6;
        ConfigManager.nickTweaksShimmerSize=.35;ConfigManager.nickTweaksShimmerInterval=4.7;
        ConfigManager.nickTweaksShimmerColor=0x0055FF;ConfigManager.nickTweaksShimmerHighlightColor=0x66DDFF;
        ConfigManager.save(path);ConfigManager.nickTweaksShimmerColor=0;ConfigManager.nickTweaksShimmerHighlightColor=0;ConfigManager.nickTweaksGradientStyle="character";ConfigManager.nickTweaksShimmerSpeed=0;
        ConfigManager.load(path);assertEquals("shimmer",ConfigManager.nickTweaksGradientStyle);assertEquals(1.6,ConfigManager.nickTweaksShimmerSpeed);
        assertEquals(.35,ConfigManager.nickTweaksShimmerSize);assertEquals(4.7,ConfigManager.nickTweaksShimmerInterval);
        assertEquals(0x0055FF,ConfigManager.nickTweaksShimmerColor);assertEquals(0x66DDFF,ConfigManager.nickTweaksShimmerHighlightColor);
        Files.writeString(path,"{\"settings\":{\"nickTweaksGradientStyle\":\"unexpected\",\"nickTweaksShimmerSpeed\":90}}");
        ConfigManager.load(path);assertEquals("character",ConfigManager.nickTweaksGradientStyle);assertEquals(4,ConfigManager.nickTweaksShimmerSpeed);
    }
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
        assertTrue(ConfigManager.syncServerHttps); assertTrue(ConfigManager.syncServerSni); assertEquals("auto",ConfigManager.syncBlockingTransport);
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
