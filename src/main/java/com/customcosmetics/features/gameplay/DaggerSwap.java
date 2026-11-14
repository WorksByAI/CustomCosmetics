// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Nebulune DaggerSwap, Copyright (c) 2025 Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.features.waypoint.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class DaggerSwap {
    private static final Set<String> ASHEN_FAMILY = Set.of("HEARTFIRE_DAGGER", "BURSTFIRE_DAGGER", "FIREDUST_DAGGER");
    private static final Set<String> SPIRIT_FAMILY = Set.of("HEARTMAW_DAGGER", "BURSTMAW_DAGGER", "MAWDUST_DAGGER");
    private static final Attunement[] ATTUNEMENTS = Attunement.values();
    private static Attunement pending;
    private static int targetId, clickAt, nextUse;

    private enum Attunement {
        ASHEN(ASHEN_FAMILY), AURIC(ASHEN_FAMILY), SPIRIT(SPIRIT_FAMILY), CRYSTAL(SPIRIT_FAMILY);

        private final Set<String> daggers;
        private final String label = name() + " ♨";
        Attunement(Set<String> daggers) { this.daggers = daggers; }

        boolean matches(ItemStack stack) {
            String id = LegacyPackItems.id(stack);
            return id != null && daggers.contains(id);
        }
    }

    private DaggerSwap() {}

    public static void attack(Player player, Entity target) {
        if (!GameplayModule.DAGGER_SWAP.enabled() || !(target instanceof LivingEntity)) return;
        var client = Minecraft.getInstance();
        if (player != client.player || client.level == null || !PresenceWorld.isHypixel(client)
                || !"crimson_isle".equals(Waypoints.currentIslandMode())) return;
        Attunement found = null;
        double closest = Double.POSITIVE_INFINITY;
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, target.getBoundingBox().inflate(.7, 1.8, .7))) {
            var name = stand.getCustomName();
            if (name == null) continue;
            String text = name.getString();
            if (text.indexOf('♨') < 0 || text.indexOf(':') < 0) continue;
            double distance = stand.distanceToSqr(target);
            if (distance >= closest) continue;
            for (var attunement : ATTUNEMENTS) if (text.contains(attunement.label)) {
                found = attunement;
                closest = distance;
                break;
            }
        }
        if (found == null || found == pending && target.getId() == targetId) return;
        pending = found;
        targetId = target.getId();
        clickAt = player.tickCount + ConfigManager.daggerSwapDelay + ThreadLocalRandom.current().nextInt(ConfigManager.daggerSwapVariance + 1);
    }

    static void tick(Minecraft client) {
        if (pending == null) return;
        var player = client.player;
        if (!GameplayModule.DAGGER_SWAP.enabled() || client.gameMode == null || !"crimson_isle".equals(Waypoints.currentIslandMode())
                || ClientUiAccess.screen(client) != null || !player.isAlive() || player.isSpectator()
                || !(client.level.getEntity(targetId) instanceof LivingEntity target) || !target.isAlive() || player.distanceToSqr(target) > 36) {
            pending = null;
            return;
        }
        if (player.tickCount < clickAt) return;
        var inventory = player.getInventory();
        var held = player.getMainHandItem();
        if (!pending.matches(held)) {
            for (int slot = 0; slot < 9; slot++) if (pending.matches(inventory.getItem(slot))) {
                inventory.setSelectedSlot(slot);
                return;
            }
        } else if (player.tickCount >= nextUse) {
            var data = held.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().getInt("td_attune_mode").orElse(-1) != pending.ordinal()) {
                client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                nextUse = player.tickCount + 20;
            }
        }
        pending = null;
    }

    static void reset() { pending = null; nextUse = 0; }
}
