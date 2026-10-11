// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class BlockingItems {
    private static String[] oldBlack, oldWhite;
    private static Set<String> black = Set.of(), white = Set.of();
    private BlockingItems() {}
    public static String identity(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : BlockingRules.identity(SwordItems.skyblockId(stack),
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    public static synchronized boolean allowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        refreshLists();
        return BlockingRules.allows(BlockingRules.Mode.parse(ConfigManager.swordBlockingListMode), identity(stack),
            SwordItems.isSword(stack), black, white);
    }
    public static synchronized boolean allowedRemote(String reportedId) {
        refreshLists();
        String id = BlockingRules.normalize(reportedId);
        return BlockingRules.allowsRemote(ConfigManager.swordBlockingListsApplyToOthers,
            BlockingRules.Mode.parse(ConfigManager.swordBlockingListMode), id, black, white);
    }
    private static void refreshLists() {
        if (oldBlack != ConfigManager.swordBlockingBlacklist) {
            oldBlack = ConfigManager.swordBlockingBlacklist; black = Set.of(BlockingRules.sanitize(oldBlack));
        }
        if (oldWhite != ConfigManager.swordBlockingWhitelist) {
            oldWhite = ConfigManager.swordBlockingWhitelist; white = Set.of(BlockingRules.sanitize(oldWhite));
        }
    }
    public static List<WeaponCatalog.Entry> catalog() {
        var entries = new TreeMap<String, WeaponCatalog.Entry>();
        for (var item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (WeaponCatalog.vanillaWeapon(id)) entries.put(id, new WeaponCatalog.Entry(id,
                item.getName(item.getDefaultInstance()).getString(), "Minecraft"));
        }
        for (var item : WeaponCatalog.skyblock()) entries.put(item.id(), item);
        for (String[] list : new String[][] {ConfigManager.swordBlockingBlacklist, ConfigManager.swordBlockingWhitelist})
            for (String id : BlockingRules.sanitize(list)) entries.putIfAbsent(id, new WeaponCatalog.Entry(id, id, "Custom"));
        return entries.values().stream().sorted(Comparator.comparing(WeaponCatalog.Entry::name,
            String.CASE_INSENSITIVE_ORDER).thenComparing(WeaponCatalog.Entry::id)).toList();
    }
}
