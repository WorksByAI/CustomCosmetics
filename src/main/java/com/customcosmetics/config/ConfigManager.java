/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.config;

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
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomCosmetics/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile long pendingSaveAt;

    public static boolean smolpeopleMode = false;
    public static boolean damageSplashEnabled=false, damageSplashBold=true, damageSplashCompact=false;
    public static boolean damageSplashStyleNormal=false, damageSplashShadow=true;
    public static String damageSplashStyle="default", damageSplashSymbol="✧", damageSplashSeparator="comma";
    public static int damageSplashStartColor=0xFFFF55, damageSplashEndColor=0xFF5555;
    public static double damageSplashSize=1, damageSplashDuration=1.2;
    public static double smolLimbSwingSpeed = 2.5;
    public static com.customcosmetics.features.accessories.AccessoriesSettings accessories=com.customcosmetics.features.accessories.AccessoriesSettings.NONE;
    public static java.util.List<PlayerBlocklist.Entry> accessoriesBlockedPlayers = java.util.List.of();
    public static boolean playerSizeEnabled=false;
    public static double playerSizeX=1,playerSizeY=1,playerSizeZ=1;
    public static boolean nickTweaksEnabled = false;
    public static String nickTweaksNickChanger = "";
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
    public static boolean capeEnabled = true;
    public static boolean localSkinEnabled=true,localSkinSlim=false,skinP2pSharing=false,skinP2pReceiving=true;
    public static java.util.List<PlayerBlocklist.Entry> capeBlockedPlayers = java.util.List.of();
    public static boolean partyEspEnabled=true,partyChatEnabled=true;
    public static boolean partyChatSoundEnabled=false;
    public static String partyChatSound="minecraft:block.note_block.pling";
    public static double partyChatSoundVolume=.5,partyChatSoundPitch=1;
    public static boolean partyConnectionNotifications=true;
    public static boolean partyP2pEnabled=true;
    public static volatile int syncP2pPort=0;
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
    public static boolean positionSharingEnabled=false,positionSharingReceive=false;
    public static String positionSharingMode="public";
    public static java.util.List<PlayerBlocklist.Entry> positionSharingTargets=java.util.List.of();
    public static double blockingX=0, blockingY=0, blockingZ=0;
    public static double blockingRotationX=0, blockingRotationY=0, blockingRotationZ=0;
    public static boolean swordBlockingEnabled = true;
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
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject settings = root.getAsJsonObject("settings");
            if (settings == null) return;
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
            if(accessories==null)accessories=com.customcosmetics.features.accessories.AccessoriesSettings.NONE;
            damageSplashStyle=com.customcosmetics.features.damage.DamageAnimation.normalize(damageSplashStyle);
            damageSplashSymbol=com.customcosmetics.features.damage.DamageText.symbol(damageSplashSymbol);
            damageSplashSeparator=damageSplashSeparator!=null&&java.util.List.of("none","comma","space","underscore").contains(damageSplashSeparator)?damageSplashSeparator:"comma";
            damageSplashSize=com.customcosmetics.features.damage.DamageAnimation.bounded(damageSplashSize,1,.25,3);
            damageSplashDuration=com.customcosmetics.features.damage.DamageAnimation.bounded(damageSplashDuration,1.2,.3,3);
            damageSplashStartColor&=0xFFFFFF;damageSplashEndColor&=0xFFFFFF;
            nickTweaksNickChanger = NicknameLimit.limit(nickTweaksNickChanger);
            nickTweaksBlockedPlayers = com.customcosmetics.config.PlayerBlocklist.normalize(nickTweaksBlockedPlayers);
            accessoriesBlockedPlayers = PlayerBlocklist.normalize(accessoriesBlockedPlayers);
            capeBlockedPlayers = PlayerBlocklist.normalize(capeBlockedPlayers);
            smolLimbSwingSpeed = Math.clamp(smolLimbSwingSpeed, 0.5, 5.0);
            playerSizeX=com.customcosmetics.features.playersize.PlayerSizeSettings.bounded(playerSizeX);
            playerSizeY=com.customcosmetics.features.playersize.PlayerSizeSettings.bounded(playerSizeY);
            playerSizeZ=com.customcosmetics.features.playersize.PlayerSizeSettings.bounded(playerSizeZ);
            syncRefreshIntervalSeconds = getSyncRefreshIntervalSeconds();
            syncP2pPort=getSyncP2pPort();
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
            partyNametagMode=com.customcosmetics.features.party.PartyNametagScale.mode(partyNametagMode);
            partyNametagSize=com.customcosmetics.features.party.PartyNametagScale.size(partyNametagSize);
            partyNametagHideDistance=com.customcosmetics.features.party.PartyDisplayRange.normalize(partyNametagHideDistance);
            partyMaxDisplayDistance=com.customcosmetics.features.party.PartyDisplayRange.normalize(partyMaxDisplayDistance);
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
        }
    }

    public static synchronized void saveConfig() {
        save(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("config.json"));
        pendingSaveAt = 0;
    }

    static void save(Path path) {
        try {
            nickTweaksNickChanger = NicknameLimit.limit(nickTweaksNickChanger);
            JsonObject settings = new JsonObject();
            for (Field field : ConfigManager.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    settings.add(field.getName(), GSON.toJsonTree(field.get(null)));
                }
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("settings", settings);
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | IllegalAccessException e) {
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
