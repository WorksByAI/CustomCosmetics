/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.features.smolpeople;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.utils.LocateUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public final class SmolPlayers {
    private static final Pattern MOB_PET_NAMETAG_PREFIX = Pattern.compile("^\\[Lv\\d+]", Pattern.CASE_INSENSITIVE);
    private static final double MIRROR_ARMOR_STAND_HORIZONTAL_HALF_EXTENT = 1.0;
    private static final double MIRROR_ARMOR_STAND_COLUMN_HEIGHT_BLOCKS = 4.0;

    private record SmolMirrorSource(UUID uuid, Player player, PlayerInfo tabInfo) {
    }

    private record ProfileIdCacheEntry(String textureValue, UUID profileId) {
    }

    private static final Map<UUID, ProfileIdCacheEntry> PROFILE_ID_CACHE = new ConcurrentHashMap<>();
    private static final UUID UNSET_SKIN_OWNER_ID = new UUID(0L, 0L);
    private static Object cachedLevel, cachedConnection, cachedPlayer, cachedAppearance;
    private static long cachedTick = Long.MIN_VALUE;
    private static boolean cachedLocalMode;
    private static List<SmolMirrorSource> mirrorSources;
    private static final it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap MIRRORS=new it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap(),NAMETAGS=new it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap();
    private static final Map<UUID,String[]> VISIBLE_NAMES = new java.util.IdentityHashMap<>();
    public static void clearCache(){cachedLevel=cachedConnection=cachedPlayer=cachedAppearance=null;cachedTick=Long.MIN_VALUE;mirrorSources=null;VISIBLE_NAMES.clear();PROFILE_ID_CACHE.clear();MIRRORS.clear();NAMETAGS.clear();}
    public static void tick(Minecraft client){if(client.player==null||!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL)){if(cachedLevel!=null)clearCache();}else refreshMirrorCache(client);}

    /** Mirror discovery is shared by all player/model/nametag hooks during this client tick. */
    private static void refreshMirrorCache(Minecraft mc) {
        Object appearance = PresenceSync.appearanceSnapshot();
        long tick = mc.player == null ? Long.MIN_VALUE : mc.player.tickCount;
        var connection = mc.getConnection();
        if (cachedLevel == mc.level && cachedConnection == connection && cachedPlayer == mc.player
                && cachedAppearance == appearance && cachedTick == tick && cachedLocalMode == (ConfigManager.smolpeopleMode && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL))) return;
        if (cachedLevel != mc.level || cachedConnection != connection) PROFILE_ID_CACHE.clear();
        cachedLevel = mc.level; cachedConnection = connection; cachedPlayer = mc.player;
        cachedAppearance = appearance; cachedTick = tick; cachedLocalMode = (ConfigManager.smolpeopleMode && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL));
        mirrorSources = null; VISIBLE_NAMES.clear();MIRRORS.clear();NAMETAGS.clear();
    }

    private SmolPlayers() {
    }

    public static boolean shouldApplySmolTo(int entityId) {
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL))return false;
        if (!(ConfigManager.smolpeopleMode && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL)) && !PresenceSync.hasRemoteSmolUser()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entityId == mc.player.getId()) {
            return (ConfigManager.smolpeopleMode && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL));
        }

        if (ConfigManager.offlineMode) return false;
        Player targetPlayer = getPlayerByEntityId(entityId);
        if (targetPlayer == null) {
            return false;
        }

        Boolean remotePreference = PresenceSync.getRemoteSmolPreference(targetPlayer.getUUID());
        if (remotePreference != null) {
            return remotePreference;
        }

        refreshMirrorCache(mc);byte cached=MIRRORS.get(entityId);if(cached!=0)return cached==2;
        boolean mirror=isMirrorOfAnySmolSource(targetPlayer);MIRRORS.put(entityId,(byte)(mirror?2:1));return mirror;
    }

    public static float getLimbSwingSpeed(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Player player = getPlayerByEntityId(entityId);
        if (player == null || player == mc.player) return (float) ConfigManager.smolLimbSwingSpeed;
        Double remoteSpeed = PresenceSync.getRemoteSmolLimbSwingSpeed(player.getUUID());
        if (remoteSpeed != null) return remoteSpeed.floatValue();
        // Mirrors inherit their source's animation. Real tab players keep their own identity.
        if (!isListedInTab(player.getUUID())) {
            UUID skinOwner = resolveSkinOwnerId(player);
            if (skinOwner != null) {
                for (SmolMirrorSource source : collectSmolMirrorSources(mc)) {
                    if (!isMirrorOf(source, player, skinOwner)) continue;
                    if (source.player() == mc.player) return (float) ConfigManager.smolLimbSwingSpeed;
                    Double sourceSpeed = PresenceSync.getRemoteSmolLimbSwingSpeed(source.uuid());
                    if (sourceSpeed != null) return sourceSpeed.floatValue();
                }
            }
        }
        return (float) ConfigManager.smolLimbSwingSpeed;
    }

    public static boolean isMirrorNametagArmorStand(int entityId) {
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL))return false;
        if (!(ConfigManager.smolpeopleMode && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL)) && !PresenceSync.hasRemoteSmolUser()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof ArmorStand armorStand)) {
            return false;
        }
        refreshMirrorCache(mc);byte cached=NAMETAGS.get(entityId);if(cached!=0)return cached==2;
        boolean match=mirrorNametag(mc,armorStand);NAMETAGS.put(entityId,(byte)(match?2:1));return match;
    }
    private static boolean mirrorNametag(Minecraft mc,ArmorStand armorStand){
        if (!isNametagArmorStandCandidate(armorStand)) {
            return false;
        }

        String standDisplayName = getArmorStandDisplayName(armorStand);
        for (SmolMirrorSource source : collectSmolMirrorSources(mc)) {
            if (!nametagDisplayNamesMatch(getSourceDisplayName(source), standDisplayName)) {
                continue;
            }
            for (Player mirrorPlayer : mc.level.players()) {
                if (mirrorPlayer == source.player()) {
                    continue;
                }
                UUID candidateSkinOwnerId = resolveSkinOwnerId(mirrorPlayer);
                if (candidateSkinOwnerId == null || !isMirrorOf(source, mirrorPlayer, candidateSkinOwnerId)) {
                    continue;
                }
                if (isWithinMirrorArmorStandRadius(mirrorPlayer, armorStand)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMirrorOfAnySmolSource(Player candidate) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || candidate == null) {
            return false;
        }
        UUID candidateSkinOwnerId = resolveSkinOwnerId(candidate);
        if (candidateSkinOwnerId == null) {
            return false;
        }
        for (SmolMirrorSource source : collectSmolMirrorSources(mc)) {
            if (isMirrorOf(source, candidate, candidateSkinOwnerId)) {
                return true;
            }
        }
        return false;
    }

    private static UUID resolveSkinOwnerId(Player player) {
        UUID profileId = getSkinTextureProfileId(player);
        return profileId == UNSET_SKIN_OWNER_ID ? null : profileId;
    }

    private static List<SmolMirrorSource> collectSmolMirrorSources(Minecraft mc) {
        refreshMirrorCache(mc);
        if (mirrorSources != null) return mirrorSources;
        List<SmolMirrorSource> sources = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        if (mc.player != null && (ConfigManager.smolpeopleMode && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL))) {
            sources.add(new SmolMirrorSource(mc.player.getUUID(), mc.player, null));
            seen.add(mc.player.getUUID());
        }
        if (mc.getConnection() == null) {
            return mirrorSources = List.copyOf(sources);
        }
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null) {
                continue;
            }
            UUID uuid = info.getProfile().id();
            if (uuid == null || !seen.add(uuid)) {
                continue;
            }
            if (!shouldSmolifySourcePlayer(mc, uuid)) {
                continue;
            }
            Player worldPlayer = mc.level != null ? mc.level.getPlayerByUUID(uuid) : null;
            sources.add(new SmolMirrorSource(uuid, worldPlayer, info));
        }
        return mirrorSources = List.copyOf(sources);
    }

    private static boolean shouldSmolifySourcePlayer(Minecraft mc, UUID uuid) {
        if (mc.player != null && uuid.equals(mc.player.getUUID())) {
            return (ConfigManager.smolpeopleMode && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SMOL));
        }
        Boolean remotePreference = PresenceSync.getRemoteSmolPreference(uuid);
        if (remotePreference != null) {
            return remotePreference;
        }
        return false;
    }

    private static boolean isMirrorOf(SmolMirrorSource source, Player candidate, UUID candidateSkinOwnerId) {
        if (source == null || candidate == null || candidate == source.player()
                || isListedInTab(candidate.getUUID())) {
            return false;
        }
        if (candidateSkinOwnerId.equals(source.uuid())) {
            return true;
        }
        if (source.player() != null) {
            return matchesLegacyMirrorPath(source.player(), candidate);
        }
        if (source.tabInfo() != null) {
            return matchesLegacyMirrorPathFromTab(source.tabInfo(), candidate);
        }
        return false;
    }

    private static boolean matchesLegacyMirrorPath(Player source, Player other) {
        if (other.getUUID().equals(source.getUUID())) {
            return true;
        }

        String sourceName = source.getGameProfile().name();
        String otherName = other.getGameProfile().name();
        if (namesMatchMirror(sourceName, otherName)) {
            return true;
        }

        if (matchesVisibleName(source, other)) {
            return true;
        }

        if (!(source instanceof AbstractClientPlayer sourceClient)) {
            return false;
        }
        if (!(other instanceof AbstractClientPlayer otherClient)) {
            return false;
        }

        var sourceSkin = sourceClient.getSkin();
        var otherSkin = otherClient.getSkin();
        if (sourceSkin == null || otherSkin == null
                || sourceSkin.body() == null || otherSkin.body() == null) {
            return false;
        }
        if (!sourceSkin.body().texturePath().equals(otherSkin.body().texturePath())) {
            return false;
        }
        return !isListedInTab(other.getUUID());
    }

    private static boolean matchesLegacyMirrorPathFromTab(PlayerInfo info, Player other) {
        if (info.getProfile() == null) {
            return false;
        }
        UUID sourceUuid = info.getProfile().id();
        if (sourceUuid != null && other.getUUID().equals(sourceUuid)) {
            return true;
        }

        String sourceName = info.getProfile().name();
        String otherName = other.getGameProfile().name();
        if (namesMatchMirror(sourceName, otherName)) {
            return true;
        }

        String tabDisplayName = info.getTabListDisplayName() != null
                ? LocateUtils.toPlainText(info.getTabListDisplayName().getString())
                : null;
        if (tabDisplayName != null) {
            for (String otherVisible : collectVisibleNames(other)) {
                if (namesMatchMirror(tabDisplayName, otherVisible)) {
                    return true;
                }
            }
        }

        String sourceTexture = getTexturesPropertyValue(info.getProfile());
        String otherTexture = getTexturesPropertyValue(other.getGameProfile());
        if (sourceTexture == null || otherTexture == null || !sourceTexture.equals(otherTexture)) {
            return false;
        }
        return !isListedInTab(other.getUUID());
    }

    private static boolean isListedInTab(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || uuid == null) {
            return false;
        }
        return mc.getConnection().getPlayerInfo(uuid) != null;
    }

    private static boolean matchesVisibleName(Player self, Player other) {
        for (String selfVisible : collectVisibleNames(self)) {
            for (String otherVisible : collectVisibleNames(other)) {
                if (namesMatchMirror(selfVisible, otherVisible)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String[] collectVisibleNames(Player player) {
        refreshMirrorCache(Minecraft.getInstance());
        var cached = VISIBLE_NAMES.get(player.getUUID());
        if (cached != null) return cached;
        String plainName = LocateUtils.toPlainText(player.getName().getString());
        String displayName = player.getDisplayName() != null
            ? LocateUtils.toPlainText(player.getDisplayName().getString())
            : null;
        if (displayName != null && displayName.equals(plainName)) {
            var result = new String[] { plainName }; VISIBLE_NAMES.put(player.getUUID(), result); return result;
        }
        var result = new String[] { plainName, displayName }; VISIBLE_NAMES.put(player.getUUID(), result); return result;
    }

    private static boolean namesMatchMirror(String selfName, String otherName) {
        if (selfName == null || selfName.isBlank() || otherName == null || otherName.isBlank()) {
            return false;
        }
        return selfName.equalsIgnoreCase(otherName)
            || otherName.equalsIgnoreCase(reverse(selfName));
    }

    private static String reverse(String value) {
        return new StringBuilder(value).reverse().toString();
    }

    private static UUID getSkinTextureProfileId(Player player) {
        if (player == null) {
            return UNSET_SKIN_OWNER_ID;
        }
        UUID uuid = player.getUUID();
        String textureValue = getTexturesPropertyValue(player.getGameProfile());
        if (textureValue == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
                if (info != null && info.getProfile() != null) {
                    textureValue = getTexturesPropertyValue(info.getProfile());
                }
            }
        }
        ProfileIdCacheEntry cached = PROFILE_ID_CACHE.get(uuid);
        if (cached != null && java.util.Objects.equals(cached.textureValue(), textureValue)) {
            UUID cachedProfileId = cached.profileId();
            return cachedProfileId != null ? cachedProfileId : UNSET_SKIN_OWNER_ID;
        }
        UUID profileId = textureValue != null ? parseProfileIdFromTextureValue(textureValue) : null;
        PROFILE_ID_CACHE.put(uuid, new ProfileIdCacheEntry(textureValue, profileId));
        return profileId != null ? profileId : UNSET_SKIN_OWNER_ID;
    }

    private static String getTexturesPropertyValue(GameProfile profile) {
        if (profile == null || profile.properties() == null) {
            return null;
        }
        var textures = profile.properties().get("textures");
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        for (Property property : textures) {
            if (property != null && property.value() != null && !property.value().isBlank()) {
                return property.value();
            }
        }
        return null;
    }

    private static UUID parseProfileIdFromTextureValue(String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            if (!object.has("profileId") || object.get("profileId").isJsonNull()) {
                return null;
            }
            return parseUuidFlexible(object.get("profileId").getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UUID parseUuidFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = raw.trim();
        if (!id.contains("-") && id.length() == 32) {
            id = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                    + "-" + id.substring(16, 20) + "-" + id.substring(20, 32);
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isWithinMirrorArmorStandRadius(Player mirrorPlayer, ArmorStand armorStand) {
        double dx = armorStand.getX() - mirrorPlayer.getX();
        double dy = armorStand.getY() - mirrorPlayer.getY();
        double dz = armorStand.getZ() - mirrorPlayer.getZ();
        if (Math.abs(dx) > MIRROR_ARMOR_STAND_HORIZONTAL_HALF_EXTENT
                || Math.abs(dz) > MIRROR_ARMOR_STAND_HORIZONTAL_HALF_EXTENT) {
            return false;
        }
        double playerCenterYOffset = 1.0;
        double halfHeight = MIRROR_ARMOR_STAND_COLUMN_HEIGHT_BLOCKS * 0.5;
        return dy >= playerCenterYOffset - halfHeight
            && dy <= playerCenterYOffset + halfHeight;
    }

    private static boolean isNametagArmorStandCandidate(ArmorStand armorStand) {
        return armorStand.isInvisible()
            && armorStand.hasCustomName()
            && !isExcludedNametagArmorStand(armorStand);
    }

    private static boolean isExcludedNametagArmorStand(ArmorStand armorStand) {
        String standName = getArmorStandDisplayName(armorStand);
        if (standName.isBlank()) {
            return true;
        }
        if (standName.equalsIgnoreCase("CLICK")) {
            return true;
        }
        if (standName.equalsIgnoreCase("Armor Stand")) {
            return true;
        }
        return MOB_PET_NAMETAG_PREFIX.matcher(standName).find() || standName.indexOf('\u2764') >= 0;
    }

    private static String getArmorStandDisplayName(ArmorStand armorStand) {
        if (armorStand.getCustomName() != null) {
            return LocateUtils.toPlainText(armorStand.getCustomName().getString()).trim();
        }
        return LocateUtils.toPlainText(armorStand.getName().getString()).trim();
    }

    private static String getPlayerDisplayName(Player player) {
        if (player.getDisplayName() != null) {
            return LocateUtils.toPlainText(player.getDisplayName().getString()).trim();
        }
        return LocateUtils.toPlainText(player.getName().getString()).trim();
    }

    private static String getSourceDisplayName(SmolMirrorSource source) {
        if (source.player() != null) {
            return getPlayerDisplayName(source.player());
        }
        if (source.tabInfo() != null && source.tabInfo().getTabListDisplayName() != null) {
            return LocateUtils.toPlainText(source.tabInfo().getTabListDisplayName().getString()).trim();
        }
        if (source.tabInfo() != null && source.tabInfo().getProfile() != null) {
            return source.tabInfo().getProfile().name();
        }
        return "";
    }

    private static boolean nametagDisplayNamesMatch(String sourceDisplayName, String standDisplayName) {
        if (sourceDisplayName == null || standDisplayName == null) {
            return false;
        }
        String source = sourceDisplayName.trim();
        String stand = standDisplayName.trim();
        if (source.isEmpty() || stand.isEmpty()) {
            return false;
        }
        if (source.equalsIgnoreCase(stand)) {
            return true;
        }
        if (source.length() > stand.length()
                && source.regionMatches(true, 0, stand, 0, stand.length())
                && !Character.isLetterOrDigit(source.charAt(stand.length()))) {
            return true;
        }
        return false;
    }

    public static Player getPlayerByEntityId(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }

        Entity entity = mc.level.getEntity(entityId);
        if (entity instanceof Player player) {
            return player;
        }
        return null;
    }

}
