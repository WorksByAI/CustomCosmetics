// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.CosmeticsConfigDir;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Never modifies an ItemStack or contacts either the game or cosmetics server. */
public final class ItemCosmetics {
    private static final Map<CustomData, String> IDENTITIES = new IdentityHashMap<>();
    private static ItemAppearanceStore store;
    private static long lastSave;
    private ItemCosmetics() {}
    public static void init() {
        store = new ItemAppearanceStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("item-appearances.json"));
        try { store.load(); }
        catch (java.io.IOException error) { CustomCosmeticsClient.LOGGER.warn("Could not load local item appearances: {}", error.getMessage()); }
    }
    public static ItemAppearanceStore store() { return store; }
    public static String uuid(CompoundTag data) {
        String direct = ItemAppearanceStore.key(data.getStringOr("uuid", ""));
        if (!direct.isEmpty()) return direct;
        return data.getCompound("ExtraAttributes").map(extra -> ItemAppearanceStore.key(extra.getStringOr("uuid", ""))).orElse("");
    }
    public static String uuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        synchronized (IDENTITIES) {
            if (IDENTITIES.size() >= 1024) IDENTITIES.clear();
            return IDENTITIES.computeIfAbsent(data, value -> uuid(value.copyTag()));
        }
    }
    public static List<Identifier> models() {
        return modelMap().keySet().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
    }
    public static boolean hasModel(Identifier id) { return id != null && modelMap().containsKey(id); }
    private static Map<Identifier, ?> modelMap() {
        var manager = Minecraft.getInstance().getModelManager();
        if (manager instanceof ItemModelCatalog catalog && catalog.customcosmetics$itemModels() != null)
            return catalog.customcosmetics$itemModels();
        return Map.of();
    }
    public static Identifier model(ItemStack stack, Identifier original) {
        if (store == null || !store.enabled()) return original;
        String raw = store.get(uuid(stack)).model();
        Identifier selected = raw.isEmpty() ? null : Identifier.tryParse(raw);
        return hasModel(selected) ? selected : original;
    }
    public static Component name(ItemStack stack, Component original) {
        // The integrated server shares ItemStack classes but must retain its original names.
        if (store == null || !store.enabled() || !Minecraft.getInstance().isSameThread()) return original;
        String raw = store.get(uuid(stack)).name();
        return raw.isEmpty() ? original : ItemNameText.parse(raw);
    }
    public static void tick() {
        if (store != null && store.dirty() && System.currentTimeMillis() - lastSave > 750) save();
    }
    public static boolean save() {
        if (store == null) return false;
        lastSave = System.currentTimeMillis();
        try { store.save(); return true; }
        catch (java.io.IOException error) { CustomCosmeticsClient.LOGGER.warn("Could not save local item appearances: {}", error.getMessage()); return false; }
    }
}
