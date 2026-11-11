// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.ExperimentalAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.world.item.ItemStack;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the vanilla pack handshake and controls which assets reach the client renderer. */
public final class HypixelPackDisabler {
    private static final boolean EXTERNAL = FabricLoader.getInstance().isModLoaded("packdisabler");
    private static final Set<String> SKYBLOCK_IDS = ConcurrentHashMap.newKeySet();
    private static volatile boolean hidden;
    private static boolean reloading;
    private static boolean reloadQueued;

    private HypixelPackDisabler() {}

    public static boolean available() { return !EXTERNAL; }

    private static boolean enabled() {
        return available() && ConfigManager.hideHypixelSkyBlockPack && ExperimentalAccess.allowed();
    }

    public static void remember(UUID id, URL url) {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if ("https".equals(url.getProtocol()) && (host.equals("hypixel.net") || host.endsWith(".hypixel.net"))
            && url.getPath().toLowerCase(Locale.ROOT).contains("skyblock")) {
            SKYBLOCK_IDS.add(id.toString());
        } else {
            SKYBLOCK_IDS.remove(id.toString());
        }
    }

    public static void disconnect() {
        SKYBLOCK_IDS.clear();
        hidden = false;
    }

    public static List<PackResources> filter(List<PackResources> packs) {
        if (SKYBLOCK_IDS.isEmpty() || !enabled()) {
            hidden = false;
            return packs;
        }
        var visible = new ArrayList<PackResources>(packs.size());
        boolean removed = false;
        for (var pack : packs) {
            String id = pack.packId();
            if (id.startsWith("server/") && SKYBLOCK_IDS.contains(id.substring(id.lastIndexOf('/') + 1))) {
                pack.close();
                removed = true;
            } else {
                visible.add(pack);
            }
        }
        hidden = removed;
        return removed ? visible : packs;
    }

    public static void refresh(Minecraft client) {
        if (SKYBLOCK_IDS.isEmpty()) return;
        if (reloading) {
            reloadQueued = true;
            return;
        }
        if (hidden == enabled()) return;
        reloading = true;
        client.reloadResourcePacks().whenComplete((ignored, error) -> client.execute(() -> {
            reloading = false;
            if (error != null) {
                CustomCosmeticsClient.LOGGER.warn("Could not reload the SkyBlock resource pack", error);
            }
            if (reloadQueued) {
                reloadQueued = false;
                refresh(client);
            }
        }));
    }

    public static Object fallbackModel(ItemStack stack, Object model) {
        if (hidden && model instanceof Identifier id && id.getNamespace().equals("hypixel_skyblock")
            && !com.customcosmetics.features.items.ItemCosmetics.hasModel(id)) {
            var vanilla = stack.getItem().components().get(DataComponents.ITEM_MODEL);
            if (vanilla != null) return vanilla;
        }
        return model;
    }
}
