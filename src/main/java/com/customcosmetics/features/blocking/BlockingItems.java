// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class BlockingItems {
    private static String[] oldBlack, oldWhite;
    private static Set<String> black = Set.of(), white = Set.of();
    private static final Map<net.minecraft.world.item.Item,String> VANILLA_IDS = new IdentityHashMap<>();
    private static final LinkedHashMap<String,String> SKYBLOCK_IDS = new LinkedHashMap<>(16,.75f,true);
    private BlockingItems() {}
    public static synchronized String identity(ItemStack stack) {
        if(stack==null||stack.isEmpty())return "";
        var skyblock=SwordItems.skyblockId(stack);
        if(skyblock.isPresent()){
            String raw=skyblock.get(),id=SKYBLOCK_IDS.get(raw);if(id!=null)return id;
            id=BlockingRules.normalize("skyblock:"+raw);SKYBLOCK_IDS.put(raw,id);
            if(SKYBLOCK_IDS.size()>1024)SKYBLOCK_IDS.pollFirstEntry();return id;
        }
        return VANILLA_IDS.computeIfAbsent(stack.getItem(),item->BlockingRules.normalize(BuiltInRegistries.ITEM.getKey(item).toString()));
    }
    public static synchronized boolean allowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var mode=BlockingRules.Mode.parse(ConfigManager.swordBlockingListMode);
        if(mode==BlockingRules.Mode.OFF)return SwordItems.isSword(stack);
        refreshLists();
        if(mode==BlockingRules.Mode.BLACKLIST){if(!SwordItems.isSword(stack))return false;return black.isEmpty()||!black.contains(identity(stack));}
        return !white.isEmpty()&&white.contains(identity(stack));
    }
    public static synchronized boolean allowedRemote(String reportedId) {
        if(!ConfigManager.swordBlockingListsApplyToOthers)return true;
        var mode=BlockingRules.Mode.parse(ConfigManager.swordBlockingListMode);
        if(mode==BlockingRules.Mode.OFF)return true;
        refreshLists();
        if(mode==BlockingRules.Mode.BLACKLIST&&black.isEmpty())return true;
        if(mode==BlockingRules.Mode.WHITELIST&&white.isEmpty())return false;
        String id = BlockingRules.normalize(reportedId);
        return BlockingRules.allowsRemote(true,mode,id,black,white);
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
