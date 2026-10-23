// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.config.FeatureModules;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;

/** Only render-state copies receive profiles; the inventory item and server data remain intact. */
public final class HeadSkins {
    public static final Identifier MODEL = Identifier.parse("minecraft:player_head");
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Path CACHE = CosmeticsConfigDir.getCosmeticsConfigDir().resolve("head-skins.json");
    private static final Map<String, ResolvableProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>();
    private static final Map<ItemStack, Proxy> PROXIES = new WeakHashMap<>();
    private record Proxy(Object revision, int count, String texture, ItemStack stack) {}
    private static List<HeadSkinCatalog.Entry> entries = List.of();
    private static boolean loading;
    private static long nextAttempt;
    private static String notice = "Open the picker to load head skins.";
    private HeadSkins() {}
    public static List<HeadSkinCatalog.Entry> entries() { return entries; }
    public static String notice() { return notice; }
    public static void requestCatalog() {
        if (loading || System.currentTimeMillis() < nextAttempt) return;
        loading = true; notice = "Loading head skins…";
        boolean offline = ConfigManager.offlineMode;
        CompletableFuture.runAsync(() -> {
            long cachedAt = 0;
            try {
                if (Files.isRegularFile(CACHE)) {
                    try (var input = Files.newInputStream(CACHE)) {
                        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                        if (bytes.length > MAX_BYTES) throw new java.io.IOException("Head catalog is too large.");
                        var cached = HeadSkinCatalog.parse(new String(bytes, StandardCharsets.UTF_8));
                        if (cached.isEmpty()) throw new java.io.IOException("Cached head catalog is empty.");
                        Minecraft.getInstance().execute(() -> entries = cached);
                    }
                    cachedAt = Files.getLastModifiedTime(CACHE).toMillis();
                }
            } catch (Exception invalidCache) { CustomCosmeticsClient.LOGGER.warn("Could not load cached head skins: {}", invalidCache.getMessage()); }
            try {
                if (offline) throw new java.io.IOException("Offline mode: using cached head skins. You can also paste a texture.");
                if (cachedAt > System.currentTimeMillis() - Duration.ofHours(24).toMillis()) {
                    Minecraft.getInstance().execute(() -> { loading = false; nextAttempt = System.currentTimeMillis() + Duration.ofHours(24).toMillis(); notice = "No matching head skins"; });
                    return;
                }
                var request = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/resources/skyblock/items"))
                        .timeout(Duration.ofSeconds(20)).GET().build();
                var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
                        .send(request, HttpResponse.BodyHandlers.ofInputStream());
                byte[] bytes;
                try (var input = response.body()) { bytes = input.readNBytes(MAX_BYTES + 1); }
                if (response.statusCode() != 200 || bytes.length > MAX_BYTES) throw new java.io.IOException("Head catalog request failed: HTTP " + response.statusCode());
                var loaded = HeadSkinCatalog.parse(new String(bytes, StandardCharsets.UTF_8));
                if (loaded.isEmpty()) throw new java.io.IOException("The head catalog was empty.");
                Files.createDirectories(CACHE.getParent());
                Path temporary = Files.createTempFile(CACHE.getParent(), "head-skins-", ".tmp");
                try {
                    Files.write(temporary, bytes);
                    try { Files.move(temporary, CACHE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, CACHE, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
                Minecraft.getInstance().execute(() -> { entries = loaded; loading = false; nextAttempt = System.currentTimeMillis() + Duration.ofHours(24).toMillis(); notice = "No matching head skins"; });
            } catch (Exception failure) {
                Minecraft.getInstance().execute(() -> { loading = false; nextAttempt = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis(); notice = "Head catalog unavailable; cached skins remain available. Paste a texture to use another skin."; });
                CustomCosmeticsClient.LOGGER.warn("Could not refresh head skins: {}", failure.getMessage());
            }
        });
    }
    public static ResolvableProfile profile(String texture) {
        if (!PROFILES.containsKey(texture) && PROFILES.size() >= 256) PROFILES.clear();
        return PROFILES.computeIfAbsent(texture, value -> {
            var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", HeadTexture.property(value))));
            return ResolvableProfile.createResolved(new GameProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), "ccos-head", properties));
        });
    }
    public static ItemStack icon(String texture) {
        if (!ICONS.containsKey(texture) && ICONS.size() >= 128) ICONS.clear();
        return ICONS.computeIfAbsent(texture, value -> {
            var head = new ItemStack(Items.PLAYER_HEAD); head.set(DataComponents.PROFILE, profile(value)); return head;
        });
    }
    private static String selected(ItemStack stack) {
        var store = ItemCosmetics.store();
        if (store == null || !store.enabled() || !store.hasHeadTextures() || stack.isEmpty()
                || !FeatureModules.enabled(FeatureModules.Feature.ITEMS)) return "";
        return store.getCanonical(ItemCosmetics.uuid(stack)).headTexture();
    }
    public static boolean hasOverride(ItemStack stack) { return !selected(stack).isEmpty(); }
    public static ItemStack renderProxy(ItemStack source) {
        String texture = selected(source);
        if (texture.isEmpty()) return source;
        Object revision = ItemCosmetics.componentIdentity(source);
        Proxy cached = PROXIES.get(source);
        if (cached != null && cached.count() == source.getCount() && cached.texture().equals(texture) && revision != null && cached.revision() == revision) return cached.stack();
        if (PROXIES.size() >= 256) PROXIES.clear();
        var copy = source.copy(); copy.set(DataComponents.PROFILE, profile(texture)); copy.set(DataComponents.ITEM_MODEL, MODEL);
        PROXIES.put(source, new Proxy(revision, source.getCount(), texture, copy)); return copy;
    }
    public static ItemStack preview(ItemStack source, String texture) {
        var copy = ItemCosmetics.preview(source, MODEL); copy.set(DataComponents.PROFILE, profile(texture)); return copy;
    }
    public static void worn(LivingEntity entity, LivingEntityRenderState state) {
        var store = ItemCosmetics.store();
        if (store == null || !store.hasHeadTextures() || !store.enabled() || !FeatureModules.enabled(FeatureModules.Feature.ITEMS)) return;
        String texture = selected(entity.getItemBySlot(EquipmentSlot.HEAD));
        if (texture.isEmpty()) return;
        state.headItem.clear(); state.wornHeadType = SkullBlock.Types.PLAYER; state.wornHeadProfile = profile(texture);
    }
}
