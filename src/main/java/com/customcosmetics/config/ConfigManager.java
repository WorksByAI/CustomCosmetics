/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.config;

import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.customcosmetics.sync.SyncEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** Local cosmetics, animation preferences and independent synchronization settings. */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomSkyBlock/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile long pendingSaveAt;

    public static boolean smolpeopleMode = false;
    public static volatile int disabledModules=0;
    public static String uiThemeMode="dark";
    public static String uiLanguage="auto";
    public static String uiFontPath = "";
    public static Boolean uiFontBold;
    public static float uiFontSize = 9, uiFontSpacing = 0;
    public static boolean droppedItems2dEnabled=false;
    public static boolean fastIpPingEnabled = false;
    public static boolean freecamEnabled = false, freecamCollision = false, freecamExitOnDamage = true;
    public static double freecamSpeed = 12, freecamVerticalSpeed = 12;
    public static String fullscreenStyle = "vanilla";
    public static boolean hitColorEnabled = false;
    public static int hitColor = 0xFF5555;
    public static float hitOpacity = 0.5f;
    public static boolean hitColorBody = true, hitColorArmor, hitColorTrims, hitColorItems, hitColorCape, hitColorChroma;
    public static int hitColorChromaSpeed = 50;
    public static boolean seymourEnabled, seymourFadeDyes = true;
    public static boolean tooltipHideGearScore, tooltipHideVanillaEnchants, tooltipShowAge, tooltipShowHex;
    public static boolean tooltipColorHex, tooltipColorBox = true, tooltipCakeYears, tooltipItemStars;
    public static boolean kuudraPeekCheckHeight = true, kuudraPeekFirstOnly;
    public static int tooltipStarColor = 0xFF5555;
    public static String tooltipAgeStyle = "&7Age: &c#age &8(#time)", tooltipHexStyle = "<gray>Color: #hex";
    public static boolean zoomEnabled, zoomToggle, zoomHands;
    public static boolean zoomScroll = true, zoomScrollBelowInitial = true, zoomSensitivity = true, zoomAnimate = true;
    public static double zoomFactor = 4, zoomInSeconds = 0.25, zoomOutSeconds = 0.25;
    public static boolean hudLayoutEnabled=false;
    public static java.util.EnumMap<com.customcosmetics.features.hud.HudLayout.Element,com.customcosmetics.features.hud.HudLayout.Options> hudLayout=
        new java.util.EnumMap<>(com.customcosmetics.features.hud.HudLayout.Element.class);
    public static boolean customSkyEnabled=false,customSkyDisc=true,customSkySunMoon=true,customSkyStars=false,customSkyFallback=true,customSkyDebug=false;
    public static int uiAccentColor=0xFFA8CA;
    public static boolean uiAnimationsEnabled=true;
    public static boolean uiBackgroundBlur=false;
    public static boolean inventoryBlurEnabled = false;
    public static boolean longSignTextEnabled = false;
    public static int inventoryDimPercent = 80;
    public static boolean hideHypixelSkyBlockPack = false;
    public static boolean hypixelPackTextColors = false;
    public static boolean hypixelPackTooltipBackground = false;
    public static boolean customTextColorsEnabled = false;
    public static java.util.Map<String, String> customTextColors = java.util.Map.of();
    public static boolean textReplacementsEnabled = false;
    public static java.util.Map<String, String> textReplacements = java.util.Map.of();
    public static java.util.Set<String> hypixelPackWhitelist = java.util.Set.of();
    public static java.util.Map<String, String> hypixelPackReplacements = java.util.Map.of();
    public static java.util.Map<String, Boolean> hypixelPackGlints = java.util.Map.of();
    public static boolean useFontInGame = true;
    public static int uiFontOversample = com.customcosmetics.gui.UiFonts.MAX_SAMPLING;
    public static float uiFontBaseline = 0;
    public static float uiFontShadow = 1;
    public static boolean damageSplashEnabled=false, damageSplashBold=true, damageSplashCompact=false;
    public static boolean damageSplashStyleNormal=false, damageSplashShadow=true;
    public static String damageSplashStyle="default", damageSplashSymbol="✧", damageSplashSeparator="comma";
    public static int damageSplashStartColor=0xFFFF55, damageSplashEndColor=0xFF5555;
    public static String damageSplashColorMode="gradient";
    public static double damageSplashRainbowSpeed=1;
    public static double damageSplashSize=1, damageSplashDuration=1.2;
    public static double smolLimbSwingSpeed = 2.5;
    public static com.customcosmetics.features.accessories.AccessoriesSettings accessories=com.customcosmetics.features.accessories.AccessoriesSettings.NONE;
    public static java.util.List<PlayerBlocklist.Entry> accessoriesBlockedPlayers = java.util.List.of();
    public static boolean playerSizeEnabled=false;
    public static double playerSizeX=1,playerSizeY=1,playerSizeZ=1;
    public static String creatureModel="player";
    public static boolean nickTweaksEnabled = false;
    public static String nickTweaksNickChanger = "";
    public static boolean nickTweaksRankEnabled = false;
    public static String nickTweaksRankPlusColor = "red";
    public static String nickTweaksRankType = "mvp_plus";
    public static String nickTweaksRankTagColor = "gold";
    public static java.util.List<com.customcosmetics.config.PlayerBlocklist.Entry> nickTweaksBlockedPlayers = java.util.List.of();
    public static boolean nickTweaksChromaEnabled = false;
    public static boolean nickTweaksCustomNickColorEnabled = false;
    public static boolean nickTweaksBoldSelf = false;
    public static double nickTweaksChromaLightness = 0.8;
    public static double nickTweaksChromaChroma = 0.2;
    public static double nickTweaksChromaSize = 3.1;
    public static double nickTweaksChromaSpeed = 1.0;
    public static int nickTweaksGradientStartColor = 0xFFFFFF;
    public static int nickTweaksGradientEndColor = 0xFFFFFF;
    public static String nickTweaksGradientStyle = "character";
    public static java.util.List<com.customcosmetics.features.nickname.NicknameSegments.Segment> nickTweaksGradientSegments = java.util.List.of();
    public static int nickTweaksShimmerColor = 0xFFCF64;
    public static int nickTweaksShimmerHighlightColor = 0xFFF0BB;
    public static double nickTweaksShimmerSpeed = 1.0;
    public static double nickTweaksShimmerSize = 0.16;
    public static double nickTweaksShimmerInterval = 2;
    public static volatile boolean offlineMode = false;
    public static volatile boolean updateCheckEnabled = true;
    public static boolean updateOnNextLaunch=false;
    public static boolean commandTooltipsEnabled=true;
    public static boolean accessoryHelperTooltipEnabled=false;
    public static boolean waypointShowLabels=true,waypointShowDistance=false;
    public static boolean waypointRenderLine=true,waypointAllowSkipping=true,waypointAllowBackwards=true;
    public static double waypointLineWidth=5;
    public static int waypointLineColor=0xff00ff00;
    public static int waypointMaxRendered=64,waypointMaxDistance=0;
    public static double waypointMarkerSize=1,waypointOpacity=.5,waypointActivationRadius=2;
    public static boolean snaplookEnabled=true;
    public static String snaplookPerspective="back";
    public static boolean capeEnabled = true;
    public static boolean showLocalCape = true;
    public static boolean localSkinEnabled=true,localSkinSlim=false,skinP2pSharing=true,skinP2pReceiving=true;
    public static volatile java.util.List<PlayerBlocklist.Entry> skinBlockedPlayers=java.util.List.of();
    public static volatile String otherPlayerSkinMode="original";
    public static boolean otherPlayerSkinSlim=false;
    public static java.util.List<PlayerBlocklist.Entry> capeBlockedPlayers = java.util.List.of();
    public static boolean partyEspEnabled=true,partyChatEnabled=true;
    public static boolean partyChatSoundEnabled=false;
    public static String partyChatSound="minecraft:block.note_block.pling";
    public static double partyChatSoundVolume=.5,partyChatSoundPitch=1;
    public static boolean partyConnectionNotifications=true;
    public static volatile int syncP2pPort=0;
    public static boolean gameplayPanelConfirmed;
    public static boolean motionBlurEnabled, motionBlurThirdPerson;
    public static boolean motionBlurRefreshRate = true, motionBlurWeighted, motionBlurDepthPass = true;
    public static float motionBlurStrength = 1;
    public static boolean spinHeadEnabled, spinBodyEnabled;
    public static float spinHeadSpeed = 1, spinBodySpeed = 1;
    public static boolean lobbyMarkerEnabled, lobbyMarkerCrystalOnly;
    public static int lobbyMarkerMinutes = 5;
    public static boolean missingEnchantsEnabled, missingEnchantsRequireShift = true;
    public static Set<String> gameplayModules = Set.of();
    public static java.util.Map<String, com.customcosmetics.features.gameplay.GameplayModule> gameplayKeybinds = java.util.Map.of();
    public static boolean hideAllPlayers, hidePlayersDungeonOnly, hidePlayersBossOnly, hidePlayersAtDevices, hidePlayersClickThrough;
    public static boolean autoClickerWhileSneaking = true, autoClickerBreakBlocks, autoClickerBlockDungeonBreaker = true;
    public static boolean autoClickerStopOnSwap, autoClickerWhitelistOnly;
    public static Set<String> autoClickerLeftItems = Set.of(), autoClickerRightItems = Set.of();
    public static int bookCombineResultDelay = 10;
    public static int experimentMinDelay = 200, experimentMaxDelay = 250, experimentSerums;
    public static boolean experimentAutoClose, experimentGetMax;
    public static boolean bookCombineDisableAfterFinish = true, bookCombineCloseAfterFinish;
    public static boolean trajectoryLine = true, trajectoryBox, trajectoryPlane, trajectoryDepth = true;
    public static int trajectoryTicks = 40;
    public static double trajectoryLineWidth = 1, trajectoryBoxSize = .5, trajectoryPlaneSize = 2;
    public static int trajectoryColor = 0x66DDFF;
    public static boolean crystalLookOnly, crystalInMenus, terminalLookOnly, terminalGroundOnly;
    public static boolean terminalLeapDelay;
    public static int terminalAuraFov = 360;
    public static double terminalLeapDelaySeconds = .5;
    public static boolean relicPickup = true, relicPlacement = true, arrowLookOnly, arrowSneakPause = true;
    public static int crystalDelay = 7, terminalDelayMs = 750, relicDelayMs = 200, simonDelayMs = 200, arrowDelayMs = 200;
    public static boolean simonSolver, simonAuto = true, simonAnnounce;
    public static int simonStartClicks = 3, simonStartDelay = 150, simonRotation = 0, simonRotationTime = 180;
    public static int simonFirstColor = 0x55FF55, simonSecondColor = 0xFFFF55, simonThirdColor = 0xFF5555;
    public static double simonHintOpacity = .5;
    public static double crystalRange = 3, terminalRange = 4, relicRange = 4.5, arrowRange = 4.5;
    public static int doorRetryMs = 500;
    public static boolean doorSwing = true, doorInMenus, doorAura;
    public static double doorAuraRange = 5;
    public static boolean slayerSpawnTime = true, slayerKillTime = true;
    public static boolean mirrorverseLavaMaze = false, mirrorverseLavaParkour = false, mirrorverseTubulator = false;
    public static boolean mirrorverseRedGreen = false, mirrorverseTinyDancer = false, mirrorverseShowMobs = true, mirrorverseShowRecipes = true;
    public static int mirrorverseRedGreenActionDelay = 3, mirrorverseRedGreenInteractDelay = 0;
    public static int mirrorverseSneakDelay = 2, mirrorverseJumpDelay = 2, mirrorversePunchDelay = 4;
    public static boolean dojoForceHighlight = true, dojoForceBlock = true;
    public static boolean dojoDisciplineHighlight = true, dojoDisciplineTracer = false, dojoDisciplineBlock = true, dojoDisciplineSwap = false;
    public static boolean dojoControlHighlight = true, dojoControlAim = false, dojoControlCenter = false;
    public static boolean dojoMasteryTracer = true, dojoMasteryAuto = false, dojoMasterySneakStops = true;
    public static boolean dojoSwiftnessAuto = false, dojoSwiftnessStop = false;
    public static boolean dojoTenacityHighlight = true, dojoTenacityLine = true;
    public static int dojoForceColor = 0xFF5555, dojoDisciplineColor = 0x55FF55, dojoControlColor = 0x55FF55, dojoTenacityColor = 0xFF5555;
    public static int dojoControlPrediction = 5, dojoMasteryAmount = 3, dojoMasteryOffset = 500, dojoSwiftnessRotation = 150;
    public static int dojoSwiftnessCurve = 0;
    public static double dojoSwiftnessLead = .3, dojoSwiftnessTurnDistance = 9;
    public static boolean slayerBossOutline = true, slayerBossFill = true, slayerBossTracer = false;
    public static int slayerBossColor = 0xFFFFFF, slayerBossWidth = 2;
    public static boolean kuudraManaIgnoreZero = true;
    public static int kuudraRequeueDelay = 20, kuudraPearlDelay = 1;
    public static int kuudraTeammateColor = 0xA6E3A1, kuudraTeammateWidth = 2;
    public static boolean kuudraSupplyDropOff = true, kuudraSupplyPickup = true, kuudraFuel = true;
    public static boolean kuudraSupplyProximity = true, kuudraSupplyMessages = true;
    public static int kuudraDropOffColor = 0xA6E3A1, kuudraPickupColor = 0x94E2D5, kuudraFuelColor = 0x89B4FA, kuudraNearbyColor = 0xFAB387;
    public static String kuudraSupplyMessage = "➤ #user recovered a supply in #time (#cur/#max)";
    public static boolean kuudraSupplyHud = true, kuudraDropAlert = true, kuudraPickupAlert;
    public static String kuudraTitleFormat = "[#bars#total - #perc%]", kuudraTitleFilled = "|", kuudraTitleEmpty = "·";
    public static String kuudraDropText = "Dropped supply!", kuudraPickupText = "Picked up supply!";
    public static int kuudraTitleBars = 20;
    public static float kuudraSupplyHudX = .42f, kuudraSupplyHudY = .4f;
    public static boolean kuudraSpawnTimer = true, kuudraBuildTimer = true;
    public static String kuudraSpawnFormat = "Supply in: #time", kuudraBuildFormat = "Build in: #time";
    public static float kuudraSpawnHudX = .42f, kuudraSpawnHudY = .34f, kuudraBuildHudX = .42f, kuudraBuildHudY = .29f;
    public static boolean kuudraHighlightPods = true, kuudraHighlightExactPod, kuudraPodDepth = true, kuudraBlockPickobulus = true;
    public static boolean kuudraStunCloseMenu;
    public static int kuudraStunCloseDelay = 1, kuudraPod = 0, kuudraBlockWhen = 2, kuudraPodColor = 0x74C7EC;
    public static boolean cocoonAlert = true, cocoonTimer = true;
    public static String cocoonAlertText = "Boss cocooned!", cocoonAlertSound = "";
    public static float cocoonHudX = .02f, cocoonHudY = .20f;
    public static boolean trevorEsp, trevorTracer, trevorAutoCall, trevorAutoAccept, trevorCooldownAlert;
    public static boolean trevorCooldownTimer = true;
    public static int trevorCallDelay = 1, trevorAcceptDelay = 1;
    public static double trevorCallEarly = 2;
    public static String trevorAlertMessage = "Cooldown ended!", trevorAlertSound = "";
    public static java.util.Map<String, Integer> trevorColors = java.util.Map.of();
    public static float trevorHudX = .02f, trevorHudY = .26f;
    public static float[] gameplayHudScales = {};
    public static double secretAuraRange = 6.2, secretAuraSkullRange = 4.7, secretAuraBossRange = 5.7;
    public static int secretAuraDelay = 150, secretAuraFov = 360, secretAuraSwap = 1, secretAuraSlot = 1;
    public static boolean secretAuraSwapBack = true, secretAuraSwing, secretAuraInBoss = true, secretAuraInContainer;
    public static boolean minibossChatDetection, minibossChatAlert = true, minibossVanillaMessage, minibossTitle = true;
    public static int minibossDistance = 10;
    public static String minibossAlertText = "Miniboss spawned!", minibossSpecialText = "Big boi spawned!";
    public static boolean beaconOutline = true, beaconFill, beaconDepth, beaconTracer = true;
    public static int beaconColor = 0x56E9E2, beaconFillColor = 0x56E9E2, beaconTracerColor = 0x56E9E2;
    public static int beaconLineWidth = 2, beaconTracerWidth = 2;
    public static int lapisCorpseColor = 0x306BFF, tungstenCorpseColor = 0xAAAAAA,
        umberCorpseColor = 0xB56222, vanguardCorpseColor = 0xF224B8;
    public static boolean lapisCorpse = true, tungstenCorpse = true, umberCorpse = true, vanguardCorpse = true;
    public static boolean corpseOutline = true, corpseFill = true, corpseCube, corpseDepth, corpseTracer = true;
    public static boolean corpseName = true, corpseDistance = true, corpseHideClaimed = true;
    public static double corpseLineWidth = 2;
    public static int pestEspColor = 0xFAB387, pestEspThickness = 2;
    public static boolean pestEspDepth, pestEspTracer;
    public static boolean renderHideArm, renderHideEffects = true, renderHidePlayerArmor, renderHideSelfArmor = true;
    public static boolean renderHideOtherArmor, renderHideMobArmor, renderHideGlow, renderHideFog = true;
    public static boolean renderHideLava = true, renderHideFire = true, renderHideEntityFire = true;
    public static java.util.EnumMap<com.customcosmetics.features.gameplay.WorldScannerSettings.Target, com.customcosmetics.features.gameplay.WorldScannerSettings.Options> scannerOptions =
        new java.util.EnumMap<>(com.customcosmetics.features.gameplay.WorldScannerSettings.Target.class);
    public static boolean scannerShowJasperCount = true;
    public static boolean kuudraHideBlockedPerks = true;
    public static Set<String> kuudraBlockedPerks = com.customcosmetics.features.gameplay.KuudraPerks.DEFAULT;
    public static double scannerJasperBackground = .5;
    public static int daggerSwapDelay = 1, daggerSwapVariance = 2;
    public static boolean chocolateClick, chocolateTower, chocolateUpgrade, chocolateStrays, chocolateMute, chocolateEggs;
    public static int chocolateDelay = 150, chocolateUpgradeDelay = 500, carnivalShotDelay = 100;
    public static boolean spookMath, spookSpeaking, spookCommitment, spookTitle, spookDungeon;
    public static int spookDelay;
    public static boolean safariFloorDrops = true, safariFloorDropsCurrentBiome = true;
    public static int safariFloorDropColor = 0x00FF00;
    public static Set<String> safariDisabledBiomes = Set.of(), safariAcrossBiomes = Set.of(), safariDisabledSpecies = Set.of();
    public static java.util.Map<String, Integer> safariSpeciesColors = java.util.Map.of();
    public static boolean trajectoryBows, trajectoryPearls = true;
    public static boolean autoClickerLeft, autoClickerRight;
    public static int autoClickerLeftMin = 10, autoClickerLeftMax = 12, autoClickerRightMin = 10, autoClickerRightMax = 12;
    public static boolean refillPearls, refillSuperboom, refillJerries, refillLeaps;
    public static boolean refillTimed, refillDungeonsOnly;
    public static int refillPercent = 50, refillIntervalSeconds = 5;
    public static float freshHudX = .02f, freshHudY = .14f;
    public static String fastLeapTarget = "";
    public static Set<String> fastLeapTriggers = Set.of();
    public static java.util.Map<String, String> fastLeapNames = java.util.Map.of("p1", "P1");
    public static java.util.Map<String, String> fastLeapClasses = java.util.Map.of();
    public static boolean fastLeapAfterBlood, fastLeapMelody;
    public static Set<String> autoLeapTriggers = Set.of();
    public static java.util.Map<String, String> autoLeapNames = java.util.Map.of("p1", "P1");
    public static java.util.Map<String, String> autoLeapClasses = java.util.Map.of();
    public static java.util.Map<String, com.customcosmetics.features.gameplay.AutoLeap.Preset> autoLeapPresets = java.util.Map.of();
    public static boolean autoLeapByName, autoLeapSwapBack, autoLeapGate, autoLeapMelody, autoLeapAfterBlood;
    public static int autoLeapDelay = 1;
    public static boolean secretSwapItem, secretSwapBack;
    public static int secretSwapSlot = 1, secretInteractDelay;
    public static int harpClickDelay = 1;
    public static double hideNearbyPlayersRadius = 3;
    public static int starredMobColor = 0xF8CC50;
    public static boolean f7TimerTicks;
    public static float f7HudX = .02f, f7HudY = .08f;
    public static boolean partyTracersEnabled=false;
    public static int partyTracerColor=0xFFA6CF;
    public static boolean locationTracersEnabled=false;
    public static boolean sharingChamsEnabled=false;
    public static int locationTracerColor=0xFFA6CF;
    public static String partyChatPrefix="`";
    public static String partyEspStyle="outline";
    public static int partyEspColor=0xFFA6CF;
    public static double partyEspOpacity=.2,partyEspLineWidth=1.5;
    public static boolean partyFarMarkers=true,partyShowNames=true;
    public static String partyNametagMode="fixed";
    public static String partyNametagStyle="simple";
    public static double partyNametagSize=1;
    public static double partyNametagHideDistance=8;
    public static double partyMaxDisplayDistance=0;
    public static int locationVisualsVersion=1;
    public static boolean locationEspEnabled=true,locationChamsEnabled=false,locationFarMarkers=true,locationShowNames=true;
    public static String locationEspStyle="outline",locationNametagMode="fixed",locationNametagStyle="simple";
    public static int locationEspColor=0xFFA6CF;
    public static double locationEspOpacity=.2,locationEspLineWidth=1.5,locationNametagSize=1,locationNametagHideDistance=8,locationMaxDisplayDistance=0;
    public static boolean positionSharingEnabled=false,positionSharingReceive=false;
    public static String positionSharingMode="public";
    public static java.util.List<PlayerBlocklist.Entry> positionSharingTargets=java.util.List.of();
    public static double blockingX=0, blockingY=0, blockingZ=0;
    public static double blockingRotationX=0, blockingRotationY=0, blockingRotationZ=0;
    public static boolean swordBlockingEnabled = false;
    public static String swordBlockingStyle = "default";
    public static boolean swordBlockingSyncEnabled = true;
    public static boolean swordBlockingListsApplyToOthers = false;
    public static String swordBlockingListMode = "off";
    public static String[] swordBlockingBlacklist = {"skyblock:ASPECT_OF_THE_VOID"};
    public static String[] swordBlockingWhitelist = new String[0];
    public static transient volatile String syncUdpHost = "cc.toryhi.com";
    public static transient volatile int syncUdpPort = 25578;
    public static boolean animationsEnabled = true;
    public static double animationItemScale = 1.0;
    public static double animationX = 0, animationY = 0, animationZ = 0;
    public static double animationRotationX = 0, animationRotationY = 0, animationRotationZ = 0;
    public static double animationSwingX = 1, animationSwingY = 1, animationSwingZ = 1;
    public static double animationSwingSpeed = 0;
    public static boolean animationDisableHandMove = false, animationDisableEquip = false;
    public static boolean animationDisableSwing = false, animationTerminatorOnly = false, animationIgnoreHaste = false;

    // Runtime connection values are loaded only from the separate override file.
    public static transient volatile String syncServerIp = "cc.toryhi.com";
    public static transient volatile int syncServerPort = 443;
    public static transient volatile boolean syncServerHttps = true;
    public static transient volatile boolean syncServerSni = true;
    public static transient volatile String syncBlockingTransport = "auto";
    public static volatile int syncRefreshIntervalSeconds = 30;
    public static volatile boolean syncEnabled = true;
    public static volatile boolean syncNotifications = false;
    public static volatile String syncProxyHost = "";
    public static volatile int syncProxyPort = 0;
    public static volatile String syncProxyAuth = "";
    public static volatile boolean syncProxyFallbackDirect = true;
    public static volatile String syncProxySource = "none";

    private ConfigManager() {}

    public static int getSyncRefreshIntervalSeconds() {
        return Math.clamp(syncRefreshIntervalSeconds, 30, 7200);
    }

    public static int getSyncServerPort() {
        return syncServerPort >= 1 && syncServerPort <= 65535 ? syncServerPort : (syncServerHttps ? 443 : 80);
    }

    public static int getSyncP2pPort() { return syncP2pPort>=0&&syncP2pPort<=65535?syncP2pPort:0; }

    public static void loadConfig() {
        load(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("config.json"));
        RemoteServerConfig.init(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("customremoteserver.json"));
    }

    static void load(Path path) {
        if (!Files.exists(path)) {loadBlockingLists(path);return;}
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject settings = root.getAsJsonObject("settings");
            if (settings == null) return;
            uiFontPath = "";
            uiFontBold = null;
            uiFontOversample = com.customcosmetics.gui.UiFonts.MAX_SAMPLING;
            uiFontSize = 9;
            uiFontSpacing = 0;
            for (Field field : ConfigManager.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || !settings.has(field.getName())
                        || settings.get(field.getName()).isJsonNull()) continue;
                try {
                    Object value = GSON.fromJson(settings.get(field.getName()), field.getGenericType());
                    if (value instanceof Double number && !Double.isFinite(number)) continue;
                    field.set(null, value);
                } catch (RuntimeException | IllegalAccessException e) {
                    LOGGER.warn("Ignoring invalid setting {}", field.getName());
                }
            }
            hudLayout.values().removeIf(java.util.Objects::isNull);
            if(accessories==null)accessories=com.customcosmetics.features.accessories.AccessoriesSettings.NONE;
            uiThemeMode="light".equals(uiThemeMode)?"light":"dark";
            uiLanguage=com.customcosmetics.gui.UiText.normalize(uiLanguage);
            try { if (uiFontPath == null || uiFontPath.length() > 4096 || !uiFontPath.isEmpty()
                    && java.nio.file.Path.of(uiFontPath).getFileName() == null) uiFontPath = ""; }
            catch (java.nio.file.InvalidPathException invalid) { uiFontPath = ""; }
            fullscreenStyle = "borderless".equals(fullscreenStyle) ? "borderless" : "vanilla";
            freecamSpeed = com.customcosmetics.features.map.Freecam.validSpeed(freecamSpeed);
            freecamVerticalSpeed = com.customcosmetics.features.map.Freecam.validSpeed(freecamVerticalSpeed);
            hitColor &= 0xFFFFFF;
            hitOpacity = Float.isFinite(hitOpacity) ? Math.clamp(hitOpacity, 0, 1) : 0.5f;
            hitColorChromaSpeed = Math.clamp(hitColorChromaSpeed, 1, 100);
            textReplacements = com.customcosmetics.features.text.TextReplacements.normalize(textReplacements);
            com.customcosmetics.features.text.TextReplacements.setRules(textReplacements);
            inventoryDimPercent = Math.clamp(inventoryDimPercent, 0, 100);
            uiFontOversample = Math.clamp(uiFontOversample, 1, com.customcosmetics.gui.UiFonts.MAX_SAMPLING);
            uiFontBaseline = Float.isFinite(uiFontBaseline) ? Math.clamp(uiFontBaseline, -2, 2) : 0;
            uiFontShadow = Float.isFinite(uiFontShadow) ? Math.clamp(uiFontShadow, 0, 2) : 1;
            uiFontSize = Float.isFinite(uiFontSize) ? Math.clamp(uiFontSize, 7, 12) : 9;
            uiFontSpacing = Float.isFinite(uiFontSpacing) ? Math.clamp(uiFontSpacing, -1, 2) : 0;
            uiAccentColor&=0xFFFFFF;
            damageSplashStyle=com.customcosmetics.features.damage.DamageAnimation.normalize(damageSplashStyle);
            damageSplashColorMode=com.customcosmetics.features.damage.DamageColors.normalize(damageSplashColorMode);
            damageSplashRainbowSpeed=com.customcosmetics.features.damage.DamageColors.speed(damageSplashRainbowSpeed);
            snaplookPerspective=com.customcosmetics.features.camera.Snaplook.normalize(snaplookPerspective);
            damageSplashSymbol=com.customcosmetics.features.damage.DamageText.symbol(damageSplashSymbol);
            damageSplashSeparator=damageSplashSeparator!=null&&java.util.List.of("none","comma","space","underscore").contains(damageSplashSeparator)?damageSplashSeparator:"comma";
            damageSplashSize=com.customcosmetics.features.damage.DamageAnimation.bounded(damageSplashSize,1,.25,3);
            damageSplashDuration=com.customcosmetics.features.damage.DamageAnimation.bounded(damageSplashDuration,1.2,.3,3);
            damageSplashStartColor&=0xFFFFFF;damageSplashEndColor&=0xFFFFFF;
            nickTweaksNickChanger = NicknameLimit.limit(nickTweaksNickChanger);
            nickTweaksRankType = com.customcosmetics.features.nickname.RankSettings.normalizeType(nickTweaksRankType);
            nickTweaksRankTagColor = com.customcosmetics.features.nickname.RankSettings.normalizeTagColor(nickTweaksRankTagColor);
            nickTweaksRankPlusColor = com.customcosmetics.features.nickname.RankSettings.PlusColor.parse(nickTweaksRankPlusColor).id();
            nickTweaksBlockedPlayers = com.customcosmetics.config.PlayerBlocklist.normalize(nickTweaksBlockedPlayers);
            accessoriesBlockedPlayers = PlayerBlocklist.normalize(accessoriesBlockedPlayers);
            capeBlockedPlayers = PlayerBlocklist.normalize(capeBlockedPlayers);
            skinBlockedPlayers = PlayerBlocklist.normalize(skinBlockedPlayers);
            otherPlayerSkinMode = com.customcosmetics.features.skins.SkinRules.mode(otherPlayerSkinMode);
            smolLimbSwingSpeed = Math.clamp(smolLimbSwingSpeed, 0.5, 5.0);
            playerSizeX=com.customcosmetics.features.playersize.PlayerSizeSettings.bounded(playerSizeX);
            playerSizeY=com.customcosmetics.features.playersize.PlayerSizeSettings.bounded(playerSizeY);
            playerSizeZ=com.customcosmetics.features.playersize.PlayerSizeSettings.bounded(playerSizeZ);
            creatureModel=com.customcosmetics.features.creature.CreatureModels.parse(creatureModel).id;
            syncRefreshIntervalSeconds = getSyncRefreshIntervalSeconds();
            syncP2pPort=getSyncP2pPort();
            Set<String> retiredModules = Set.of("HEALTH_MANA", "AUTO_SELL", "AUTO_KICK");
            gameplayModules = gameplayModules == null ? Set.of() : gameplayModules.stream()
                .filter(name -> name != null && !retiredModules.contains(name))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (gameplayHudScales == null) gameplayHudScales = new float[0];
            if (trevorColors == null) trevorColors = java.util.Map.of();
            trevorCallDelay = Math.clamp(trevorCallDelay, 0, 5);
            trevorAcceptDelay = Math.clamp(trevorAcceptDelay, 0, 5);
            trevorCallEarly = Double.isFinite(trevorCallEarly) ? Math.clamp(trevorCallEarly, 0, 5) : 2;
            secretAuraRange = Math.clamp(secretAuraRange, 2.1, 6.5);
            secretAuraSkullRange = Math.clamp(secretAuraSkullRange, 2.1, 4.7);
            secretAuraBossRange = Math.clamp(secretAuraBossRange, 2.1, 6.5);
            secretAuraFov = Math.clamp(secretAuraFov, 10, 360);
            secretAuraDelay = Math.clamp(secretAuraDelay, 100, 4000);
            secretAuraSlot = Math.clamp(secretAuraSlot, 1, 9);
            secretAuraSwap = Math.clamp(secretAuraSwap, 0, 2);
            daggerSwapDelay = Math.clamp(daggerSwapDelay, 0, 10);
            chocolateDelay = Math.clamp(chocolateDelay, 50, 1500);
            chocolateUpgradeDelay = Math.clamp(chocolateUpgradeDelay, 300, 2000);
            carnivalShotDelay = Math.clamp(carnivalShotDelay, 50, 500);
            spookDelay = Math.clamp(spookDelay, 0, 10);
            daggerSwapVariance = Math.clamp(daggerSwapVariance, 0, 10);
            if (scannerOptions == null) scannerOptions = new java.util.EnumMap<>(com.customcosmetics.features.gameplay.WorldScannerSettings.Target.class);
            if (kuudraBlockedPerks == null) kuudraBlockedPerks = com.customcosmetics.features.gameplay.KuudraPerks.DEFAULT;
            gameplayKeybinds = com.customcosmetics.features.gameplay.GameplayKeybinds.normalize(gameplayKeybinds);
            autoLeapTriggers = autoLeapTriggers == null ? Set.of() : Set.copyOf(autoLeapTriggers);
            autoLeapNames = autoLeapNames == null ? java.util.Map.of() : java.util.Map.copyOf(autoLeapNames);
            autoLeapClasses = autoLeapClasses == null ? java.util.Map.of() : java.util.Map.copyOf(autoLeapClasses);
            autoLeapPresets = autoLeapPresets == null ? java.util.Map.of() : java.util.Map.copyOf(autoLeapPresets);
            fastLeapTriggers = fastLeapTriggers == null ? Set.of() : Set.copyOf(fastLeapTriggers);
            fastLeapNames = fastLeapNames == null ? java.util.Map.of() : java.util.Map.copyOf(fastLeapNames);
            fastLeapClasses = fastLeapClasses == null ? java.util.Map.of() : java.util.Map.copyOf(fastLeapClasses);
            autoLeapDelay = Math.clamp(autoLeapDelay, 0, 10);
            secretSwapSlot = Math.clamp(secretSwapSlot, 1, 9);
            secretInteractDelay = Math.clamp(secretInteractDelay, 0, 5);
            refillPercent = Math.clamp(refillPercent, 5, 95);
            refillIntervalSeconds = Math.clamp(refillIntervalSeconds, 1, 60);
            kuudraRequeueDelay = Math.clamp(kuudraRequeueDelay, 0, 100);
            kuudraPearlDelay = Math.clamp(kuudraPearlDelay, 1, 2);
            kuudraTeammateWidth = Math.clamp(kuudraTeammateWidth, 1, 10);
            kuudraTitleBars = Math.clamp(kuudraTitleBars, 5, 30);
            kuudraStunCloseDelay = Math.clamp(kuudraStunCloseDelay, 0, 5);
            kuudraPod = Math.clamp(kuudraPod, 0, 2);
            kuudraBlockWhen = Math.clamp(kuudraBlockWhen, 0, 2);
            minibossDistance = Math.clamp(minibossDistance, 1, 15);
            beaconLineWidth = Math.clamp(beaconLineWidth, 1, 10);
            beaconTracerWidth = Math.clamp(beaconTracerWidth, 1, 10);
            trajectoryTicks = Math.clamp(trajectoryTicks, 1, 120);
            trajectoryLineWidth = Math.clamp(trajectoryLineWidth, .1, 5);
            trajectoryBoxSize = Math.clamp(trajectoryBoxSize, .5, 3);
            trajectoryPlaneSize = Math.clamp(trajectoryPlaneSize, .1, 5);
            crystalDelay = Math.clamp(crystalDelay, 1, 20);
            crystalRange = Math.clamp(crystalRange, 1, 5);
            terminalDelayMs = Math.clamp(terminalDelayMs, 0, 2000);
            terminalAuraFov = Math.clamp(terminalAuraFov, 10, 360);
            simonStartClicks = Math.clamp(simonStartClicks, 1, 10);
            simonStartDelay = Math.clamp(simonStartDelay, 50, 1250);
            simonRotation = Math.clamp(simonRotation, 0, 2);
            simonRotationTime = Math.clamp(simonRotationTime, 50, 500);
            simonHintOpacity = Math.clamp(simonHintOpacity, 0, 1);
            terminalLeapDelaySeconds = Math.clamp(terminalLeapDelaySeconds, .1, 5);
            terminalRange = Math.clamp(terminalRange, 0, 4);
            relicDelayMs = Math.clamp(relicDelayMs, 50, 1000);
            relicRange = Math.clamp(relicRange, 1, 4.5);
            simonDelayMs = Math.clamp(simonDelayMs, 50, 500);
            arrowDelayMs = Math.clamp(arrowDelayMs, 70, 500);
            arrowRange = Math.clamp(arrowRange, 2.1, 6.5);
            doorRetryMs = Math.clamp(doorRetryMs, 100, 2000);
            doorAuraRange = Math.clamp(doorAuraRange, 2, 6);
            corpseLineWidth = Math.clamp(corpseLineWidth, 1, 10);
            pestEspThickness = Math.clamp(pestEspThickness, 1, 10);
            fastLeapTarget = fastLeapTarget != null && fastLeapTarget.matches("[A-Za-z0-9_]{1,16}") ? fastLeapTarget : "";
            if (!settings.has("autoClickerLeftMin") && settings.has("autoClickerCps")
                    && settings.get("autoClickerCps") instanceof com.google.gson.JsonPrimitive legacyCps && legacyCps.isNumber())
                autoClickerLeftMin = autoClickerLeftMax = autoClickerRightMin = autoClickerRightMax = legacyCps.getAsInt();
            autoClickerLeftMin = Math.clamp(autoClickerLeftMin, 1, 20);
            autoClickerLeftMax = Math.clamp(autoClickerLeftMax, 1, 20);
            autoClickerRightMin = Math.clamp(autoClickerRightMin, 1, 20);
            autoClickerRightMax = Math.clamp(autoClickerRightMax, 1, 20);
            bookCombineResultDelay = Math.clamp(bookCombineResultDelay, 2, 40);
            experimentMinDelay = Math.clamp(experimentMinDelay, 100, 1000);
            experimentMaxDelay = Math.clamp(experimentMaxDelay, experimentMinDelay, 1000);
            experimentSerums = Math.clamp(experimentSerums, 0, 3);
            harpClickDelay = Math.clamp(harpClickDelay, 0, 5);
            hideNearbyPlayersRadius = Math.clamp(hideNearbyPlayersRadius, 0, 32);
            lobbyMarkerMinutes = Math.clamp(lobbyMarkerMinutes, 1, 60);
            motionBlurStrength = Float.isFinite(motionBlurStrength) ? Math.clamp(motionBlurStrength, 0, 2) : 1;
            zoomFactor = Double.isFinite(zoomFactor) ? Math.clamp(zoomFactor, 1.25, 16) : 4;
            if (settings.has("zoomSmoothing") && !settings.has("zoomInSeconds") && !settings.has("zoomOutSeconds")) {
                try {
                    double legacy = settings.get("zoomSmoothing").getAsDouble();
                    if (Double.isFinite(legacy)) zoomInSeconds = zoomOutSeconds = legacy;
                } catch (IllegalStateException | UnsupportedOperationException | NumberFormatException ignored) {
                    // Keep the default durations when the old value cannot be read.
                }
            }
            zoomInSeconds = Math.clamp(zoomInSeconds, 0, 2);
            zoomOutSeconds = Math.clamp(zoomOutSeconds, 0, 2);
            nickTweaksChromaSpeed = Math.clamp(nickTweaksChromaSpeed, 0.0, 8.0);
            nickTweaksChromaSize = Math.clamp(nickTweaksChromaSize, 0.1, 12.0);
            nickTweaksChromaChroma = Math.clamp(nickTweaksChromaChroma, 0.0, 0.4);
            nickTweaksChromaLightness = Math.clamp(nickTweaksChromaLightness, 0.2, 1.0);
            nickTweaksGradientStyle = com.customcosmetics.features.nickname.NicknameGradient.normalizeStyle(nickTweaksGradientStyle);
            nickTweaksGradientSegments = com.customcosmetics.features.nickname.NicknameSegments.normalize(nickTweaksGradientSegments);
            nickTweaksShimmerSpeed = Double.isFinite(nickTweaksShimmerSpeed) ? Math.clamp(nickTweaksShimmerSpeed, 0, 4) : 1;
            nickTweaksShimmerSize = com.customcosmetics.features.nickname.NicknameGradient.normalizeSize(nickTweaksShimmerSize);
            nickTweaksShimmerInterval = com.customcosmetics.features.nickname.NicknameGradient.normalizeInterval(nickTweaksShimmerInterval);
            clampAnimations();
            partyEspStyle=java.util.Set.of("none","fill","outline","both").contains(partyEspStyle)?partyEspStyle:"outline";
            partyNametagStyle=com.customcosmetics.features.party.SharingNametag.normalize(partyNametagStyle);
            partyEspOpacity=Math.clamp(partyEspOpacity,.05,.75);partyEspLineWidth=Math.clamp(partyEspLineWidth,.5,4);
            waypointMaxRendered=Math.clamp(waypointMaxRendered,16,256);
            waypointMaxDistance=Math.clamp(waypointMaxDistance,0,2048);
            waypointMarkerSize=Double.isFinite(waypointMarkerSize)?Math.clamp(waypointMarkerSize,.5,2):1;
            waypointOpacity=Double.isFinite(waypointOpacity)?Math.clamp(waypointOpacity,.1,.85):.5;
            waypointActivationRadius=Double.isFinite(waypointActivationRadius)?Math.clamp(waypointActivationRadius,1,10):1.5;
            waypointLineWidth=Double.isFinite(waypointLineWidth)?Math.clamp(waypointLineWidth,1,15):5;
            partyNametagMode=com.customcosmetics.features.party.PartyNametagScale.mode(partyNametagMode);
            partyNametagSize=com.customcosmetics.features.party.PartyNametagScale.size(partyNametagSize);
            partyNametagHideDistance=com.customcosmetics.features.party.PartyDisplayRange.normalize(partyNametagHideDistance);
            partyMaxDisplayDistance=com.customcosmetics.features.party.PartyDisplayRange.normalize(partyMaxDisplayDistance);
            if(!settings.has("locationVisualsVersion")||locationVisualsVersion<1){
                locationEspEnabled=partyEspEnabled;locationChamsEnabled=sharingChamsEnabled;
                locationEspStyle=partyEspStyle;locationEspColor=partyEspColor;locationEspOpacity=partyEspOpacity;locationEspLineWidth=partyEspLineWidth;
                locationFarMarkers=partyFarMarkers;locationShowNames=partyShowNames;locationNametagMode=partyNametagMode;locationNametagStyle=partyNametagStyle;
                locationNametagSize=partyNametagSize;locationNametagHideDistance=partyNametagHideDistance;locationMaxDisplayDistance=partyMaxDisplayDistance;locationVisualsVersion=1;
            }
            locationEspStyle=java.util.Set.of("none","fill","outline","both").contains(locationEspStyle)?locationEspStyle:"outline";
            locationEspColor&=0xFFFFFF;
            locationEspOpacity=Double.isFinite(locationEspOpacity)?Math.clamp(locationEspOpacity,.05,.75):.2;
            locationEspLineWidth=Double.isFinite(locationEspLineWidth)?Math.clamp(locationEspLineWidth,.5,4):1.5;
            locationNametagMode=com.customcosmetics.features.party.PartyNametagScale.mode(locationNametagMode);
            locationNametagStyle=com.customcosmetics.features.party.SharingNametag.normalize(locationNametagStyle);
            locationNametagSize=com.customcosmetics.features.party.PartyNametagScale.size(locationNametagSize);
            locationNametagHideDistance=com.customcosmetics.features.party.PartyDisplayRange.normalize(locationNametagHideDistance);
            locationMaxDisplayDistance=com.customcosmetics.features.party.PartyDisplayRange.normalize(locationMaxDisplayDistance);
            partyChatPrefix=com.customcosmetics.features.party.PartyChatRules.prefix(partyChatPrefix);
            partyChatSound=com.customcosmetics.features.party.PartyChatSound.sound(partyChatSound);
            partyChatSoundVolume=com.customcosmetics.features.party.PartyChatSound.volume(partyChatSoundVolume);
            partyChatSoundPitch=com.customcosmetics.features.party.PartyChatSound.pitch(partyChatSoundPitch);
            positionSharingMode=com.customcosmetics.features.party.PositionAudience.mode(positionSharingMode);
            positionSharingTargets=com.customcosmetics.features.party.PositionAudience.targets(positionSharingTargets);
            swordBlockingStyle = com.customcosmetics.features.blocking.BlockingStyle.parse(swordBlockingStyle).value();
            swordBlockingListMode = com.customcosmetics.features.blocking.BlockingRules.Mode.parse(swordBlockingListMode).value();
            swordBlockingBlacklist = com.customcosmetics.features.blocking.BlockingRules.sanitize(swordBlockingBlacklist);
            swordBlockingWhitelist = com.customcosmetics.features.blocking.BlockingRules.sanitize(swordBlockingWhitelist);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to read config {}", path, e);
        } finally {
            loadBlockingLists(path);
        }
    }
    private static void loadBlockingLists(Path main){
        Path lists=SwordBlockingLists.path(main);
        SwordBlockingLists.apply(SwordBlockingLists.capture());
        try{
            if(Files.exists(lists))SwordBlockingLists.apply(SwordBlockingLists.read(lists));
            else{var migrated=SwordBlockingLists.capture();SwordBlockingLists.write(lists,migrated);SwordBlockingLists.apply(migrated);}
        }catch(IOException|RuntimeException error){LOGGER.error("Sword blocking lists {} were retained unchanged; using legacy/default lists. Saving will back up a damaged file before replacement.",lists,error);}
    }

    private record SaveRequest(Path path, JsonObject snapshot,SwordBlockingLists.Data lists) {}
    private static final java.util.concurrent.ExecutorService WRITER=java.util.concurrent.Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"CustomSkyBlock-config-writer");thread.setDaemon(true);return thread;
    });
    private static SaveRequest queuedSave;
    private static boolean writing;

    public static synchronized void saveConfig() {
        queueSave(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("config.json"));
        pendingSaveAt = 0;
    }

    static synchronized void queueSave(Path path) {
        var snapshot=snapshot();if(snapshot==null)return;
        queuedSave=new SaveRequest(path,snapshot,SwordBlockingLists.capture());
        if(writing)return;
        writing=true;
        WRITER.execute(()->{
            while(true){
                SaveRequest request;
                synchronized(ConfigManager.class){
                    request=queuedSave;queuedSave=null;
                    if(request==null){writing=false;return;}
                }
                write(request.path(),request.snapshot(),request.lists());
            }
        });
    }

    /** Only shutdown waits for queued saves; editing never waits for the filesystem. */
    public static void awaitPendingSaves() {
        try { WRITER.submit(()->{}).get(); }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(java.util.concurrent.ExecutionException failure){LOGGER.error("Failed to finish config saves",failure);}
    }

    static void save(Path path) {
        var snapshot=snapshot();if(snapshot!=null)write(path,snapshot,SwordBlockingLists.capture());
    }
    private static JsonObject snapshot() {
        try {
            nickTweaksNickChanger = NicknameLimit.limit(nickTweaksNickChanger);
            JsonObject settings = new JsonObject();
            for (Field field : ConfigManager.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())&&!SwordBlockingLists.separate(field.getName())) {
                    settings.add(field.getName(), GSON.toJsonTree(field.get(null)));
                }
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);root.add("settings", settings);return root;
        } catch(IllegalAccessException error){LOGGER.error("Failed to capture config",error);return null;}
    }
    private static void write(Path path,JsonObject root,SwordBlockingLists.Data lists) {
        try {
            // Persist the lists first: never discard the only legacy copy after a list-write failure.
            SwordBlockingLists.write(SwordBlockingLists.path(path),lists);
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to save config {}", path, e);
        }
    }

    public static synchronized void requestSave() {
        pendingSaveAt = System.currentTimeMillis() + 750;
    }

    public static void clampAnimations() {
        animationItemScale = Math.clamp(animationItemScale, 0.1, 2.5);
        animationX = Math.clamp(animationX, -2, 2); animationY = Math.clamp(animationY, -2, 2); animationZ = Math.clamp(animationZ, -2, 2);
        animationRotationX = Math.clamp(animationRotationX, -50, 50);
        animationRotationY = Math.clamp(animationRotationY, -50, 50);
        animationRotationZ = Math.clamp(animationRotationZ, -50, 50);
        animationSwingX = Math.clamp(animationSwingX, 0, 2); animationSwingY = Math.clamp(animationSwingY, 0, 2); animationSwingZ = Math.clamp(animationSwingZ, 0, 2);
        animationSwingSpeed = Math.clamp(animationSwingSpeed, -2, 1);
    }

    public static void resetAnimations() {
        animationsEnabled = true; animationItemScale = 1;
        animationX = animationY = animationZ = 0;
        animationRotationX = animationRotationY = animationRotationZ = 0;
        animationSwingX = animationSwingY = animationSwingZ = 1;
        animationSwingSpeed = 0;
        animationDisableHandMove = animationDisableEquip = animationDisableSwing = animationTerminatorOnly = animationIgnoreHaste = false;
    }

    public static synchronized void flushPendingSave() {
        if (pendingSaveAt != 0 && System.currentTimeMillis() >= pendingSaveAt) saveConfig();
    }
}
