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
import java.nio.charset.StandardCharsets;
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
    private static final Map<String, ResolvableProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>();
    private static final Map<ItemStack, Proxy> PROXIES = new WeakHashMap<>();
    private record Proxy(Object revision, int count, String texture, ItemStack stack) {}
    private static List<HeadSkinCatalog.Entry> entries = List.of();
    private static boolean requested;
    private static long ticks;
    private static Map<String, HeadSkinCatalog.Animation> animations = Map.of();
    private static final Map<String, FrameProfiles> ANIMATED_PROFILES = new LinkedHashMap<>(16, .75f, true);
    private static final Map<ItemStack, String> PREVIEWS = new WeakHashMap<>();
    private record FrameProfiles(HeadSkinCatalog.Animation definition, ResolvableProfile[] profiles) {}
    private static String notice = "Open the picker to load head skins.";
    private HeadSkins() {}
    public static List<HeadSkinCatalog.Entry> entries() { return entries; }
    public static String notice() { return notice; }
    public static void requestCatalog() {
        if (requested || !FeatureModules.enabled(FeatureModules.Feature.ITEMS)) return;
        requested = true; notice = "Loading head skins…";
        boolean offline = ConfigManager.offlineMode;
        var client = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                var result = new HeadSkinRepository(CosmeticsConfigDir.getCosmeticsConfigDir()).load(offline,
                    data -> client.execute(() -> install(data)));
                client.execute(() -> notice = result.failures().isEmpty() ? "No matching head skins"
                    : "Head catalog unavailable; cached skins remain available. Paste a texture to use another skin.");
                for (var failure : result.failures()) CustomCosmeticsClient.LOGGER.warn("Could not refresh head skins: {}", failure);
            } catch (Exception failure) {
                client.execute(() -> notice = "Head catalog unavailable; cached skins remain available. Paste a texture to use another skin.");
                CustomCosmeticsClient.LOGGER.warn("Could not load head skins", failure);
            }
        });
    }
    private static void install(HeadSkinCatalog.Data data) {
        entries = data.entries(); animations = data.animations(); ANIMATED_PROFILES.clear();
    }
    /** Only a counter advances; no skin list, inventory scan or profile allocation on ticks. */
    public static void tick() { requestCatalog(); ticks++; }
    public static ResolvableProfile profile(String texture) {
        if (texture.startsWith("animated:")) {
            var cached = ANIMATED_PROFILES.get(texture);
            if (cached == null) {
                var animation = animations.get(HeadTexture.animationId(texture));
                if (animation == null) animation = new HeadSkinCatalog.Animation(1, List.of(texture.substring(texture.lastIndexOf(':') + 1)));
                if (ANIMATED_PROFILES.size() >= 32) ANIMATED_PROFILES.remove(ANIMATED_PROFILES.keySet().iterator().next());
                cached = new FrameProfiles(animation, new ResolvableProfile[animation.frames().size()]);
                ANIMATED_PROFILES.put(texture, cached);
            }
            int index = cached.definition().frame(ticks);
            var frame = cached.profiles()[index];
            if (frame == null) cached.profiles()[index] = frame = staticProfile(cached.definition().frames().get(index));
            return frame;
        }
        return staticProfile(texture);
    }
    private static ResolvableProfile staticProfile(String texture) {
        if (!PROFILES.containsKey(texture) && PROFILES.size() >= 256) PROFILES.remove(PROFILES.keySet().iterator().next());
        return PROFILES.computeIfAbsent(texture, value -> {
            var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", HeadTexture.property(value))));
            return ResolvableProfile.createResolved(new GameProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), "ccos-head", properties));
        });
    }
    public static ItemStack icon(String texture) {
        if (!ICONS.containsKey(texture) && ICONS.size() >= 128) ICONS.remove(ICONS.keySet().iterator().next());
        var head = ICONS.computeIfAbsent(texture, value -> new ItemStack(Items.PLAYER_HEAD));
        updateProfile(head, profile(texture)); return head;
    }
    private static void updateProfile(ItemStack stack, ResolvableProfile profile) {
        if (stack.get(DataComponents.PROFILE) != profile) stack.set(DataComponents.PROFILE, profile);
    }
    private static String selected(ItemStack stack) {
        if (!FeatureModules.enabled(FeatureModules.Feature.ITEMS)) return "";
        var preview = PREVIEWS.get(stack);
        if (preview != null) return preview;
        var store = ItemCosmetics.store();
        if (store == null || !store.enabled() || !store.hasHeadTextures() || stack.isEmpty()
                || !FeatureModules.enabled(FeatureModules.Feature.ITEMS)) return "";
        return store.getCanonical(ItemCosmetics.appearanceKey(stack)).headTexture();
    }
    public static boolean hasOverride(ItemStack stack) { return !selected(stack).isEmpty(); }
    public static ItemStack renderProxy(ItemStack source) {
        String texture = selected(source);
        if (texture.isEmpty()) return source;
        Object revision = ItemCosmetics.componentIdentity(source);
        Proxy cached = PROXIES.get(source);
        var current = profile(texture);
        if (cached != null && cached.count() == source.getCount() && cached.texture().equals(texture) && revision != null && cached.revision() == revision) {
            updateProfile(cached.stack(), current); return cached.stack();
        }
        if (PREVIEWS.containsKey(source)) { updateProfile(source, current); return source; }
        if (PROXIES.size() >= 256) PROXIES.clear();
        var copy = source.copy(); copy.set(DataComponents.PROFILE, current); copy.set(DataComponents.ITEM_MODEL, MODEL);
        PROXIES.put(source, new Proxy(revision, source.getCount(), texture, copy)); return copy;
    }
    public static ItemStack preview(ItemStack source, String texture) {
        var copy = ItemCosmetics.preview(source, MODEL); copy.set(DataComponents.PROFILE, profile(texture)); PREVIEWS.put(copy, texture); return copy;
    }
    public static void worn(LivingEntity entity, LivingEntityRenderState state) {
        var store = ItemCosmetics.store();
        if (store == null || !store.hasHeadTextures() || !store.enabled() || !FeatureModules.enabled(FeatureModules.Feature.ITEMS)) return;
        String texture = selected(entity.getItemBySlot(EquipmentSlot.HEAD));
        if (texture.isEmpty()) return;
        state.headItem.clear(); state.wornHeadType = SkullBlock.Types.PLAYER; state.wornHeadProfile = profile(texture);
    }
}
