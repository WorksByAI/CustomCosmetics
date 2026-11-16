// SPDX-License-Identifier: AGPL-3.0-or-later
// I4 layout and spread aiming adapted from NoammAddons (CC0-1.0). See NOTICE.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.pack.LegacyPackItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

final class AutoI4 {
    private static final List<BlockPos> TARGETS = List.of(
        new BlockPos(68, 130, 50), new BlockPos(66, 130, 50), new BlockPos(64, 130, 50),
        new BlockPos(68, 128, 50), new BlockPos(66, 128, 50), new BlockPos(64, 128, 50),
        new BlockPos(68, 126, 50), new BlockPos(66, 126, 50), new BlockPos(64, 126, 50)
    );
    private static final Identifier BLUE_TERRACOTTA = Identifier.withDefaultNamespace("blue_terracotta");
    private static final double ARROW_SPEED = 3.0, ARROW_DRAG = .99, ARROW_GRAVITY = .05;
    private static final int RETRY_DELAY = 20;
    private static final Set<BlockPos> completed = new HashSet<>();
    private static final AutoI4Actions actions = new AutoI4Actions();
    private static BlockPos lastLit, fired;
    private static int lastShotTick;
    private static boolean finished, engaged;
    private static Turn turn;

    private record Turn(BlockPos target, Vec3 aim, boolean precise, long since, float yaw, float pitch) {}

    private AutoI4() {}

    static void reset(Minecraft client) {
        resetShooting();
        actions.reset(client);
        engaged = false;
    }

    private static void resetShooting() {
        completed.clear();
        lastLit = fired = null;
        lastShotTick = -RETRY_DELAY;
        turn = null;
        finished = false;
    }

    static void message(Minecraft client, String line) {
        if (!GameplayModule.AUTO_I4.enabled() || client.player == null || !GameplayFeatures.isFloorSeven()) return;
        engaged = true;
        boolean onDevice = onDevice(client);
        boolean justCompleted = onDevice && !finished
            && line.startsWith(client.player.getGameProfile().name() + " completed a device!");
        if (justCompleted) {
            finished = true;
            turn = null;
        }
        actions.message(client, line, onDevice, justCompleted);
    }

    static void serverTick(Minecraft client) {
        if (GameplayModule.AUTO_I4.enabled()) actions.serverTick(client);
    }

    static void tick(Minecraft client, AutoLeap leap) {
        if (!GameplayModule.AUTO_I4.enabled()) {
            if (engaged) reset(client);
            return;
        }
        engaged = true;
        if (!onDevice(client)) {
            if (lastLit != null || finished) resetShooting();
            actions.stop(client);
            return;
        }
        var player = client.player;
        boolean precise = ConfigManager.autoI4Precise;
        int rotationTime = ConfigManager.autoI4RotationTime;
        if (client.gameMode == null || !player.isAlive() || player.isSpectator() || Freecam.active()) {
            actions.stop(client);
            turn = null;
            return;
        }

        if (lastLit != null && unlit(client, lastLit)) completed.add(lastLit);
        BlockPos lit = TARGETS.stream().filter(pos -> client.level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)).findFirst().orElse(null);
        if (lit != null) lastLit = lit;
        if (actions.tick(client, leap, finished) || finished || rotationTime == 0
                || ClientUiAccess.screen(client) != null || player.isUsingItem() || !canShoot(player.getMainHandItem(), precise)) {
            turn = null;
            return;
        }
        BlockPos target = lit == null && ConfigManager.autoI4Predict ? prediction(client) : lit;
        if (target == null) {
            turn = null;
            return;
        }

        long now = System.nanoTime();
        if (turn == null || !turn.target().equals(target) || turn.precise() != precise) {
            Vec3 aim = precise ? center(target) : spreadAim(target);
            turn = new Turn(target, aim, precise, now, player.getYRot(), player.getXRot());
        }
        Vec3 eye = player.getEyePosition();
        Vec3 direction = precise ? centerShot(eye.add(0, -.1, 0), turn.aim()) : turn.aim().subtract(eye);
        if (direction == null) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
        double progress = Math.min(1, (now - turn.since()) / (rotationTime * 1_000_000.0));
        float eased = (float) (progress < .5 ? 4 * progress * progress * progress : 1 - Math.pow(2 - 2 * progress, 3) / 2);
        player.setYRot(turn.yaw() + Mth.wrapDegrees(yaw - turn.yaw()) * eased);
        player.setXRot(Mth.lerp(eased, turn.pitch(), pitch));

        if (progress < 1 || lit == null || player.tickCount - lastShotTick < 3
                || lit.equals(fired) && player.tickCount - lastShotTick < RETRY_DELAY) return;
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        fired = lit;
        lastShotTick = player.tickCount;
    }

    static boolean onDevice(Minecraft client) {
        if (client.player == null || client.level == null || !GameplayFeatures.isFloorSeven()) return false;
        var player = client.player;
        return Math.abs(player.getY() - 127) < .5 && player.getX() >= 62 && player.getX() <= 65
            && player.getZ() >= 34 && player.getZ() <= 37;
    }

    private static boolean canShoot(ItemStack stack, boolean precise) {
        if (!(stack.getItem() instanceof BowItem)) return false;
        String id = LegacyPackItems.id(stack);
        if ("TERMINATOR".equals(id)) return true;
        if (!precise) return false;
        if (id.endsWith("_SHORTBOW") || "SPIRIT_BOW".equals(id)) return true;
        var lore = stack.get(DataComponents.LORE);
        return lore != null && lore.lines().stream().anyMatch(line -> line.getString().startsWith("Shortbow:"));
    }

    private static BlockPos prediction(Minecraft client) {
        if (lastLit == null) return null;
        if (turn != null && !completed.contains(turn.target()) && !turn.target().equals(lastLit)) return turn.target();
        return TARGETS.stream().filter(pos -> !completed.contains(pos) && !pos.equals(lastLit) && unlit(client, pos))
            .findFirst().orElse(null);
    }

    private static boolean unlit(Minecraft client, BlockPos target) {
        return BLUE_TERRACOTTA.equals(BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(target).getBlock()));
    }

    private static Vec3 spreadAim(BlockPos target) {
        boolean leftDone = completed.contains(target.west(2));
        boolean rightDone = completed.contains(target.east(2));
        double x = switch (target.getX()) {
            case 68 -> 67.5;
            case 64 -> 65.5;
            default -> leftDone != rightDone ? (leftDone ? 67.5 : 65.5)
                : ThreadLocalRandom.current().nextBoolean() ? 65.5 : 67.5;
        };
        return new Vec3(x, target.getY() + 1, target.getZ());
    }

    static Vec3 center(BlockPos target) {
        return new Vec3(target.getX() + .5, target.getY() + .5, target.getZ());
    }

    /** Solve the low arc using Minecraft's per-tick arrow drag and gravity. */
    static Vec3 centerShot(Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        double travel = 0, drag = 1, drop = 0, fallingSpeed = 0;
        for (int tick = 0; tick < 24; tick++) {
            if (speedError(delta, travel + drag, drop + fallingSpeed) <= 0) {
                double low = 0, high = 1;
                for (int i = 0; i < 16; i++) {
                    double fraction = (low + high) / 2;
                    if (speedError(delta, travel + drag * fraction, drop + fallingSpeed * fraction) > 0) low = fraction;
                    else high = fraction;
                }
                return delta.add(0, drop + fallingSpeed * high, 0).normalize();
            }
            travel += drag;
            drop += fallingSpeed;
            drag *= ARROW_DRAG;
            fallingSpeed = fallingSpeed * ARROW_DRAG + ARROW_GRAVITY;
        }
        return null;
    }

    private static double speedError(Vec3 delta, double travel, double drop) {
        return delta.add(0, drop, 0).lengthSqr() - ARROW_SPEED * ARROW_SPEED * travel * travel;
    }
}
