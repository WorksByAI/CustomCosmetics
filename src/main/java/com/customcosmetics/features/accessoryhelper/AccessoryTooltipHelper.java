// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.config.FeatureModules;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.blocking.SwordItems;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/** Client-only ownership helper with asynchronous, cached public accessory metadata. */
public final class AccessoryTooltipHelper {
    private static final Pattern BAG_TITLE = Pattern.compile("^Accessory Bag(?: \\((\\d+)/(\\d+)\\))?$");
    private static final int MAX_CACHE_BYTES = 1024 * 1024;
    private static final Path PATH = CosmeticsConfigDir.getCosmeticsConfigDir().resolve("accessory-helper.json");
    private static final Path CATALOG_PATH = CosmeticsConfigDir.getCosmeticsConfigDir().resolve("accessory-catalog.json");
    private static final URI CATALOG_URI = URI.create("https://hysky.de/api/accessories");
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "CustomCosmetics-accessory-save"); thread.setDaemon(true); return thread;
    });
    private static final AtomicReference<String> PENDING = new AtomicReference<>();
    private static final AtomicBoolean WRITING = new AtomicBoolean();
    private static AccessoryCatalog catalog;
    private static AccessoryBagIndex index;
    private static boolean loaded, writable = true, onSkyblock;
    private static String profileKey = "", notice = "";
    private static Object lastScreen;
    private static int screenFirstTick, bagFingerprint, ticks;
    private static long lastRefresh;
    private static boolean refreshing;
    private AccessoryTooltipHelper() {}

    public static void init() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> append(stack, lines));
        ClientTickEvents.END_CLIENT_TICK.register(AccessoryTooltipHelper::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WRITER.shutdown();
            try { WRITER.awaitTermination(3, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
    }
    private static boolean enabled() {
        return ConfigManager.accessoryHelperTooltipEnabled && FeatureModules.enabled(FeatureModules.Feature.ITEMS);
    }
    private static void load() {
        if (loaded) return;
        loaded = true;
        catalog = AccessoryCatalog.empty();
        if (Files.exists(CATALOG_PATH)) try {
            if (Files.size(CATALOG_PATH) > MAX_CACHE_BYTES) throw new IOException("Accessory catalog exceeds 1 MiB.");
            byte[] bytes;
            try (var input = Files.newInputStream(CATALOG_PATH)) { bytes = input.readNBytes(MAX_CACHE_BYTES + 1); }
            if (bytes.length > MAX_CACHE_BYTES) throw new IOException("Accessory catalog exceeds 1 MiB.");
            catalog = AccessoryCatalog.parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception invalid) {
            notice = "Cached accessory metadata is invalid; refreshing online.";
        }
        index = new AccessoryBagIndex(catalog);
        if (Files.exists(PATH)) try {
            byte[] bytes;
            try (var input = Files.newInputStream(PATH)) { bytes = input.readNBytes(MAX_CACHE_BYTES + 1); }
            if (bytes.length > MAX_CACHE_BYTES) throw new IOException("Accessory cache exceeds 1 MiB.");
            index = AccessoryBagIndex.decode(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(), catalog);
        } catch (Exception invalid) {
            writable = false;
            notice = "Accessory cache is invalid; the original file was preserved.";
            CustomCosmeticsClient.LOGGER.warn("Could not load accessory cache: {}", invalid.getMessage());
        }
        refreshCatalog();
    }
    private static void refreshCatalog() {
        if (refreshing || WRITER.isShutdown() || System.currentTimeMillis() - lastRefresh < Duration.ofHours(24).toMillis()) return;
        refreshing = true;
        lastRefresh = System.currentTimeMillis();
        WRITER.execute(() -> {
            try {
                var request = HttpRequest.newBuilder(CATALOG_URI).timeout(Duration.ofSeconds(8)).GET().build();
                var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL).build()
                        .send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                byte[] bytes;
                try (var input = response.body()) { bytes = input.readNBytes(MAX_CACHE_BYTES + 1); }
                if (bytes.length > MAX_CACHE_BYTES) throw new IOException("Accessory metadata exceeds 1 MiB.");
                String json = new String(bytes, StandardCharsets.UTF_8);
                AccessoryCatalog next = AccessoryCatalog.parse(json);
                if (next.size() < 100) throw new IOException("Accessory metadata is incomplete.");
                Files.createDirectories(CATALOG_PATH.getParent());
                Path temporary = CATALOG_PATH.resolveSibling(CATALOG_PATH.getFileName() + ".tmp");
                Files.writeString(temporary, json, StandardCharsets.UTF_8);
                try { Files.move(temporary, CATALOG_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, CATALOG_PATH, StandardCopyOption.REPLACE_EXISTING); }
                Minecraft.getInstance().execute(() -> {
                    catalog = next; index.refreshCatalog(next); notice = ""; refreshing = false;
                });
            } catch (Exception failure) {
                Minecraft.getInstance().execute(() -> {
                    notice = "Accessory metadata refresh failed; cached data remains in use.";
                    lastRefresh = System.currentTimeMillis() - Duration.ofHours(24).toMillis() + Duration.ofMinutes(10).toMillis();
                    refreshing = false;
                });
                CustomCosmeticsClient.LOGGER.warn("Could not refresh accessory metadata", failure);
            }
        });
    }
    private static void tick(Minecraft client) {
        if (!enabled()) { onSkyblock = false; profileKey = ""; lastScreen = null; return; }
        if (!loaded) load();
        if (ticks % 1200 == 0) refreshCatalog();
        if (++ticks % 20 == 0) updateProfile(client);
        if (!onSkyblock || !writable) { lastScreen = null; return; }
        Object currentScreen = com.customcosmetics.compatibility.ClientUiAccess.screen(client);
        if (!(currentScreen instanceof ContainerScreen screen)) { lastScreen = null; return; }
        var match = BAG_TITLE.matcher(screen.getTitle().getString());
        if (!match.matches()) { lastScreen = null; return; }
        if (screen != lastScreen) {
            lastScreen = screen; screenFirstTick = ticks; bagFingerprint = 0;
        }
        if (ticks - screenFirstTick < 10 || ticks % 5 != 0) return;
        int page = match.group(1) == null ? 1 : Integer.parseInt(match.group(1));
        int total = match.group(2) == null ? 1 : Integer.parseInt(match.group(2));
        if (total > 50 || page < 1 || page > total) return;
        ChestMenu menu = screen.getMenu();
        int slots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int fingerprint = 31 * (31 * page + total) + profileKey.hashCode();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            fingerprint = fingerprint * 31 + System.identityHashCode(stack);
        }
        if (fingerprint == bagFingerprint) return;
        bagFingerprint = fingerprint;
        var ids = new HashSet<String>();
        for (int i = 0; i < slots; i++) {
            String id = SwordItems.skyblockId(menu.slots.get(i).getItem()).orElse("");
            if (!id.isEmpty()) ids.add(id);
        }
        if (index.update(profileKey, page, total, ids)) queueSave();
    }
    private static void updateProfile(Minecraft client) {
        profileKey = "";
        onSkyblock = false;
        if (client.player == null || client.getConnection() == null || !PresenceWorld.isHypixel(client)) return;
        for (var entry : client.getConnection().getOnlinePlayers()) {
            Component display = entry.getTabListDisplayName();
            if (display == null) continue;
            String text = display.getString();
            if (text.startsWith("Profile: ")) {
                String name = text.substring(9).strip();
                if (!name.isEmpty() && name.length() <= 64) {
                    profileKey = client.player.getUUID() + ":" + name;
                    onSkyblock = true;
                }
                break;
            }
        }
    }
    private static void append(ItemStack stack, java.util.List<Component> lines) {
        if (!enabled() || !onSkyblock || catalog == null || stack == null || stack.isEmpty()) return;
        String id = SwordItems.skyblockId(stack).orElse("");
        if (catalog.get(id) == null) return;
        var view = index.view(profileKey);
        if (!view.complete()) {
            lines.add(com.customcosmetics.gui.UiText.component("Accessory: Scan every Accessory Bag page").withColor(0xAAAAAA));
            return;
        }
        var report = catalog.report(id, view.highest());
        String label = switch (report.status()) {
            case COLLECTED -> "✔ Collected";
            case UPGRADE -> "✦ Upgrade";
            case UPGRADABLE -> "↑ Upgradable";
            case DOWNGRADE -> "↓ Downgrade";
            case MISSING -> "✖ Missing";
        };
        int color = switch (report.status()) {
            case COLLECTED -> 0x55FF55;
            case UPGRADE -> 0x218BFF;
            case UPGRADABLE -> 0xF8D048;
            case DOWNGRADE -> 0xAAAAAA;
            case MISSING -> 0xFF5555;
        };
        String tiers = switch (report.status()) {
            case COLLECTED -> "";
            case UPGRADE, DOWNGRADE -> " (" + report.ownedTier() + "→" + report.candidateTier() + "/" + report.highestTier() + ")";
            case UPGRADABLE -> " (" + report.ownedTier() + "/" + report.highestTier() + ")";
            case MISSING -> report.highestTier() > 1 ? " (" + report.candidateTier() + "/" + report.highestTier() + ")" : "";
        };
        lines.add(com.customcosmetics.gui.UiText.component("Accessory: ").withColor(0xF57542)
                .append(com.customcosmetics.gui.UiText.component(label).withColor(color))
                .append(Component.literal(tiers).withColor(0xF8F8FF)));
    }
    public static String notice() { return notice; }
    public static boolean clearCurrent() {
        if (!loaded) load();
        if (!writable || profileKey.isEmpty()) return false;
        if (!index.clear(profileKey)) return false;
        queueSave(); return true;
    }
    private static void queueSave() {
        if (!writable) return;
        String json = index.encode();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CACHE_BYTES) {
            notice = "Accessory cache exceeds 1 MiB; it was not saved."; return;
        }
        PENDING.set(json);
        scheduleSave();
    }
    private static void scheduleSave() {
        if (WRITING.compareAndSet(false, true)) WRITER.execute(AccessoryTooltipHelper::writePending);
    }
    private static void writePending() {
        try {
            String next;
            while ((next = PENDING.getAndSet(null)) != null) {
                try {
                    Files.createDirectories(PATH.getParent());
                    Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
                    Files.writeString(temporary, next, StandardCharsets.UTF_8);
                    try { Files.move(temporary, PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING); }
                } catch (IOException failure) {
                    notice = "Accessory cache save failed: " + failure.getMessage();
                    CustomCosmeticsClient.LOGGER.warn("Could not save accessory cache", failure);
                }
            }
        } finally {
            WRITING.set(false);
            if (PENDING.get() != null && !WRITER.isShutdown()) scheduleSave();
        }
    }
}
