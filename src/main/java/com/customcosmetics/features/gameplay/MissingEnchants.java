// Adapted from Athen MissingEnchants, Copyright (c) 2025-2026 Starred. BSD-3-Clause.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.gui.UiText;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class MissingEnchants {
    private static final Pattern TYPE = Pattern.compile("\\b(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL)\\s+(?:DUNGEON\\s+)?([A-Z]+(?: [A-Z]+)*)");
    private static final URI REPO = URI.create("https://data.starred.foo/enchants.json");
    private static long nextLoad;
    private static volatile Enchants data;

    private record Enchants(Map<String, List<String>> byType, List<Set<String>> pools) {}

    private MissingEnchants() {}

    public static void init() {
        ItemTooltipCallback.EVENT.register((stack, context, type, tooltip) -> add(stack, tooltip));
    }

    private static void add(ItemStack stack, List<Component> tooltip) {
        if (!ConfigManager.missingEnchantsEnabled) return;
        var client = Minecraft.getInstance();
        if (ConfigManager.missingEnchantsRequireShift && !client.hasShiftDown() || !PresenceWorld.isSkyblock(client)) return;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;
        var root = custom.copyTag();
        var extra = root.getCompound("ExtraAttributes").orElse(root);
        if (extra.getString("id").orElse("").isEmpty()) return;
        var active = extra.getCompound("enchantments").map(tag -> tag.keySet()).orElse(Set.of());
        if (active.contains("ultimate_one_for_all") || active.contains("one_for_all")) return;
        var enchants = data;
        if (enchants == null) { load(); return; }
        List<String> supported = null;
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            var match = TYPE.matcher(tooltip.get(i).getString());
            if (!match.find()) continue;
            supported = enchants.byType().get(match.group(1));
            break;
        }
        if (supported == null || supported.isEmpty()) return;
        var missing = new ArrayList<String>();
        boolean ultimate = active.stream().anyMatch(id -> id.startsWith("ultimate_"));
        if (!ultimate && supported.stream().anyMatch(id -> id.startsWith("ultimate_"))) missing.add(UiText.tr("Ultimate Enchant"));
        for (String enchant : supported) {
            if (enchant.startsWith("ultimate_") || active.contains(enchant)) continue;
            boolean conflicts = false;
            for (var pool : enchants.pools()) {
                if (pool.contains(enchant) && active.stream().anyMatch(pool::contains)) { conflicts = true; break; }
            }
            if (!conflicts) missing.add(pretty(enchant));
        }
        if (missing.isEmpty()) return;
        tooltip.add(Component.literal(UiText.tr("Missing Enchants")).withColor(0xCF9AFF));
        for (int i = 0; i < missing.size(); i += 3)
            tooltip.add(Component.literal(" • " + String.join(", ", missing.subList(i, Math.min(i + 3, missing.size())))).withColor(0xBBBBBB));
    }

    private static String pretty(String value) {
        var result = new StringBuilder();
        boolean capital = true;
        for (char c : value.toCharArray()) {
            if (c == '_') { result.append(' '); capital = true; }
            else { result.append(capital ? Character.toUpperCase(c) : c); capital = false; }
        }
        return result.toString();
    }

    private static void load() {
        long now = System.currentTimeMillis();
        if (now < nextLoad || !com.customcosmetics.sync.RemoteAccess.online()) return;
        nextLoad = now + 300_000;
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build()
            .sendAsync(HttpRequest.newBuilder(REPO).timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofInputStream())
            .thenAcceptAsync(response -> {
                try (var input = response.body()) {
                    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                    byte[] bytes = input.readNBytes(262_145);
                    if (bytes.length > 262_144) throw new IOException("Enchant data is too large");
                    data = parse(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (IOException | RuntimeException failure) {
                    CustomCosmeticsClient.LOGGER.debug("Could not load the enchantment list", failure);
                }
            }).exceptionally(failure -> {
                CustomCosmeticsClient.LOGGER.debug("Could not reach the enchantment repository", failure);
                return null;
            });
    }

    private static Enchants parse(JsonObject source) {
        var table = new HashMap<String, TreeSet<String>>();
        for (String category : List.of("NORMAL", "STACKING", "ULTIMATE")) {
            var entries = source.getAsJsonObject(category);
            if (entries == null) continue;
            for (var entry : entries.entrySet()) {
                var item = entry.getValue().getAsJsonObject();
                if (!item.has("nbtName") || !item.has("appliedTo")) continue;
                String id = item.get("nbtName").getAsString().toLowerCase(Locale.ROOT);
                for (var type : item.getAsJsonArray("appliedTo"))
                    table.computeIfAbsent(type.getAsString(), ignored -> new TreeSet<>()).add(id);
            }
        }
        var byType = new HashMap<String, List<String>>();
        table.forEach((type, values) -> byType.put(type, List.copyOf(values)));
        var pools = new ArrayList<Set<String>>();
        for (var entry : source.getAsJsonArray("ENCHANT_POOLS")) {
            var pool = new TreeSet<String>();
            for (var id : entry.getAsJsonArray()) pool.add(id.getAsString().toLowerCase(Locale.ROOT));
            pools.add(Set.copyOf(pool));
        }
        return new Enchants(Map.copyOf(byType), List.copyOf(pools));
    }
}
