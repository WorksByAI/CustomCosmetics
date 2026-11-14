// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi's Dojo module by jcnlk and contributors.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.features.party.PartyTracer;
import com.customcosmetics.features.waypoint.Waypoints;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class DojoGames {
    public static final List<String> SWIFTNESS_CURVES = List.of("Linear", "Ease In Quad", "Ease Out Quad", "Ease In Out Quad",
        "Ease In Quint", "Ease Out Quint", "Ease In Out Quint", "Ease In Back", "Ease Out Back",
        "Ease In Sine", "Ease In Out Sine", "Ease In Out Cubic", "Smoother Step");
    private enum Test { FORCE, MASTERY, DISCIPLINE, SWIFTNESS, CONTROL, TENACITY, STAMINA }
    private static final Vec3 CENTRE = new Vec3(-207, 99, -598);
    private static final AABB ARENA = AABB.ofSize(CENTRE, 50, 30, 50);
    private static final GameplayInput input = new GameplayInput();
    private static final Map<Item, Item> SWORDS = Map.of(Items.LEATHER_HELMET, Items.WOODEN_SWORD,
        Items.IRON_HELMET, Items.IRON_SWORD, Items.GOLDEN_HELMET, Items.GOLDEN_SWORD, Items.DIAMOND_HELMET, Items.DIAMOND_SWORD);
    private static final int[] TRACER_COLORS = {0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55};
    private static final Map<BlockPos, Long> targets = new HashMap<>();
    private static final ArrayDeque<BlockPos> platforms = new ArrayDeque<>();
    private static final Map<Integer, Fireball> fireballs = new HashMap<>();
    private static List<AABB> highlights = List.of();
    private static List<BlockPos> arrowTracers = List.of();
    private static BlockPos arrowTarget;
    private static Vec3 platformGoal;
    private static Test active;
    private static float turnFrom, turnTo;
    private static long turnStart;

    private static final class Fireball {
        final ArmorStand stand;
        final Vec3 spawn;
        Vec3 offset, start, end;
        final ArrayList<BlockPos> floor = new ArrayList<>();
        Fireball(ArmorStand stand) { this.stand = stand; spawn = stand.position(); }
    }

    private DojoGames() {}

    static void reset() {
        input.release();
        active = null;
        highlights = List.of();
        arrowTracers = List.of();
        targets.clear();
        platforms.clear();
        fireballs.clear();
        arrowTarget = null;
        platformGoal = null;
        turnStart = 0;
    }

    static void message(Minecraft client, String text) {
        if (!GameplayModule.DOJO.enabled() || client.player == null || !ARENA.contains(client.player.position())
            || !"crimson_isle".equals(Waypoints.currentIslandMode())) return;
        if (text.contains("Your Rank:")) {
            reset();
            return;
        }
        if (!text.contains("OBJECTIVES")) return;
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        for (var test : Test.values()) if (upper.contains(test.name())) {
            reset();
            active = test;
            break;
        }
    }

    static void blockChanging(Minecraft client, BlockPos pos, BlockState state) {
        if ((active != Test.MASTERY && active != Test.SWIFTNESS) || !GameplayModule.DOJO.enabled()
                || !ARENA.contains(Vec3.atCenterOf(pos)) || client.player == null) return;
        if (active == Test.MASTERY) {
            if (state.is(Blocks.WOOL.lime()) && pos.distToCenterSqr(client.player.position()) <= 400)
                targets.put(pos.immutable(), System.currentTimeMillis() + 6500);
            else if (client.level.getBlockState(pos).is(Blocks.WOOL.red())) targets.remove(pos);
        } else if (ConfigManager.dojoSwiftnessAuto && state.is(Blocks.WOOL.lime()) && platforms.size() < 12
                && !pos.equals(BlockPos.containing(CENTRE)) && !platforms.contains(pos))
            platforms.add(pos.immutable());
    }

    static boolean attack(Minecraft client, Entity target) {
        if ((active != Test.FORCE && active != Test.DISCIPLINE) || !GameplayModule.DOJO.enabled()
                || !(target instanceof Zombie zombie) || !ARENA.contains(zombie.position()) || !canMove(client)) return false;
        var helmet = zombie.getItemBySlot(EquipmentSlot.HEAD);
        if (active == Test.FORCE) return ConfigManager.dojoForceBlock && helmet.is(Items.LEATHER_HELMET);
        Item needed = SWORDS.get(helmet.getItem());
        if (needed == null || client.player.getMainHandItem().is(needed)) return false;
        if (ConfigManager.dojoDisciplineSwap) {
            var inventory = client.player.getInventory();
            for (int slot = 0; slot < 9; slot++) if (inventory.getItem(slot).is(needed)) {
                inventory.setSelectedSlot(slot);
                return false;
            }
        }
        return ConfigManager.dojoDisciplineBlock;
    }

    static void tick(Minecraft client) {
        if (active == null) return;
        if (!GameplayModule.DOJO.enabled() || !ARENA.contains(client.player.position())) {
            reset();
            return;
        }
        boolean canMove = canMove(client);
        if (!canMove) {
            input.release();
            arrowTarget = null;
        }
        switch (active) {
            case FORCE, DISCIPLINE -> updateZombies(client);
            case CONTROL -> control(client, canMove);
            case MASTERY -> mastery(client, canMove);
            case SWIFTNESS -> { if (canMove) swiftness(client); }
            case TENACITY -> tenacity(client);
            case STAMINA -> { /* Upstream has no Stamina solver. */ }
        }
    }

    private static boolean canMove(Minecraft client) {
        return client.player != null && client.gameMode != null && client.isWindowActive()
            && ClientUiAccess.screen(client) == null && !Freecam.active() && client.player.isAlive();
    }

    private static void updateZombies(Minecraft client) {
        if (active == Test.FORCE ? !ConfigManager.dojoForceHighlight : !ConfigManager.dojoDisciplineHighlight) {
            highlights = List.of();
            return;
        }
        var boxes = new ArrayList<AABB>();
        Item hand = client.player.getMainHandItem().getItem();
        for (var zombie : client.level.getEntitiesOfClass(Zombie.class, ARENA)) {
            if (!zombie.isAlive()) continue;
            var helmet = zombie.getItemBySlot(EquipmentSlot.HEAD);
            if (active == Test.FORCE ? helmet.is(Items.LEATHER_HELMET) : SWORDS.get(helmet.getItem()) == hand)
                boxes.add(zombie.getBoundingBox());
        }
        highlights = boxes;
    }

    private static void control(Minecraft client, boolean canMove) {
        if (!ConfigManager.dojoControlAim && !ConfigManager.dojoControlCenter && !ConfigManager.dojoControlHighlight) {
            highlights = List.of();
            return;
        }
        var player = client.player;
        WitherSkeleton nearest = null;
        for (var skeleton : client.level.getEntitiesOfClass(WitherSkeleton.class, ARENA)) {
            if (!skeleton.isAlive() || skeleton.getItemBySlot(EquipmentSlot.HEAD).is(Items.REDSTONE_BLOCK)) continue;
            if (nearest == null || skeleton.distanceToSqr(player) < nearest.distanceToSqr(player)) nearest = skeleton;
        }
        Vec3 offset = nearest == null ? Vec3.ZERO : nearest.position().subtract(nearest.oldPosition())
            .multiply(ConfigManager.dojoControlPrediction, 1, ConfigManager.dojoControlPrediction);
        highlights = nearest != null && ConfigManager.dojoControlHighlight ? List.of(nearest.getBoundingBox().move(offset)) : List.of();
        if (!canMove) return;
        boolean center = ConfigManager.dojoControlCenter && player.onGround()
            && player.position().distanceToSqr(CENTRE.add(.5, 1, .5)) > 1.6;
        if (center) player.lookAt(Anchor.EYES, CENTRE.add(.5, 1.8, .5));
        else if (nearest != null && ConfigManager.dojoControlAim) player.lookAt(Anchor.EYES, nearest.getEyePosition().add(offset));
        input.set(client.options.keyUp, center);
    }

    private static void mastery(Minecraft client, boolean canMove) {
        long now = System.currentTimeMillis();
        targets.values().removeIf(expiry -> expiry <= now);
        arrowTracers = ConfigManager.dojoMasteryTracer && !targets.isEmpty() ? targets.entrySet().stream()
            .sorted(Map.Entry.comparingByValue()).limit(Math.clamp(ConfigManager.dojoMasteryAmount, 1, 4)).map(Map.Entry::getKey).toList() : List.of();
        if (!canMove || !ConfigManager.dojoMasteryAuto || ConfigManager.dojoMasterySneakStops && client.player.isShiftKeyDown()) {
            input.set(client.options.keyUse, false);
            arrowTarget = null;
            return;
        }
        if (arrowTarget != null) {
            Long expires = targets.get(arrowTarget);
            if (expires == null || expires - now < ConfigManager.dojoMasteryOffset) {
                input.set(client.options.keyUse, false);
                targets.remove(arrowTarget);
                arrowTarget = null;
            }
            return;
        }
        var inventory = client.player.getInventory();
        if (!inventory.getItem(0).is(Items.BOW) || client.options.keyUse.isDown()) return;
        var first = targets.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue)).orElse(null);
        if (first == null || first.getValue() - now < ConfigManager.dojoMasteryOffset + 1000) return;
        if (inventory.getSelectedSlot() != 0) {
            inventory.setSelectedSlot(0);
            return;
        }
        arrowTarget = first.getKey();
        Vec3 target = Vec3.atCenterOf(arrowTarget);
        double horizontal = target.subtract(client.player.getEyePosition()).horizontalDistance();
        client.player.lookAt(Anchor.EYES, target.add(0, horizontal * horizontal * .003, 0));
        input.set(client.options.keyUse, true);
    }

    private static void swiftness(Minecraft client) {
        var player = client.player;
        if (!ConfigManager.dojoSwiftnessAuto) {
            platforms.clear();
            platformGoal = null;
            input.release();
            return;
        }
        if (platformGoal == null && player.onGround() && !platforms.isEmpty()) {
            Vec3 target = Vec3.atBottomCenterOf(platforms.remove().above());
            platformGoal = target.add(target.subtract(player.position()).normalize().scale(ConfigManager.dojoSwiftnessLead));
            turnFrom = player.getYRot();
            var delta = target.subtract(player.position());
            turnTo = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
            turnStart = player.position().distanceToSqr(target) > ConfigManager.dojoSwiftnessTurnDistance ? System.nanoTime() : 0;
        }
        if (platformGoal == null) return;
        Vec3 delta = platformGoal.subtract(player.position());
        if (player.onGround() && delta.horizontalDistanceSqr() < .16 && Math.abs(delta.y) < 1.2) {
            platformGoal = null;
            input.release();
            if (ConfigManager.dojoSwiftnessStop) {
                Vec3 velocity = player.getDeltaMovement();
                player.setDeltaMovement(0, velocity.y, 0);
            }
            return;
        }
        if (turnStart != 0) {
            float t = ConfigManager.dojoSwiftnessRotation == 0 ? 1
                : Math.min(1f, (System.nanoTime() - turnStart) / (ConfigManager.dojoSwiftnessRotation * 1_000_000f));
            float progress = rotationProgress(t);
            player.setYRot(Mth.rotLerp(progress, turnFrom, turnTo));
            if (t < 1) return;
            turnStart = 0;
        }
        player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
        input.set(client.options.keyUp, true);
        input.set(client.options.keyJump, player.onGround() && delta.horizontalDistanceSqr() > 1);
    }

    private static float rotationProgress(float t) {
        float rest = 1 - t;
        float back = 1.70158f;
        return switch (ConfigManager.dojoSwiftnessCurve) {
            case 1 -> t * t;
            case 2 -> 1 - rest * rest;
            case 3 -> t < .5f ? 2 * t * t : 1 - 2 * rest * rest;
            case 4 -> (float) Math.pow(t, 5);
            case 5 -> 1 - (float) Math.pow(rest, 5);
            case 6 -> t < .5f ? 16 * (float) Math.pow(t, 5) : 1 - 16 * (float) Math.pow(rest, 5);
            case 7 -> (back + 1) * t * t * t - back * t * t;
            case 8 -> 1 - (back + 1) * rest * rest * rest + back * rest * rest;
            case 9 -> 1 - (float) Math.cos(t * Math.PI / 2);
            case 10 -> (1 - (float) Math.cos(t * Math.PI)) / 2;
            case 11 -> t < .5f ? 4 * t * t * t : 1 - 4 * rest * rest * rest;
            case 12 -> t * t * t * (t * (t * 6 - 15) + 10);
            default -> t;
        };
    }

    static void particles(ClientboundLevelParticlesPacket packet) {
        if (active != Test.TENACITY || !ConfigManager.dojoTenacityHighlight || !GameplayModule.DOJO.enabled()
            || packet.getParticle().getType() != ParticleTypes.FLAME) return;
        var pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        Fireball nearest = null;
        double distance = 25;
        for (var fireball : fireballs.values()) {
            double next = fireball.stand.distanceToSqr(pos);
            if (next < distance) { nearest = fireball; distance = next; }
        }
        if (nearest != null && nearest.end == null) nearest.offset = pos.subtract(nearest.stand.position());
    }

    private static void tenacity(Minecraft client) {
        if (!ConfigManager.dojoTenacityHighlight) { fireballs.clear(); return; }
        fireballs.values().removeIf(fireball -> fireball.stand.isRemoved());
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, ARENA)) {
            var fireball = fireballs.computeIfAbsent(stand.getId(), id -> new Fireball(stand));
            if (fireball.end == null && fireball.offset != null && stand.position().distanceToSqr(fireball.spawn) > .001) {
                fireball.start = fireball.spawn.add(fireball.offset);
                Vec3 motion = stand.position().subtract(fireball.spawn);
                var hit = client.level.clip(new ClipContext(fireball.start, fireball.start.add(motion.normalize().scale(60)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, stand));
                fireball.end = hit.getLocation();
                var path = fireball.end.subtract(fireball.start);
                var step = path.normalize().scale(.5);
                var side = new Vec3(-step.z, 0, step.x).normalize().scale(.5);
                var cells = new java.util.LinkedHashSet<BlockPos>();
                int steps = (int) (path.length() * 2);
                for (int i = 0; i < steps; i++) {
                    Vec3 point = fireball.start.add(step.scale(i));
                    if (point.y > 101.5) continue;
                    for (int width = -1; width <= 1; width++)
                        cells.add(BlockPos.containing(point.x + side.x * width, 98, point.z + side.z * width));
                }
                fireball.floor.addAll(cells);
            } else if (fireball.end != null) {
                Vec3 direction = fireball.end.subtract(fireball.start).normalize();
                double passed = stand.position().subtract(fireball.start).dot(direction) - 1.5;
                fireball.floor.removeIf(pos -> Vec3.atCenterOf(pos).subtract(fireball.start).dot(direction) < passed);
            }
        }
    }

    static void render(LevelRenderContext context) {
        if (active == null || !GameplayModule.DOJO.enabled()) return;
        int rgb = active == Test.FORCE ? ConfigManager.dojoForceColor
            : active == Test.DISCIPLINE ? ConfigManager.dojoDisciplineColor : ConfigManager.dojoControlColor;
        for (var box : highlights) {
            drawBox(context, box, rgb);
            if (active == Test.DISCIPLINE && ConfigManager.dojoDisciplineTracer) trace(context, box.getCenter(), 0xFF000000 | rgb);
        }
        for (int i = 0; i < arrowTracers.size(); i++) trace(context, Vec3.atCenterOf(arrowTracers.get(i)), TRACER_COLORS[i]);
        if (active == Test.TENACITY && ConfigManager.dojoTenacityHighlight) for (var fireball : fireballs.values()) {
            if (fireball.end == null) continue;
            int color = ConfigManager.dojoTenacityColor;
            drawBox(context, AABB.ofSize(fireball.end, 3, .1, 3), color);
            for (var cell : fireball.floor) drawBox(context, new AABB(cell), color);
            if (ConfigManager.dojoTenacityLine) {
                var camera = context.levelState().cameraRenderState;
                Vec3 start = fireball.start.subtract(camera.pos), end = fireball.end.subtract(camera.pos);
                if (start.distanceToSqr(end) < .01) continue;
                Vec3 side = end.subtract(start).cross(new Vec3(0, 1, 0)).normalize().scale(.04);
                context.submitNodeCollector().submitCustomGeometry(new PoseStack(), PartyRenderPipeline.type(), (matrix, output) -> {
                    for (var p : List.of(start.add(side), start.subtract(side), end.subtract(side), end.add(side)))
                        output.addVertex(matrix.pose(), (float) p.x, (float) p.y, (float) p.z).setColor(0xFF000000 | color);
                });
            }
        }
    }

    private static void drawBox(LevelRenderContext context, AABB bounds, int color) {
        var p = context.levelState().cameraRenderState.pos;
        var box = bounds.move(-p.x, -p.y, -p.z);
        context.submitNodeCollector().submitCustomGeometry(new PoseStack(), PartyRenderPipeline.type(), (matrix, output) ->
            PartyGeometry.outline(output, matrix.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, .018, 0xFF000000 | color));
    }

    private static void trace(LevelRenderContext context, Vec3 target, int color) {
        var camera = context.levelState().cameraRenderState;
        var forward = new Vector3f(0, 0, -1).rotate(camera.orientation);
        var right = new Vector3f(1, 0, 0).rotate(camera.orientation);
        var vertices = PartyTracer.ribbon(new Vec3(forward.x, forward.y, forward.z), new Vec3(right.x, right.y, right.z),
            target.subtract(camera.pos), 32, 1);
        if (!vertices.isEmpty()) context.submitNodeCollector().submitCustomGeometry(new PoseStack(), PartyRenderPipeline.type(), (matrix, output) -> {
            for (var p : vertices) output.addVertex(matrix.pose(), (float) p.x, (float) p.y, (float) p.z).setColor(color);
        });
    }
}
