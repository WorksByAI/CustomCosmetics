// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi SecretTriggerbot and Dungeon by Kyleen, jcnlk and contributors.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.SwingAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.map.MapEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class SecretTriggerbot {
    static final UUID WITHER_ESSENCE = UUID.fromString("2865274b-3097-394e-8149-ec629c72d850");
    static final UUID REDSTONE_KEY = UUID.fromString("fed95410-aba1-39df-9b95-1d4f361eb66e");
    private static final Set<BlockPos> TERMINAL_LEVERS = Set.of(new BlockPos(94,124,113), new BlockPos(106,124,113),
        new BlockPos(27,124,127), new BlockPos(23,132,138), new BlockPos(14,122,55), new BlockPos(2,122,55),
        new BlockPos(86,128,46), new BlockPos(84,121,34));
    private static final Set<BlockPos> DEVICE_LEVERS = Set.of(new BlockPos(62,136,142), new BlockPos(62,133,142),
        new BlockPos(60,134,142), new BlockPos(60,135,142), new BlockPos(58,133,142), new BlockPos(58,136,142));
    private static final BlockPos EXTRA_LEVER = new BlockPos(59,133,142);
    private static final Set<BlockPos> clicked = new HashSet<>();
    private static BlockPos target;
    private static int fireAt;

    private SecretTriggerbot() {}

    static void reset() { clicked.clear(); target = null; }

    static void tick(Minecraft client) {
        if (!GameplayModule.SECRET_TRIGGERBOT.enabled() || !MapEditor.automaticDungeon(client)) {
            if (target != null || !clicked.isEmpty()) reset();
            return;
        }
        if (client.gameMode == null || client.player.isSpectator() || !client.player.isAlive() || Freecam.active()
                || ClientUiAccess.screen(client) != null || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) { target = null; return; }
        var room = MapEditor.currentRoom();
        if (room != null && ("Water Board".equals(room.label()) || "Three Weirdos".equals(room.label()))) { target = null; return; }
        BlockPos pos = hit.getBlockPos();
        if (clicked.contains(pos) || SecretAura.recentlyClicked(pos) || !isSecret(client, pos, inF7Boss(client))) { target = null; return; }
        if (!pos.equals(target)) {
            target = pos.immutable();
            fireAt = client.player.tickCount + ConfigManager.secretInteractDelay;
        }
        if (client.player.tickCount < fireAt) return;
        var inventory = client.player.getInventory();
        int before = inventory.getSelectedSlot();
        if (ConfigManager.secretSwapItem) inventory.setSelectedSlot(ConfigManager.secretSwapSlot - 1);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        SwingAccess.swingMainHand(client.player);
        if (ConfigManager.secretSwapItem && ConfigManager.secretSwapBack) inventory.setSelectedSlot(before);
        clicked.add(target);
        SecretAura.triggerbotClicked(target);
        target = null;
    }

    static boolean inF7Boss(Minecraft client) {
        return GameplayFeatures.isFloorSeven() && client.player.getX() > -7 && client.player.getZ() > -7;
    }

    static boolean isSecret(Minecraft client, BlockPos pos, boolean boss) {
        var state = client.level.getBlockState(pos);
        if (boss) {
            if (!state.is(Blocks.LEVER)) return false;
            if (DEVICE_LEVERS.contains(pos)) return !state.getValue(BlockStateProperties.POWERED);
            if (TERMINAL_LEVERS.contains(pos)) return hasLabel(client, pos.above(), .75, "Not Activated");
            return pos.equals(EXTRA_LEVER) && hasLabel(client, pos, 1, "Inactive");
        }
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.LEVER)) return true;
        if (!(client.level.getBlockEntity(pos) instanceof SkullBlockEntity skull) || skull.getOwnerProfile() == null) return false;
        var id = skull.getOwnerProfile().partialProfile().id();
        return WITHER_ESSENCE.equals(id) || REDSTONE_KEY.equals(id);
    }

    static boolean bossLever(BlockPos pos) {
        return DEVICE_LEVERS.contains(pos) || TERMINAL_LEVERS.contains(pos) || EXTRA_LEVER.equals(pos);
    }

    private static boolean hasLabel(Minecraft client, BlockPos pos, double radius, String text) {
        return !client.level.getEntitiesOfClass(ArmorStand.class, new AABB(pos).inflate(radius),
            stand -> text.equals(stand.getName().getString())).isEmpty();
    }
}
