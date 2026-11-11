// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.features.waypoint.Waypoints;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Visual-only support for a subset of Catharsis environment and tooltip definitions. */
public final class CatharsisPackSupport extends SimplePreparableReloadListener<CatharsisPackSupport.Pack> {
    private static final Identifier TOOLTIP = Identifier.fromNamespaceAndPath("catharsis", "tooltip.json");
    private static final Set<String> VISUAL_ATTRIBUTES = Set.of("fog_color", "fog_start_distance", "fog_end_distance",
        "sky_fog_end_distance", "cloud_fog_end_distance", "water_fog_color", "water_fog_start_distance",
        "water_fog_end_distance", "sky_color", "sunrise_sunset_color", "sky_light_color", "ambient_light_color");
    private static volatile Pack pack = new Pack(Map.of(), null);
    record Pack(Map<EnvironmentAttribute<?>, Object> environment, Tooltip tooltip) {}
    private interface Tooltip { Identifier resolve(ItemStack stack); }

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("catharsis"))
            ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath("customcosmetics", "catharsis_pack"), new CatharsisPackSupport());
    }

    public static Object environment(EnvironmentAttribute<?> attribute) {
        var overrides = pack.environment();
        return !overrides.isEmpty() && CatharsisItemProperties.enabled() ? overrides.get(attribute) : null;
    }

    public static Identifier tooltip(ItemStack stack) {
        var tooltip = pack.tooltip();
        return tooltip != null && CatharsisItemProperties.enabled() ? tooltip.resolve(stack) : null;
    }

    @Override protected Pack prepare(ResourceManager manager, ProfilerFiller profiler) {
        var environment = new HashMap<EnvironmentAttribute<?>, Object>();
        var definitions = new TreeSet<Identifier>();
        try (var packs = manager.listPacks()) {
            packs.forEach(pack -> {
                for (var namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES))
                    pack.listResources(PackType.CLIENT_RESOURCES, namespace, "catharsis/environment_modifier",
                        (id, resource) -> { if (id.getPath().endsWith(".json")) definitions.add(id); });
            });
        }
        for (var id : definitions) {
            var definition = manager.getResource(id).orElse(null);
            if (definition == null) continue;
            try (var reader = definition.openAsReader()) {
                readEnvironment(JsonParser.parseReader(reader).getAsJsonObject(), environment, 0);
            } catch (IOException | RuntimeException error) {
                CustomCosmeticsClient.LOGGER.debug("Could not load environment definition {}", id, error);
            }
        }
        Tooltip tooltip = null;
        var resource = manager.getResource(TOOLTIP).orElse(null);
        if (resource != null) {
            try (var reader = resource.openAsReader()) {
                tooltip = readTooltip(JsonParser.parseReader(reader), 0);
            } catch (IOException | RuntimeException error) {
                CustomCosmeticsClient.LOGGER.debug("Could not load Catharsis tooltip definition", error);
            }
        }
        return new Pack(Map.copyOf(environment), tooltip);
    }

    @Override protected void apply(Pack loaded, ResourceManager manager, ProfilerFiller profiler) { pack = loaded; }

    static void readEnvironment(JsonObject node, Map<EnvironmentAttribute<?>, Object> values, int depth) {
        if (depth >= 16 || node.has("condition") && !"true".equals(type(node.getAsJsonObject("condition")))) return;
        switch (type(node)) {
            case "composite" -> {
                if (node.has("modifiers")) for (var modifier : node.getAsJsonArray("modifiers"))
                    if (modifier.isJsonObject()) readEnvironment(modifier.getAsJsonObject(), values, depth + 1);
            }
            case "environmental_attribute" -> {
                var id = Identifier.tryParse(string(node, "attribute"));
                if (id == null || !id.getNamespace().equals("minecraft")) return;
                String path = id.getPath().startsWith("visual/") ? id.getPath().substring(7) : id.getPath();
                if (!VISUAL_ATTRIBUTES.contains(path)) return;
                var provider = node.getAsJsonObject("provider");
                if (provider == null || !"override".equals(type(provider)) || !provider.has("value")) return;
                var attribute = BuiltInRegistries.ENVIRONMENT_ATTRIBUTE.getValue(Identifier.withDefaultNamespace("visual/" + path));
                if (attribute != null) attribute.type().valueCodec().parse(JsonOps.INSTANCE, provider.get("value"))
                    .result().ifPresent(value -> values.put(attribute, value));
            }
            default -> { }
        }
    }

    private static Tooltip readTooltip(JsonElement json, int depth) {
        if (depth >= 16 || json == null || !json.isJsonObject()) return null;
        var node = json.getAsJsonObject();
        String type = string(node, "type");
        if (type.equals("catharsis:texture")) {
            String name = string(node, "texture");
            var texture = name.isBlank() ? null : Identifier.tryParse(name);
            return texture == null ? null : stack -> texture;
        }
        if (!type.equals("minecraft:select") || !node.has("cases")) return null;
        String property = string(node, "property"), key = string(node, "data_type");
        if (!Set.of("catharsis:item_model", "catharsis:data_type", "catharsis:skyblock_island").contains(property)) return null;
        var cases = new HashMap<String, Tooltip>();
        for (var entry : node.getAsJsonArray("cases")) {
            if (!entry.isJsonObject()) continue;
            var choice = entry.getAsJsonObject();
            var model = readTooltip(choice.get("model"), depth + 1);
            var when = choice.get("when");
            if (model == null || when == null) continue;
            if (when.isJsonPrimitive()) cases.put(when.getAsString(), model);
            else if (when.isJsonArray()) for (var value : when.getAsJsonArray())
                if (value.isJsonPrimitive()) cases.put(value.getAsString(), model);
        }
        var fallback = readTooltip(node.get("fallback"), depth + 1);
        return stack -> {
            String selected = switch (property) {
                case "catharsis:item_model" -> {
                    var id = stack.get(DataComponents.ITEM_MODEL);
                    yield id == null ? null : id.toString();
                }
                case "catharsis:data_type" -> CatharsisItemProperties.itemData(stack, key);
                default -> Waypoints.currentIslandMode();
            };
            var model = cases.getOrDefault(selected, fallback);
            return model == null ? null : model.resolve(stack);
        };
    }

    private static String type(JsonObject node) {
        String type = string(node, "type");
        return type.startsWith("catharsis:") ? type.substring("catharsis:".length()) : type;
    }

    private static String string(JsonObject node, String field) {
        var element = node.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : "";
    }
}
