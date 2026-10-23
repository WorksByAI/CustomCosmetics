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
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/** Local render-only armor assets and tint, keyed by the physical SkyBlock item's UUID. */
public final class ArmorAppearance {
    public interface AssetAccess { EquipmentAssetManager customcosmetics$equipmentAssets(); }
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
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return false;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (!(dispatcher instanceof AssetAccess access)) return false;
        EquipmentAssetManager assets = access.customcosmetics$equipmentAssets();
        if (assets == null) return false;
        var info = assets.get(ResourceKey.create(EquipmentAssets.ROOT_ID, identifier));
        return info != EquipmentAssetManager.MISSING && !info.getLayers(layer).isEmpty();
    }
    public static ResourceKey<EquipmentAsset> model(ItemStack stack, ResourceKey<EquipmentAsset> original, EquipmentClientInfo.LayerType layer) {
        ItemAppearanceStore store = activeStore();
        if (store == null || !store.hasArmorModels()) return original;
        String id = store.getCanonical(ItemCosmetics.uuid(stack)).armorModel();
        if (id.isEmpty() || !available(id, layer)) return original;
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse(id));
    }
    public static int color(ItemStack stack, int original) {
        ItemAppearanceStore store = activeStore();
        if (store == null || !store.hasArmorColors()) return original;
        Integer selected = store.getCanonical(ItemCosmetics.uuid(stack)).armorColor();
        return selected == null ? original : 0xFF000000 | selected;
    }
    /** A render-state copy for player-head helmets; the inventory stack is never changed. */
    public static ItemStack headProxy(ItemStack original, EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD && HeadSkins.hasOverride(original)) return ItemStack.EMPTY;
        ItemAppearanceStore store = activeStore();
        if (slot != EquipmentSlot.HEAD || store == null || !store.hasArmorModels() || !original.is(Items.PLAYER_HEAD)) return original;
        var appearance = store.getCanonical(ItemCosmetics.uuid(original));
        if (!available(appearance.armorModel(), EquipmentSlot.HEAD)) return original;
        ItemStack proxy = new ItemStack(Items.LEATHER_HELMET);
        var data = original.get(DataComponents.CUSTOM_DATA);
        if (data != null) proxy.set(DataComponents.CUSTOM_DATA, data);
        if (original.hasFoil()) proxy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        Integer tint = appearance.armorColor();
        if (tint != null) proxy.set(DataComponents.DYED_COLOR, new DyedItemColor(tint));
        return proxy;
    }
    public static boolean hidesHead(ItemStack headEquipment, boolean hasSkull) {
        if (!hasSkull || headEquipment == null || !headEquipment.is(Items.LEATHER_HELMET)) return false;
        ItemAppearanceStore store = activeStore();
        return store != null && store.hasArmorModels();
    }
}
