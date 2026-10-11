// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 CustomCosmetics contributors
package com.customcosmetics.features.blocking;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Uses received SkyBlock identity before the vanilla appearance. No network calls or item mutations. */
public final class SwordItems {
    private static final Set<String> SKYBLOCK_SWORDS = loadIds();
    private static final IdentityHashMap<CustomData, Optional<String>> IDS = new IdentityHashMap<>();
    private SwordItems() {}

    private static Set<String> loadIds() {
        var stream = SwordItems.class.getResourceAsStream("/assets/customcosmetics/skyblock-swords.txt");
        if (stream == null) throw new IllegalStateException("Missing bundled SkyBlock sword identities");
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return Set.copyOf(reader.lines().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .collect(Collectors.toSet()));
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read bundled sword identities", e); }
    }

    static Optional<String> skyblockId(CompoundTag data) {
        if (data == null) return Optional.empty();
        // Modern SkyBlock uses the root custom-data id; older formats nest it in ExtraAttributes.
        if (data.contains("id")) return Optional.of(data.getStringOr("id", ""));
        return data.getCompound("ExtraAttributes").filter(extra -> extra.contains("id"))
            .map(extra -> extra.getStringOr("id", ""));
    }

    public static Optional<String> skyblockId(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        // Components are immutable; avoid copying a whole item NBT payload on every rendered frame.
        synchronized (IDS) {
            if (IDS.size() >= 1024) IDS.clear();
            return IDS.computeIfAbsent(data, value -> skyblockId(value.copyTag()));
        }
    }

    static boolean eligible(Optional<String> id, boolean vanillaSword) {
        return id.isPresent() ? SKYBLOCK_SWORDS.contains(id.get().strip().toUpperCase(Locale.ROOT)) : vanillaSword;
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && eligible(skyblockId(stack), stack.is(ItemTags.SWORDS));
    }
}
