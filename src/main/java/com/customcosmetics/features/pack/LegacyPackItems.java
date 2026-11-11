// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.items.HeadSkins;
import com.google.gson.stream.JsonReader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class LegacyPackItems {
    public interface CustomDataView {
        CompoundTag customcosmetics$readOnlyTag();
    }

    private record Entry(Identifier model, String texture) {}
    private static final String QUIVER_ARROW = "quiver_arrow";
    private static final Map<String, Integer> ATTUNEMENT = Map.of(
        "FIREDUST_DAGGER", 1, "BURSTFIRE_DAGGER", 1, "HEARTFIRE_DAGGER", 1,
        "MAWDUST_DAGGER", 3, "BURSTMAW_DAGGER", 3, "HEARTMAW_DAGGER", 3);
    private static final Set<String> KATANAS = Set.of("VOIDEDGE_KATANA", "VORPAL_KATANA", "ATOMSPLIT_KATANA");
    private static volatile Map<String, Entry> catalog = Map.of();
    private static boolean loading;

    private LegacyPackItems() {}

    static synchronized void prepare() {
        if (loading) return;
        loading = true;
        CompletableFuture.runAsync(() -> {
            var input = LegacyPackItems.class.getResourceAsStream("/assets/customcosmetics/pack/legacy-items.json");
            if (input == null) {
                CustomCosmeticsClient.LOGGER.warn("SkyBlock legacy item catalog is missing");
                return;
            }
            var items = new HashMap<String, Entry>();
            var models = new HashMap<String, Identifier>();
            try (var reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String id = reader.nextName();
                    reader.beginArray();
                    var model = models.computeIfAbsent(reader.nextString(), Identifier::withDefaultNamespace);
                    String texture = reader.hasNext() ? reader.nextString() : "";
                    reader.endArray();
                    items.put(id, new Entry(model, texture));
                }
                reader.endObject();
                catalog = Map.copyOf(items);
            } catch (Exception error) {
                CustomCosmeticsClient.LOGGER.warn("Could not load SkyBlock legacy item catalog", error);
            }
        });
    }

    public static Set<String> ids() {
        prepare();
        return catalog.keySet();
    }

    public static String id(ItemStack stack) {
        return id(tag(stack));
    }

    private static CompoundTag tag(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : ((CustomDataView) (Object) data).customcosmetics$readOnlyTag();
    }

    private static String id(CompoundTag tag) {
        if (tag == null) return "";
        return tag.contains(QUIVER_ARROW) ? QUIVER_ARROW : tag.getStringOr("id", "").replace(':', '-');
    }

    public static Identifier model(ItemStack stack, Identifier original) {
        if (!HypixelPackDisabler.usingFallback()) return original;
        boolean official = HypixelPackDisabler.officialAsset(original);
        var replacements = ConfigManager.hypixelPackReplacements;
        if (!official && replacements.isEmpty()) return original;
        CompoundTag tag = tag(stack);
        String id = id(tag);
        if (id.isEmpty()) return original;
        String replacement = replacements.get(id);
        Identifier replacementModel = replacement == null ? null : vanillaModel(replacement);
        if (replacementModel != null) return replacementModel;
        if (!official || ConfigManager.hypixelPackWhitelist.contains(id)) return original;
        if (QUIVER_ARROW.equals(id)) return itemModel(Items.ARROW);
        Entry entry = catalog.get(id);
        if (entry == null) return original;
        // Keep an official model when its legacy item is absent in this Minecraft version.
        if (!com.customcosmetics.features.items.ItemCosmetics.hasModel(entry.model())) return original;
        if (entry.model().equals(HeadSkins.MODEL) && !stack.is(Items.PLAYER_HEAD)
            && texture(entry, tag).isEmpty() && stack.get(DataComponents.PROFILE) == null) return original;
        return dynamicModel(id, stack, tag, original, entry.model());
    }

    private static Identifier dynamicModel(String id, ItemStack stack, CompoundTag tag, Identifier original, Identifier fallback) {
        if (id.equals("CARNIVAL_SHOVEL")) {
            String path = original.getPath();
            if (path.endsWith("/carnival_shovel_iron")) return itemModel(Items.IRON_SHOVEL);
            if (path.endsWith("/carnival_shovel_gold")) return itemModel(Items.GOLDEN_SHOVEL);
            if (path.endsWith("/carnival_shovel_diamond")) return itemModel(Items.DIAMOND_SHOVEL);
        }
        Integer mode = ATTUNEMENT.get(id);
        if (mode != null && tag.getIntOr("td_attune_mode", -1) == mode) {
            return itemModel(mode == 1 ? Items.GOLDEN_SWORD : Items.DIAMOND_SWORD);
        }
        if (KATANAS.contains(id)) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getCooldowns().isOnCooldown(stack)) return itemModel(Items.GOLDEN_SWORD);
        }
        if (id.equals("FUNGI_CUTTER") || id.equals("FUNGI_CUTTER_2") || id.equals("FUNGI_CUTTER_3")) {
            return switch (tag.getStringOr("fungi_cutter_mode", "")) {
                case "RED" -> itemModel(Items.RED_MUSHROOM);
                case "BROWN" -> itemModel(Items.BROWN_MUSHROOM);
                default -> fallback;
            };
        }
        return fallback;
    }

    public static Identifier vanillaModel(String value) {
        var id = Identifier.tryParse(value);
        if (id == null || !"minecraft".equals(id.getNamespace())) return null;
        return BuiltInRegistries.ITEM.getOptional(id).map(LegacyPackItems::itemModel).orElse(null);
    }

    private static Identifier itemModel(Item item) {
        return item.components().get(DataComponents.ITEM_MODEL);
    }

    public static ResolvableProfile headProfile(ItemStack stack, ResolvableProfile original) {
        if (!HypixelPackDisabler.usingFallback() || stack.is(Items.PLAYER_HEAD) || HeadSkins.hasOverride(stack)) return original;
        var tag = tag(stack);
        if (tag == null) return original;
        var entry = catalog.get(id(tag));
        String texture = texture(entry, tag);
        return texture.isEmpty() ? original : HeadSkins.profile(texture);
    }

    private static String texture(Entry entry, CompoundTag tag) {
        String skin = tag.getStringOr("skin", "");
        Entry variant = skin.isEmpty() ? null : catalog.get(skin.replace(':', '-'));
        if (variant != null && !variant.texture().isEmpty()) return variant.texture();
        return entry == null ? "" : entry.texture();
    }

    public static boolean foil(ItemStack stack, boolean original) {
        var glints = ConfigManager.hypixelPackGlints;
        if (glints.isEmpty() || !HypixelPackDisabler.usingFallback() || !Minecraft.getInstance().isSameThread()) return original;
        Boolean override = glints.get(id(stack));
        return override == null ? original : override;
    }
}
