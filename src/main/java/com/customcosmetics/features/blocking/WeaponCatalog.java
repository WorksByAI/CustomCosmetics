// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Bundled factual item IDs/names; opening the editor makes no remote requests. */
public final class WeaponCatalog {
    private static final Pattern FORMATTING = Pattern.compile(
        "(?i)§[0-9a-fk-orx]|%%(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white|obfuscated|bold|strikethrough|underlined|italic|reset)%%");
    public record Entry(String id, String name, String category) {
        public Entry { name = plainName(name); }
        public boolean skyblock() { return id.startsWith("skyblock:"); }
        public boolean matches(String query) {
            String q = query.strip().toLowerCase(Locale.ROOT);
            return id.toLowerCase(Locale.ROOT).contains(q) || name.toLowerCase(Locale.ROOT).contains(q)
                || category.toLowerCase(Locale.ROOT).contains(q);
        }
    }
    private static final List<Entry> SKYBLOCK = load();
    private WeaponCatalog() {}
    public static String plainName(String name) { return FORMATTING.matcher(name).replaceAll("").strip(); }
    public static List<Entry> skyblock() { return SKYBLOCK; }
    public static boolean vanillaWeapon(String id) {
        if (!id.startsWith("minecraft:")) return false;
        String path = id.substring(10);
        return path.endsWith("_sword") || path.endsWith("_axe") || path.endsWith("_spear")
            || Set.of("bow", "crossbow", "trident", "mace").contains(path);
    }
    private static List<Entry> load() {
        var stream = WeaponCatalog.class.getResourceAsStream("/assets/customcosmetics/skyblock-weapons.json");
        if (stream == null) throw new IllegalStateException("Missing SkyBlock weapon catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var entries = new ArrayList<Entry>();
            var ids = new HashSet<String>();
            for (var element : root.getAsJsonArray("items")) {
                var item = element.getAsJsonObject();
                String id = BlockingRules.normalize("skyblock:" + item.get("id").getAsString());
                if (id.isEmpty() || !ids.add(id)) throw new IllegalStateException("Invalid weapon catalog identity");
                entries.add(new Entry(id, item.get("name").getAsString(), item.get("category").getAsString()));
            }
            entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id));
            return List.copyOf(entries);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read weapon catalog", e); }
    }
}
