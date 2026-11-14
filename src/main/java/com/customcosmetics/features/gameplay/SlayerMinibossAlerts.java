// SPDX-License-Identifier: BSD-3-Clause AND MIT
// Adapted from Athen MinibossAlert and SlayerMini, copyright 2025 Starred and SkyblockAPI contributors.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.utils.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class SlayerMinibossAlerts {
    private static final Pattern CHAT = Pattern.compile("^SLAYER MINIBOSS! The (.+?) spawned!$");
    private static final Set<String> SPECIAL = Set.of("Deformed Revenant", "Atoned Revenant", "Mutant Tarantula",
        "Primordial Viscount", "Sven Alpha", "Voidcrazed Maniac", "Burningsoul Demon");
    private static final Set<String> REGULAR = Set.of("Revenant Sycophant", "Revenant Champion", "Atoned Champion", "Tarantula Vermin",
        "Tarantula Beast", "Primordial Jockey", "Pack Enforcer", "Sven Follower", "Voidling Devotee", "Voidling Radical",
        "Flare Demon", "Kindleheart Demon");
    private static final Set<Integer> seen = new HashSet<>();

    private SlayerMinibossAlerts() {}

    static void reset() { seen.clear(); }

    static boolean allowMessage(Minecraft client, Component text) {
        if (!GameplayModule.SLAYER_MINIBOSS_ALERT.enabled() || !ConfigManager.minibossChatDetection || !PresenceWorld.isSkyblock(client)) return true;
        String line = ChatFormatting.stripFormatting(text.getString()).strip();
        if (!line.startsWith("SLAYER MINIBOSS!")) return true;
        var match = CHAT.matcher(line);
        if (!match.matches()) return true;
        alert(client, SPECIAL.contains(match.group(1)));
        return ConfigManager.minibossVanillaMessage || !ConfigManager.minibossChatAlert && !ConfigManager.minibossTitle;
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.SLAYER_MINIBOSS_ALERT.enabled() || ConfigManager.minibossChatDetection) { reset(); return; }
        int radius = ConfigManager.minibossDistance;
        if (client.player.tickCount % 20 == 0) seen.removeIf(id -> client.level.getEntity(id) == null);
        for (var label : client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(radius))) {
            if (!label.hasCustomName() || label.tickCount >= 20 || seen.contains(label.getId())) continue;
            if (!(client.level.getEntity(label.getId() - 1) instanceof LivingEntity entity) || entity instanceof ArmorStand
                    || entity.tickCount >= 20 || client.player.distanceToSqr(entity) >= radius * radius) continue;
            String name = ChatFormatting.stripFormatting(label.getCustomName().getString());
            boolean special = SPECIAL.stream().anyMatch(name::contains);
            if ((special || REGULAR.stream().anyMatch(name::contains)) && seen.add(label.getId())) alert(client, special);
        }
    }

    private static void alert(Minecraft client, boolean special) {
        String value = special ? ConfigManager.minibossSpecialText : ConfigManager.minibossAlertText;
        if (ConfigManager.minibossTitle) ClientUiAccess.title(client, Component.literal(value)
            .withStyle(special ? ChatFormatting.RED : ChatFormatting.AQUA), Component.empty());
        if (ConfigManager.minibossChatAlert) MessageUtils.sendMessage(value);
    }
}
