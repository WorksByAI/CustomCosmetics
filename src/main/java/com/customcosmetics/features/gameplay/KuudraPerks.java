// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Athen BlockPerks, Copyright (c) 2025-2026 Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.map.MapEditor;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

public final class KuudraPerks {
    public enum Group {
        CANNONEER("Cannoneer", "Accelerated Shot", "Blast Radius", "Cannon Proficiency", "Multi-Shot", "Rapid Fire", "Steady Aim"),
        CROWD_CONTROL("Crowd Control", "Sweeping Edge", "Freezing Touch", "Bonus Damage", "Antibiotic", "Blight Slayer"),
        SPECIALIST("Specialist", "Steady Hands", "Ballista Mechanic", "Bomberman", "Mining Frenzy"),
        SUPPORT("Support", "Healing Aura", "Mana Aura", "Protective Aura", "Faster Respawn"),
        BASIC("Basic", "Auto Revive", "Human Cannonball", "Elle's Lava Rod", "Elle's Pickaxe");

        public final String title;
        public final List<String> perks;
        Group(String title, String... perks) {
            this.title = title;
            this.perks = List.of(perks);
        }
    }

    public static final Set<String> DEFAULT = Set.of("Bomberman", "Mining Frenzy", "Auto Revive", "Elle's Lava Rod", "Elle's Pickaxe");
    private static KeyMapping override;

    private KuudraPerks() {}

    static void init() {
        override = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.kuudra_perk_override",
            InputConstants.UNKNOWN.getValue(), KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics", "kuudra_perks"))));
    }

    public static KeyMapping overrideKey() { return override; }

    public static boolean blocked(AbstractContainerScreen<?> screen, Slot slot) {
        if (!GameplayModule.KUUDRA_BLOCK_PERKS.enabled() || slot == null || !slot.hasItem()
                || override != null && override.isDown() || !"Perk Menu".equals(screen.getTitle().getString())) return false;
        var client = Minecraft.getInstance();
        if (!PresenceWorld.isHypixel(client) || !MapEditor.automaticKuudra(client)) return false;
        String name = slot.getItem().getHoverName().getString();
        if (ConfigManager.kuudraBlockedPerks.contains(name)) return true;
        int suffix = name.lastIndexOf(' ');
        return suffix > 0 && ConfigManager.kuudraBlockedPerks.contains(name.substring(0, suffix));
    }
}
