// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi SecretAura and its block interaction helper by jcnlk and contributors.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.SwingAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.DungeonRooms;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.map.RoomLayouts;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class SecretAura {
    private static final Identifier BLUE_CLAY = Identifier.withDefaultNamespace("blue_terracotta");
    private static final Long2LongOpenHashMap readyAt = new Long2LongOpenHashMap();
    private static final LongOpenHashSet collected = new LongOpenHashSet();
    private static BlockPos lastPos;
    private static int previousSlot = -1, swappedSlot = -1, nextScan;
    private static long retryAt;
    private static int nextRoomScan;
    private static int nextAnchorScan;
    private static RoomLayouts.Frame roomWithAnchor;
    private static Anchor anchor;
    private static boolean redstoneKey;

    private record Anchor(BlockPos position, int turn) {
        BlockPos world(int x, int y, int z) {
            return switch (turn) {
                case 0 -> position.offset(x, 0, z).atY(y);
                case 1 -> position.offset(-z, 0, x).atY(y);
                case 2 -> position.offset(-x, 0, -z).atY(y);
                default -> position.offset(z, 0, -x).atY(y);
            };
        }
    }

    private SecretAura() {}

    static void reset(Minecraft client) {
        restoreSlot(client);
        readyAt.clear();
        collected.clear();
        lastPos = null;
        nextScan = 0;
        nextRoomScan = 0;
        nextAnchorScan = 0;
        roomWithAnchor = null;
        anchor = null;
        redstoneKey = false;
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.SECRET_AURA.enabled() || !MapEditor.automaticDungeon(client)) {
            if (lastPos != null || !readyAt.isEmpty() || !collected.isEmpty() || previousSlot != -1) reset(client);
            return;
        }
        var player = client.player;
        var screen = ClientUiAccess.screen(client);
        if (client.gameMode == null || player.isSpectator() || !player.isAlive() || Freecam.active()
                || screen != null && !(ConfigManager.secretAuraInContainer && screen instanceof AbstractContainerScreen<?>)) {
            restoreSlot(client);
            return;
        }
        boolean boss = SecretTriggerbot.inF7Boss(client);
        if (boss && !ConfigManager.secretAuraInBoss) { restoreSlot(client); return; }
        if (player.tickCount < nextScan) return;
        nextScan = player.tickCount + 2;
        long now = System.currentTimeMillis();
        if (!readyAt.isEmpty() && player.tickCount % 20 <= 1) readyAt.values().removeIf(time -> now - time > 2000);
        if (lastPos != null && client.level.getBlockEntity(lastPos) instanceof ChestBlockEntity chest && chest.getOpenNess(0) > 0)
            collected.add(lastPos.asLong());

        var room = MapEditor.currentRoom();
        if (!boss && room == null && player.tickCount >= nextRoomScan) {
            nextRoomScan = player.tickCount + 5;
            DungeonRooms.scanStep();
            room = MapEditor.currentRoom();
        }
        String roomName = room == null ? "" : room.label();
        if (!boss && !eligibleRoom(client, room)) { restoreSlot(client); return; }
        boolean waterBoard = roomName.equals("Water Board");
        BlockPos blazeChest = switch (roomName) {
            case "Lower Blaze" -> anchor == null ? null : anchor.world(15, 20, 15);
            case "Higher Blaze" -> anchor == null ? null : anchor.world(15, 119, 15);
            default -> null;
        };
        boolean blazeLocked = blazeChest != null && !client.level.getBlockState(blazeChest).is(Blocks.CHEST);
        var eye = player.getEyePosition();
        double range = boss ? ConfigManager.secretAuraBossRange : ConfigManager.secretAuraRange;
        double maxDistanceSq = range * range;
        double skullDistanceSq = ConfigManager.secretAuraSkullRange * ConfigManager.secretAuraSkullRange;
        double fovDot = Math.cos(Math.toRadians(ConfigManager.secretAuraFov / 2.0));
        var look = player.getViewVector(1);
        BlockHitResult best = null;
        var min = BlockPos.containing(eye.x - range, eye.y - range, eye.z - range);
        var max = BlockPos.containing(eye.x + range, eye.y + range, eye.z + range);
        double closestSq = maxDistanceSq;
        for (var pos : BlockPos.betweenClosed(min, max)) {
            long key = pos.asLong();
            if (collected.contains(key) || recentlyClicked(pos)) continue;
            double distanceSq = eye.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            if (distanceSq > closestSq || !client.level.hasChunkAt(pos)) continue;
            var state = client.level.getBlockState(pos);
            boolean skull = state.getBlock() instanceof SkullBlock;
            if (skull && distanceSq > skullDistanceSq) continue;
            if (!SecretTriggerbot.isSecret(client, pos, boss) && !(redstoneKey && !boss && state.is(Blocks.REDSTONE_BLOCK))) continue;
            if (waterBoard && state.is(Blocks.LEVER) || blazeLocked && blazeChest.getX() == pos.getX()) continue;
            var center = Vec3.atCenterOf(pos);
            if (ConfigManager.secretAuraFov < 360 && center.subtract(eye).dot(look) < Math.sqrt(distanceSq) * fovDot) continue;
            long ready = readyAt.get(key);
            if (ready == 0) {
                readyAt.put(key, now + ConfigManager.secretAuraDelay);
                continue;
            }
            if (ready > now) continue;
            var shape = state.getShape(client.level, pos);
            if (shape.isEmpty()) continue;
            var middle = shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
            var hit = shape.clip(eye, middle.add(middle.subtract(eye).normalize().scale(1.5)), pos);
            if (hit != null) {
                best = new BlockHitResult(hit.getLocation(), hit.getDirection(), pos.immutable(), hit.isInside());
                closestSq = distanceSq;
            }
        }
        if (best == null) { restoreSlot(client); return; }
        var pos = best.getBlockPos();
        var state = client.level.getBlockState(pos);
        int swap = ConfigManager.secretAuraSwap;
        if (swap == 2 || swap == 1 && state.getBlock() instanceof SkullBlock) {
            var inventory = player.getInventory();
            int slot = ConfigManager.secretAuraSlot - 1;
            if (inventory.getSelectedSlot() != slot) {
                if (ConfigManager.secretAuraSwapBack) {
                    previousSlot = inventory.getSelectedSlot();
                    swappedSlot = slot;
                }
                inventory.setSelectedSlot(slot);
                return;
            }
        } else restoreSlot(client);
        lastPos = pos;
        retryAt = now + 500;
        readyAt.put(pos.asLong(), retryAt);
        client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, best);
        if (ConfigManager.secretAuraSwing && !player.isShiftKeyDown()) SwingAccess.swingMainHand(player);
    }

    private static boolean eligibleRoom(Minecraft client, RoomLayouts.Frame room) {
        if (room == null || room.key().startsWith("floor:")) return false;
        String name = room.label();
        if (name.equals("Three Weirdos")) return false;
        switch (name) {
            case "Ice Path", "Ice Fill", "Water Board", "Teleport Maze", "Lower Blaze", "Higher Blaze" -> {
                if (!room.equals(roomWithAnchor) || anchor == null && client.player.tickCount >= nextAnchorScan) {
                    anchor = findAnchor(client, room);
                    roomWithAnchor = room;
                    nextAnchorScan = client.player.tickCount + 10;
                }
                if (anchor == null) return false;
            }
            default -> { return true; }
        }
        return switch (room.label()) {
            case "Ice Path" -> at(client, 15, 68, 25).isAir();
            case "Ice Fill" -> at(client, 15, 71, 26).is(Blocks.PACKED_ICE);
            case "Water Board" -> {
                boolean clear = true;
                for (int z = 15; z <= 19; z++) if (!at(client, 15, 57, z).isAir()) { clear = false; break; }
                yield clear;
            }
            case "Teleport Maze" -> new AABB(Vec3.atLowerCornerOf(anchor.world(12, 68, 14)), Vec3.atLowerCornerOf(anchor.world(18, 70, 20)))
                .intersects(new AABB(client.player.blockPosition()));
            default -> true;
        };
    }

    private static Anchor findAnchor(Minecraft client, RoomLayouts.Frame room) {
        var origin = MapEditor.blockPos(room.origin());
        if (!client.level.hasChunkAt(origin)) return null;
        var cursor = new BlockPos.MutableBlockPos();
        for (int y = 160; y >= 12; y--) {
            var roof = client.level.getBlockState(cursor.set(origin.getX(), y, origin.getZ()));
            if (roof.isAir()) continue;
            int top = roof.is(Blocks.GOLD_BLOCK) ? y - 1 : y;
            for (int turn = 0; turn < 4; turn++) {
                int dx = turn == 1 || turn == 2 ? 15 : -15;
                int dz = turn >= 2 ? 15 : -15;
                var corner = origin.offset(dx, 0, dz).atY(top);
                if (client.level.hasChunkAt(corner)
                        && BLUE_CLAY.equals(BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(corner).getBlock()))) return new Anchor(corner, turn);
            }
            return null;
        }
        return null;
    }

    private static BlockState at(Minecraft client, int x, int y, int z) {
        return client.level.getBlockState(anchor.world(x, y, z));
    }

    static boolean recentlyClicked(BlockPos pos) { return pos.equals(lastPos) && System.currentTimeMillis() < retryAt; }

    static void triggerbotClicked(BlockPos pos) {
        if (!GameplayModule.SECRET_AURA.enabled()) return;
        lastPos = pos;
        retryAt = System.currentTimeMillis() + 500;
    }

    public static void blockChanging(BlockPos pos, BlockState next) {
        if (!GameplayModule.SECRET_AURA.enabled() || !readyAt.containsKey(pos.asLong())) return;
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        var current = client.level.getBlockState(pos);
        if (current.is(Blocks.LEVER) && !SecretTriggerbot.bossLever(pos) && !current.equals(next)
                || current.is(Blocks.REDSTONE_BLOCK) && !next.is(Blocks.REDSTONE_BLOCK)) collected.add(pos.asLong());
        if (current.getBlock() instanceof SkullBlock && next.isAir()) {
            if (client.level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity skull
                    && skull.getOwnerProfile() != null && SecretTriggerbot.REDSTONE_KEY.equals(skull.getOwnerProfile().partialProfile().id()))
                redstoneKey = true;
            collected.add(pos.asLong());
        }
    }

    static void equipmentUpdated(int id) {
        if (!GameplayModule.SECRET_AURA.enabled() || readyAt.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(id) instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) return;
        var profile = stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).get(net.minecraft.core.component.DataComponents.PROFILE);
        if (profile != null && SecretTriggerbot.WITHER_ESSENCE.equals(profile.partialProfile().id()))
            collected.add(stand.blockPosition().above(2).asLong());
    }

    private static void restoreSlot(Minecraft client) {
        if (previousSlot >= 0 && client.player != null && client.player.getInventory().getSelectedSlot() == swappedSlot)
            client.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = swappedSlot = -1;
    }
}
