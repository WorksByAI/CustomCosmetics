// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.config.FeatureModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/** Local render-only armor assets and tint, keyed by the physical SkyBlock item's UUID. */
public final class ArmorAppearance {
    public interface AssetAccess { EquipmentAssetManager customcosmetics$equipmentAssets(); }
    private static final java.util.LinkedHashMap<String,ResourceKey<EquipmentAsset>> ASSET_KEYS = new java.util.LinkedHashMap<>(16,.75f,true);
    private static final java.util.Map<ItemStack,HeadProxy> HEAD_PROXIES = new java.util.WeakHashMap<>();
    private record HeadProxy(CustomData data,ItemAppearanceStore.Appearance appearance,boolean foil,ItemStack stack) {}
    private ArmorAppearance() {}

    private static ItemAppearanceStore activeStore() {
        ItemAppearanceStore store = ItemCosmetics.store();
        return FeatureModules.enabled(FeatureModules.Feature.ITEMS) && store != null && store.enabled() ? store : null;
    }
    public static boolean editable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.PLAYER_HEAD)) return true;
        Equippable equipment = stack.get(DataComponents.EQUIPPABLE);
        if (equipment == null) return false;
        return switch (equipment.slot()) {
            case HEAD, CHEST, LEGS, FEET -> true;
            default -> false;
        };
    }
    public static EquipmentSlot slot(ItemStack stack) {
        if (stack.is(Items.PLAYER_HEAD)) return EquipmentSlot.HEAD;
        Equippable equipment = stack.get(DataComponents.EQUIPPABLE);
        return equipment == null ? null : equipment.slot();
    }
    public static boolean available(String id, EquipmentSlot slot) {
        if (slot == null) return false;
        return available(id, slot == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID);
    }
    private static boolean available(String id, EquipmentClientInfo.LayerType layer) {
        if (id == null || id.isBlank()) return false;
        return available(assetKey(id), layer);
    }
    private static ResourceKey<EquipmentAsset> assetKey(String id) {
        var cached = ASSET_KEYS.get(id);
        if (cached != null) return cached;
        Identifier identifier = ItemCosmetics.modelId(id);
        if (identifier == null) return null;
        var key = ResourceKey.create(EquipmentAssets.ROOT_ID, identifier);
        ASSET_KEYS.put(id, key);
        if (ASSET_KEYS.size() > ItemAppearanceStore.MAX_ENTRIES) ASSET_KEYS.pollFirstEntry();
        return key;
    }
    private static boolean available(ResourceKey<EquipmentAsset> key, EquipmentClientInfo.LayerType layer) {
        if (key == null) return false;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (!(dispatcher instanceof AssetAccess access)) return false;
        EquipmentAssetManager assets = access.customcosmetics$equipmentAssets();
        if (assets == null) return false;
        var info = assets.get(key);
        return info != EquipmentAssetManager.MISSING && !info.getLayers(layer).isEmpty();
    }
    public static ResourceKey<EquipmentAsset> model(ItemStack stack, ResourceKey<EquipmentAsset> original, EquipmentClientInfo.LayerType layer) {
        ItemAppearanceStore store = activeStore();
        if (store == null || !store.hasArmorModels()) return original;
        String id = store.getCanonical(ItemCosmetics.appearanceKey(stack)).armorModel();
        if (id.isEmpty()) return original;
        var key = assetKey(id);
        return available(key, layer) ? key : original;
    }
    public static int color(ItemStack stack, int original) {
        ItemAppearanceStore store = activeStore();
        if (store == null || !store.hasArmorColors()) return original;
        Integer selected = store.getCanonical(ItemCosmetics.appearanceKey(stack)).armorColor();
        return selected == null ? original : 0xFF000000 | selected;
    }
    /** A render-state copy for player-head helmets; the inventory stack is never changed. */
    public static ItemStack headProxy(ItemStack original, EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD && HeadSkins.hasOverride(original)) return ItemStack.EMPTY;
        ItemAppearanceStore store = activeStore();
        if (slot != EquipmentSlot.HEAD || store == null || !store.hasArmorModels() || !original.is(Items.PLAYER_HEAD)) return original;
        var appearance = store.getCanonical(ItemCosmetics.appearanceKey(original));
        if (!available(appearance.armorModel(), EquipmentSlot.HEAD)) return original;
        var data = original.get(DataComponents.CUSTOM_DATA);
        boolean foil = original.hasFoil();
        var cached = HEAD_PROXIES.get(original);
        if (cached != null && cached.data() == data && cached.appearance() == appearance && cached.foil() == foil) return cached.stack();
        ItemStack proxy = new ItemStack(Items.LEATHER_HELMET);
        if (data != null) proxy.set(DataComponents.CUSTOM_DATA, data);
        if (foil) proxy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        Integer tint = appearance.armorColor();
        if (tint != null) proxy.set(DataComponents.DYED_COLOR, new DyedItemColor(tint));
        if (HEAD_PROXIES.size() >= 256) HEAD_PROXIES.clear();
        HEAD_PROXIES.put(original, new HeadProxy(data, appearance, foil, proxy));
        return proxy;
    }
    public static boolean hidesHead(ItemStack headEquipment, boolean hasSkull) {
        if (!hasSkull || headEquipment == null || !headEquipment.is(Items.LEATHER_HELMET)) return false;
        ItemAppearanceStore store = activeStore();
        return store != null && store.hasArmorModels();
    }
}
