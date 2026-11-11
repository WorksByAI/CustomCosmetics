// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.ExperimentalAccess;
import com.customcosmetics.utils.MessageUtils;
import com.google.common.hash.HashCode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.PackDownloader;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.DownloadQueue;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the vanilla pack handshake and controls which assets reach the client renderer. */
public final class HypixelPackDisabler {
    private static final boolean EXTERNAL = FabricLoader.getInstance().isModLoaded("packdisabler");
    private static final Map<UUID, DownloadQueue.DownloadRequest> SKYBLOCK_PACKS = new ConcurrentHashMap<>();
    private static final List<Identifier> TEXT_SHADERS = List.of(
        "core/text.vsh", "core/rendertype_text.vsh", "core/rendertype_text_see_through.vsh",
        "core/rendertype_text_intensity.vsh", "core/rendertype_text_intensity_see_through.vsh",
        "include/modify_vanilla_color.glsl", "include/color_util.glsl"
    ).stream().map(path -> Identifier.withDefaultNamespace("shaders/" + path)).toList();
    private static volatile Set<String> fallbackPacks = Set.of();
    private static volatile boolean textColors;
    private static boolean reloading;
    private static boolean reloadQueued;
    private static boolean forceQueued;
    private static Object downloadInProgress;

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
        } else {
            SKYBLOCK_PACKS.remove(id);
        }
    }

    public static void disconnect() {
        SKYBLOCK_PACKS.clear();
        fallbackPacks = Set.of();
        downloadInProgress = null;
    }

    public static List<PackResources> prioritize(List<PackResources> packs) {
        if (SKYBLOCK_PACKS.isEmpty() || !enabled()) {
            fallbackPacks = Set.of();
            return packs;
        }
        var fallback = new ArrayList<PackResources>();
        var remaining = new ArrayList<PackResources>(packs.size());
        for (var pack : packs) {
            String id = pack.packId();
            if (id.startsWith("server/") && SKYBLOCK_PACKS.keySet().stream().anyMatch(uuid -> id.endsWith("/" + uuid))) {
                fallback.add(pack);
            } else {
                remaining.add(pack);
            }
        }
        fallbackPacks = Set.copyOf(fallback.stream().map(PackResources::packId).toList());
        textColors = ConfigManager.hypixelPackTextColors;
        if (fallback.isEmpty()) return packs;
        LegacyPackItems.prepare();
        // Packs are opened in ascending priority. Keep the official namespace for items
        // and other assets that have no replacement, underneath vanilla and local packs.
        fallback.addAll(remaining);
        return fallback;
    }

    public static void refresh(Minecraft client) {
        refresh(client, false);
    }

    public static void checkDownloadAndReload(Minecraft client) {
        if (!usingFallback()) {
            MessageUtils.sendMessage("Enable Pack Disabler in SkyBlock to reload the Hypixel pack.");
            return;
        }
        if (downloadInProgress != null) return;
        var downloads = new HashMap<UUID, DownloadQueue.DownloadRequest>();
        SKYBLOCK_PACKS.forEach((id, request) -> {
            if (request.hash() != null && fallbackPacks.stream().anyMatch(pack -> pack.endsWith("/" + id))) {
                downloads.put(id, request);
            }
        });
        MessageUtils.sendMessage("Reloading the Hypixel SkyBlock pack.");
        if (downloads.isEmpty()) {
            refresh(client, true);
            return;
        }
        var manager = ((DownloadedPacks) client.getDownloadedPackSource()).customcosmetics$packManager();
        var downloader = ((DownloadWorker) manager).customcosmetics$packDownloader();
        Object ticket = new Object();
        downloadInProgress = ticket;
        // Use Minecraft's own cache and hash checks, independently of the connection's pack status.
        downloader.download(downloads, result -> client.execute(() -> {
            if (downloadInProgress != ticket) return;
            downloadInProgress = null;
            if (!usingFallback() || !SKYBLOCK_PACKS.entrySet().containsAll(downloads.entrySet())) return;
            if (!result.failed().isEmpty()) {
                MessageUtils.sendMessage("Could not verify or download the Hypixel pack.");
                return;
            }
            refresh(client, true);
        }));
    }

    private static void refresh(Minecraft client, boolean force) {
        if (SKYBLOCK_PACKS.isEmpty()) return;
        if (reloading) {
            reloadQueued = true;
            forceQueued |= force;
            return;
        }
        boolean enable = enabled();
        boolean active = !fallbackPacks.isEmpty();
        if (!force && active == enable && (!enable || textColors == ConfigManager.hypixelPackTextColors)) return;
        reloading = true;
        client.reloadResourcePacks().whenComplete((ignored, error) -> client.execute(() -> {
            reloading = false;
            if (error != null) {
                CustomCosmeticsClient.LOGGER.warn("Could not reload the SkyBlock resource pack", error);
            }
            if (force && usingFallback()) {
                MessageUtils.sendMessage(error == null ? "Hypixel SkyBlock pack reloaded." : "Could not reload the Hypixel pack.");
            }
            if (reloadQueued) {
                reloadQueued = false;
                boolean nextForce = forceQueued;
                forceQueued = false;
                refresh(client, nextForce);
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
