// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;

/** SkyBlock accessory-family metadata refreshed from Skyblocker's public data feed. */
public final class AccessoryCatalog {
    public enum Status { COLLECTED, UPGRADE, UPGRADABLE, DOWNGRADE, MISSING }
    public record Entry(String id, String family, int tier, int highestTier) {}
    public record Report(Status status, int ownedTier, int candidateTier, int highestTier) {}
    private final Map<String, Entry> entries;

    private AccessoryCatalog(Map<String, Entry> entries) { this.entries = Map.copyOf(entries); }
    public static AccessoryCatalog empty() { return new AccessoryCatalog(Map.of()); }
    public static AccessoryCatalog parse(String json) { return parse(JsonParser.parseString(json).getAsJsonObject()); }
    static AccessoryCatalog parse(JsonObject root) {
        var tiers = new HashMap<String, Integer>();
        var basic = new HashMap<String, Entry>();
        for (var field : root.entrySet()) {
            String id = field.getKey();
            if (!id.matches("[A-Z0-9_;-]{2,100}")) continue;
            JsonObject data = field.getValue().getAsJsonObject();
            if (data.has("origin") && "RIFT".equals(data.get("origin").getAsString())) continue;
            String family = data.has("family") ? data.get("family").getAsString() : id;
            int tier = data.has("tier") ? data.get("tier").getAsInt() : 1;
            if (family.isBlank() || tier < 1 || tier > 100) continue;
            tiers.merge(family, tier, Math::max);
            basic.put(id, new Entry(id, family, tier, 0));
        }
        var result = new HashMap<String, Entry>();
        basic.forEach((id, entry) -> result.put(id, new Entry(id, entry.family(), entry.tier(), tiers.get(entry.family()))));
        return new AccessoryCatalog(result);
    }
    public int size() { return entries.size(); }
    public Entry get(String id) { return entries.get(id); }
    public Report report(String id, Map<String, Integer> highestOwned) {
        Entry entry = entries.get(id);
        if (entry == null) return null;
        int owned = highestOwned.getOrDefault(entry.family(), 0);
        Status status = owned == 0 ? Status.MISSING
                : entry.tier() > owned ? Status.UPGRADE
                : entry.tier() < owned ? Status.DOWNGRADE
                : owned < entry.highestTier() ? Status.UPGRADABLE : Status.COLLECTED;
        return new Report(status, owned, entry.tier(), entry.highestTier());
    }
    public Map<String, Integer> highestOwned(Iterable<String> ids) {
        var result = new HashMap<String, Integer>();
        for (String id : ids) {
            Entry entry = entries.get(id);
            if (entry != null) result.merge(entry.family(), entry.tier(), Math::max);
        }
        return Map.copyOf(result);
    }
}
