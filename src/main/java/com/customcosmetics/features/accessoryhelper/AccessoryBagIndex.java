// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Per-player, per-profile snapshots of pages actually observed in the Accessory Bag. */
public final class AccessoryBagIndex {
    public record View(boolean complete, Map<String, Integer> highest) {
        public static final View EMPTY = new View(false, Map.of());
    }
    private static final class Bag {
        int total;
        final Map<Integer, Set<String>> pages = new HashMap<>();
        View view = View.EMPTY;
        Bag(int total) { this.total = total; }
        void rebuild(AccessoryCatalog catalog) {
            var all = new HashSet<String>();
            pages.values().forEach(all::addAll);
            view = new View(pages.size() == total, catalog.highestOwned(all));
        }
    }
    private final Map<String, Bag> profiles = new HashMap<>();
    private AccessoryCatalog catalog;
    public AccessoryBagIndex(AccessoryCatalog catalog) { this.catalog = catalog; }
    public void refreshCatalog(AccessoryCatalog next) {
        catalog = next;
        profiles.values().forEach(bag -> bag.rebuild(next));
    }
    public View view(String profile) {
        Bag bag = profiles.get(profile);
        return bag == null ? View.EMPTY : bag.view;
    }
    public boolean update(String profile, int page, int total, Set<String> ids) {
        if (profile == null || profile.isBlank() || profile.length() > 120 || total < 1 || total > 50 || page < 1 || page > total)
            throw new IllegalArgumentException("Invalid accessory bag page or profile.");
        if (ids.size() > 500) throw new IllegalArgumentException("Too many accessory IDs on a page.");
        var filtered = new HashSet<String>();
        for (String id : ids) if (id != null && id.matches("[A-Z0-9_;-]{2,100}")) filtered.add(id);
        Bag bag = profiles.get(profile);
        if (bag == null || bag.total != total) {
            bag = new Bag(total);
            profiles.put(profile, bag);
        } else if (filtered.equals(bag.pages.get(page))) return false;
        bag.pages.put(page, Set.copyOf(filtered));
        bag.rebuild(catalog);
        return true;
    }
    public boolean clear(String profile) { return profiles.remove(profile) != null; }
    public String encode() {
        JsonObject root = new JsonObject(); root.addProperty("version", 1);
        JsonObject stored = new JsonObject();
        profiles.forEach((key, bag) -> {
            JsonObject value = new JsonObject(); value.addProperty("total", bag.total);
            JsonObject pages = new JsonObject();
            bag.pages.forEach((number, ids) -> {
                JsonArray array = new JsonArray();
                ids.stream().sorted().forEach(array::add);
                pages.add(Integer.toString(number), array);
            });
            value.add("pages", pages); stored.add(key, value);
        });
        root.add("profiles", stored);
        return root.toString();
    }
    public static AccessoryBagIndex decode(String json, AccessoryCatalog catalog) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported accessory cache version.");
        JsonObject stored = root.getAsJsonObject("profiles");
        if (stored == null || stored.size() > 100) throw new IllegalArgumentException("Invalid accessory profiles.");
        var index = new AccessoryBagIndex(catalog);
        for (var profile : stored.entrySet()) {
            JsonObject value = profile.getValue().getAsJsonObject();
            int total = value.get("total").getAsInt();
            JsonObject pages = value.getAsJsonObject("pages");
            if (pages == null || pages.size() > 50) throw new IllegalArgumentException("Invalid accessory pages.");
            for (var page : pages.entrySet()) {
                int number = Integer.parseInt(page.getKey());
                var ids = new HashSet<String>();
                for (var id : page.getValue().getAsJsonArray()) ids.add(id.getAsString());
                index.update(profile.getKey(), number, total, ids);
            }
        }
        return index;
    }
}
