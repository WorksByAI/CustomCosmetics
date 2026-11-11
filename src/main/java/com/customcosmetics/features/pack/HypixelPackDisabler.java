// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.sync.ExperimentalAccess;
import com.customcosmetics.utils.MessageUtils;
import com.google.common.hash.HashCode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.PackDownloader;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.DownloadQueue;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Util;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Keeps the vanilla pack handshake and controls which assets reach the client renderer. */
public final class HypixelPackDisabler {
    private static final boolean EXTERNAL = FabricLoader.getInstance().isModLoaded("packdisabler");
    private static final Map<UUID, DownloadQueue.DownloadRequest> SKYBLOCK_PACKS = new ConcurrentHashMap<>();
    private static final String FALLBACK_ID = "customcosmetics/hypixel-fallback";
    private static final UUID FALLBACK_DOWNLOAD = UUID.nameUUIDFromBytes(FALLBACK_ID.getBytes(StandardCharsets.UTF_8));
    private static final List<Identifier> TEXT_SHADERS = List.of(
        "core/text.vsh", "core/rendertype_text.vsh", "core/rendertype_text_see_through.vsh",
        "core/rendertype_text_intensity.vsh", "core/rendertype_text_intensity_see_through.vsh",
        "include/modify_vanilla_color.glsl", "include/color_util.glsl"
    ).stream().map(path -> Identifier.withDefaultNamespace("shaders/" + path)).toList();
    private static volatile Set<String> fallbackPacks = Set.of();
    private static volatile boolean textColors;
    private static Pack retainedPack;
    private static Pack pendingNativePack;
    private static boolean reloading;
    private static boolean reloadQueued;
    private static boolean forceQueued;
    private static boolean notifyQueued;
    private static boolean downloading;
    private static int recoveryTicks;
    private static long nextRecovery;
    private static long waitForNativeUntil;

    public interface DownloadedPacks {
        ServerPackManager customcosmetics$packManager();
    }

    public interface DownloadWorker {
        PackDownloader customcosmetics$packDownloader();
    }

    private HypixelPackDisabler() {}

    public static boolean available() { return !EXTERNAL; }

    public static boolean enabled() {
        return available() && ConfigManager.hideHypixelSkyBlockPack && ExperimentalAccess.allowed();
    }

    public static boolean usingFallback() {
        return !fallbackPacks.isEmpty() && enabled();
    }

    public static void remember(UUID id, URL url, String hash) {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if ("https".equals(url.getProtocol()) && (host.equals("hypixel.net") || host.endsWith(".hypixel.net"))
            && url.getPath().toLowerCase(Locale.ROOT).contains("skyblock")) {
            HashCode expected = hash != null && hash.matches("[0-9a-fA-F]{40}") ? HashCode.fromString(hash.toLowerCase(Locale.ROOT)) : null;
            SKYBLOCK_PACKS.put(id, new DownloadQueue.DownloadRequest(url, expected));
            waitForNativeUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        } else {
            SKYBLOCK_PACKS.remove(id);
        }
    }

    public static void disconnect() {
        SKYBLOCK_PACKS.clear();
    }

    public static void forget(UUID id) {
        SKYBLOCK_PACKS.remove(id);
    }

    private static DownloadQueue.DownloadRequest officialOffer(String packId) {
        int separator = packId.lastIndexOf('/');
        if (!packId.startsWith("server/") || packId.length() - separator != 37) return null;
        try {
            return SKYBLOCK_PACKS.get(UUID.fromString(packId.substring(separator + 1)));
        } catch (IllegalArgumentException invalidId) {
            return null;
        }
    }

    public static void prepareNativePack(List<Pack> packs) {
        pendingNativePack = null;
        for (var pack : packs) {
            var offer = officialOffer(pack.getId());
            if (offer != null && offer.hash() != null) pendingNativePack = pack;
        }
    }

    public static void finishNativeReload() {
        if (pendingNativePack != null) retainedPack = pendingNativePack;
        pendingNativePack = null;
    }

    public static void discardFailedPack() {
        pendingNativePack = null;
        if (retainedPack != null && fallbackPacks.contains(retainedPack.getId())) retainedPack = null;
    }

    public static List<Pack> prioritize(List<Pack> packs) {
        if (!enabled() || SKYBLOCK_PACKS.isEmpty() && retainedPack == null) {
            fallbackPacks = Set.of();
            return packs;
        }
        var fallback = new ArrayList<Pack>();
        var remaining = new ArrayList<Pack>(packs.size());
        for (var pack : packs) {
            String id = pack.getId();
            if (officialOffer(id) != null || retainedPack != null && id.equals(retainedPack.getId())) {
                fallback.add(pack);
            } else {
                remaining.add(pack);
            }
        }
        // Retain the verified official assets across server pack removal and island transfers.
        if (fallback.isEmpty() && retainedPack != null) fallback.add(retainedPack);
        fallbackPacks = Set.copyOf(fallback.stream().map(Pack::getId).toList());
        textColors = ConfigManager.hypixelPackTextColors;
        if (fallback.isEmpty()) return packs;
        LegacyPackItems.prepare();
        // Packs are opened in ascending priority. Keep the official namespace for items
        // and other assets that have no replacement, underneath vanilla and local packs.
        fallback.addAll(remaining);
        return fallback;
    }

    public static void refresh(Minecraft client) {
        refresh(client, TextColorOverrides.update(), false);
    }

    public static void tick(Minecraft client) {
        if (!enabled() || usingFallback() || downloading || reloading || client.player == null) return;
        if (++recoveryTicks < 40) return;
        recoveryTicks = 0;
        if (System.nanoTime() - nextRecovery < 0 || waitingForNative() || !inSkyBlock(client)) return;
        if (retainedPack != null) refresh(client, false, false);
        else recover(client, false);
    }

    public static void checkDownloadAndReload(Minecraft client) {
        if (!enabled()) {
            MessageUtils.sendMessage("Enable Pack Disabler to reload the Hypixel pack.");
            return;
        }
        if (downloading) return;
        if (usingFallback() || retainedPack != null) {
            MessageUtils.sendMessage("Reloading the Hypixel SkyBlock pack.");
            refresh(client, true, true);
        } else if (waitingForNative()) {
            MessageUtils.sendMessage("Waiting for Minecraft to load the Hypixel pack.");
        } else if (inSkyBlock(client)) {
            MessageUtils.sendMessage("Downloading the Hypixel SkyBlock pack.");
            recover(client, true);
        } else {
            MessageUtils.sendMessage("Enter Hypixel SkyBlock to load the official pack.");
        }
    }

    private static boolean inSkyBlock(Minecraft client) {
        return PresenceWorld.isHypixel(client) && PresenceWorld.isSkyblock(client);
    }

    private static boolean waitingForNative() {
        return !SKYBLOCK_PACKS.isEmpty() && System.nanoTime() - waitForNativeUntil < 0;
    }

    private static void recover(Minecraft client, boolean notify) {
        downloading = true;
        int format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
        CompletableFuture.supplyAsync(() -> {
            try {
                return HypixelPackManifest.read(format);
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, Util.ioPool()).whenComplete((request, error) -> client.execute(() -> {
            if (error != null || !enabled() || !inSkyBlock(client) || usingFallback() || waitingForNative()) {
                finishRecovery(error, notify);
            } else {
                download(client, request, notify);
            }
        }));
    }

    private static void download(Minecraft client, DownloadQueue.DownloadRequest request, boolean notify) {
        var manager = ((DownloadedPacks) client.getDownloadedPackSource()).customcosmetics$packManager();
        var downloader = ((DownloadWorker) manager).customcosmetics$packDownloader();
        downloader.download(Map.of(FALLBACK_DOWNLOAD, request), result -> client.execute(() -> {
            if (!enabled() || retainedPack != null || usingFallback() || waitingForNative()) {
                finishRecovery(null, false);
                return;
            }
            Path path = result.downloaded().get(FALLBACK_DOWNLOAD);
            retainedPack = path == null ? null : Pack.readMetaAndCreate(
                new PackLocationInfo(FALLBACK_ID, Component.literal("Hypixel SkyBlock"), PackSource.BUILT_IN, Optional.empty()),
                new FilePackResources.FileResourcesSupplier(path.toFile()), PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            finishRecovery(retainedPack == null ? new java.io.IOException("No verified SkyBlock resource pack") : null, notify);
            if (retainedPack != null && inSkyBlock(client)) refresh(client, false, notify);
        }));
    }

    private static void finishRecovery(Throwable error, boolean notify) {
        downloading = false;
        nextRecovery = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        if (error != null) {
            CustomCosmeticsClient.LOGGER.warn("Could not load the official Hypixel SkyBlock pack", error);
            if (notify) MessageUtils.sendMessage("Could not verify or download the Hypixel pack.");
        }
    }

    private static void refresh(Minecraft client, boolean force, boolean notify) {
        if (!force && SKYBLOCK_PACKS.isEmpty() && retainedPack == null && fallbackPacks.isEmpty()) return;
        if (reloading) {
            reloadQueued = true;
            forceQueued |= force;
            notifyQueued |= notify;
            return;
        }
        boolean enable = enabled();
        boolean active = !fallbackPacks.isEmpty();
        if (!force && active == enable && (!enable || textColors == ConfigManager.hypixelPackTextColors)) return;
        reloading = true;
        client.reloadResourcePacks().whenComplete((ignored, error) -> client.execute(() -> {
            reloading = false;
            if (error != null) {
                CustomCosmeticsClient.LOGGER.warn("Could not reload resources after a visual setting changed", error);
            }
            if (notify) {
                MessageUtils.sendMessage(error == null && usingFallback() ? "Hypixel SkyBlock pack reloaded." : "Could not reload the Hypixel pack.");
            }
            if (reloadQueued) {
                reloadQueued = false;
                boolean nextForce = forceQueued;
                boolean nextNotify = notifyQueued;
                forceQueued = false;
                notifyQueued = false;
                refresh(client, nextForce, nextNotify);
            }
        }));
    }

    public static boolean officialAsset(Identifier id) {
        return id != null && "hypixel_skyblock".equals(id.getNamespace());
    }

    public static Identifier tooltipStyle(Identifier style) {
        return officialAsset(style) && !ConfigManager.hypixelPackTooltipBackground && usingFallback() ? null : style;
    }

    public static Map<Identifier, Resource> textShaders(Map<Identifier, Resource> selected,
        java.util.function.Function<Identifier, List<Resource>> stack) {
        Set<String> packs = fallbackPacks;
        if (packs.isEmpty()) return selected;
        Map<Identifier, Resource> changed = null;
        for (var id : TEXT_SHADERS) {
            Resource original = selected.get(id);
            if (original == null) continue;
            Resource replacement = null;
            if (textColors) {
                for (var resource : stack.apply(id)) {
                    if (packs.contains(resource.sourcePackId())) replacement = resource;
                }
                if (replacement == null) continue;
            } else if (!packs.contains(original.sourcePackId())) {
                continue;
            }
            if (original == replacement) continue;
            if (changed == null) changed = new HashMap<>(selected);
            if (replacement == null) changed.remove(id);
            else changed.put(id, replacement);
        }
        return changed == null ? selected : changed;
    }
}
