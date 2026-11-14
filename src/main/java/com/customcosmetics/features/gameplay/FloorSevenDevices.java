// SPDX-License-Identifier: GPL-3.0-only
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.compatibility.SwingAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.utils.MessageUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;

/** F7/M7 target selection and device layouts adapted from Quoi. */
public final class FloorSevenDevices {
    private static final Vec3[] CRYSTALS = {new Vec3(64.5, 238.375, 50.5), new Vec3(82.5, 238.375, 50.5)};
    private static final BlockPos SIMON_START = new BlockPos(110, 121, 91);
    private static final AABB SIMON_LABELS = new AABB(108, 120, 90, 115, 125, 95);
    private static final AABB ARROW_FRAMES = new AABB(-3, 119, 74, 0, 126, 81);
    // Column-major frame positions, from ArrowAlign.kt. A dot marks an empty slot.
    private static final String[] ARROW_LAYOUTS = {
        "7777." + "1...." + "13333" + "....1" + ".7771",
        "....." + "1.1.1" + "1.1.1" + "1.1.1" + ".....",
        "5333." + "5...." + "77..." + "1...." + "1333.",
        "....." + ".1.1." + "71713" + "1.1.1" + ".....",
        "..775" + ".71.5" + "....." + ".75.1" + "..771",
        "77..." + "1...." + "13333" + "....1" + "...71",
        "53333" + "5...1" + "77..1" + "....1" + ".7771",
        "77..." + "1...." + "13.75" + "....5" + "...33",
        "....." + "13333" + "....1" + "77771" + "....."
    };
    private static final ItemFrame[] arrows = new ItemFrame[25];
    private static final ArrayList<BlockPos> simonSequence = new ArrayList<>(16);
    private static int simonProgress, simonStarts, nextSimonClick;
    private static long simonStartedAt;
    private static SimonTurn simonTurn;
    private static boolean simonStarted, simonDiscardedInitial;
    private static int nextInteraction;

    private record SimonTurn(BlockPos pos, long started, long duration, float yaw, float pitch, float dyaw, float dpitch, int mode) {}

    private FloorSevenDevices() {}

    static void reset() {
        resetSimon();
        Arrays.fill(arrows, null);
        nextInteraction = 0;
    }

    private static void resetSimon() {
        simonSequence.clear();
        simonProgress = 0;
        simonStarts = nextSimonClick = 0;
        simonStartedAt = 0;
        simonTurn = null;
        simonStarted = false;
        simonDiscardedInitial = false;
    }

    static void tick(Minecraft client) {
        boolean crystals = GameplayModule.CRYSTAL_PICKUP.enabled();
        boolean relics = GameplayModule.RELICS.enabled();
        boolean terminals = GameplayModule.OPEN_TERMINALS.enabled();
        boolean simon = GameplayModule.SIMON_SAYS.enabled();
        boolean align = GameplayModule.ARROW_ALIGN.enabled();
        if (!simon && simonStarted) resetSimon();
        if (!(crystals || relics || terminals || simon || align) || client.gameMode == null ||
            client.player.isSpectator() || !client.player.isAlive()
            || client.player.tickCount < nextInteraction || Freecam.active() || !GameplayFeatures.isFloorSeven()) return;

        var player = client.player;
        var eye = player.getEyePosition();
        double y = player.getY();
        int delay = 0;
        boolean inMenu = ClientUiAccess.screen(client) != null;
        if (crystals && (!inMenu || ConfigManager.crystalInMenus) && y > 220 && y < 249 && pickupCrystal(client, eye))
            delay = ConfigManager.crystalDelay;
        else if (inMenu) return;
        else if (relics && y >= 0 && y < 32 && MapEditor.floor().equals("M7") && relic(client, eye))
            delay = Math.ceilDiv(ConfigManager.relicDelayMs, 50);
        else if (y > 100 && y <= 155) {
            if (simon && player.distanceToSqr(Vec3.atCenterOf(SIMON_START)) <= 25 && simon(client))
                delay = 1;
            else if (align && player.distanceToSqr(.5, 120.5, 77.5) <= 100 && align(client, eye))
                delay = Math.ceilDiv(ConfigManager.arrowDelayMs, 50);
            else if (terminals && !GameplayFeatures.terminalDelayedByLeap() && player.getX() >= -6 && player.getX() <= 113 && player.getZ() >= 27 && player.getZ() <= 145
                && (!ConfigManager.terminalGroundOnly || player.onGround()) && terminal(client, eye))
                delay = Math.max(1, Math.ceilDiv(ConfigManager.terminalDelayMs, 50));
        }
        if (delay > 0) nextInteraction = player.tickCount + delay;
    }

    private static boolean pickupCrystal(Minecraft client, Vec3 eye) {
        if (client.player.getInventory().getItem(8).getHoverName().getString().equals("Energy Crystal")) return false;
        for (var pos : CRYSTALS) {
            if (eye.distanceToSqr(pos) > 36) continue;
            for (var crystal : client.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos, pos).inflate(.1))) {
                if (crystal.position().distanceToSqr(pos) < .03 && (!ConfigManager.crystalLookOnly || aimedAt(client, crystal))
                    && interact(client, eye, crystal, ConfigManager.crystalRange)) return true;
            }
        }
        return false;
    }

    private static boolean terminal(Minecraft client, Vec3 eye) {
        double range = ConfigManager.terminalRange;
        if (ConfigManager.terminalLookOnly) {
            return client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof ArmorStand stand && inactiveTerminal(stand, eye)
                && interact(client, eye, stand, range);
        }
        int fov = ConfigManager.terminalAuraFov;
        var look = fov == 360 ? null : client.player.getViewVector(1);
        double threshold = Math.cos(Math.toRadians(fov / 2.0));
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(range))) {
            if (!inactiveTerminal(stand, eye)) continue;
            if (look != null) {
                var to = stand.getBoundingBox().getCenter().subtract(eye);
                if (to.dot(look) < to.length() * threshold) continue;
            }
            if (interact(client, eye, stand, range)) return true;
        }
        return false;
    }

    private static boolean inactiveTerminal(ArmorStand stand, Vec3 eye) {
        return stand.isAlive() && eye.y >= stand.getY() && stand.getName().getString().contains("Inactive Terminal");
    }

    private static boolean relic(Minecraft client, Vec3 eye) {
        var inventory = client.player.getInventory();
        double range = ConfigManager.relicRange;
        var carried = Relic.fromName(inventory.getItem(8).getHoverName().getString());
        if (carried != null) {
            if (!ConfigManager.relicPlacement || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                || !carried.matches(hit.getBlockPos()) || eye.distanceToSqr(hit.getLocation()) > range * range) return false;
            inventory.setSelectedSlot(8);
            useBlock(client, hit);
            return true;
        }
        if (!ConfigManager.relicPickup) return false;
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(range))) {
            var relic = Relic.fromName(stand.getItemBySlot(EquipmentSlot.HEAD).getHoverName().getString());
            if (relic != null && !relic.placed(stand.position()) && interact(client, eye, stand, range)) return true;
        }
        return false;
    }

    private static boolean aimedAt(Minecraft client, Entity entity) {
        return client.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity;
    }

    private static boolean interact(Minecraft client, Vec3 eye, Entity entity, double reach) {
        if (!entity.isAlive() || entity.isRemoved()) return false;
        AABB box = entity.getBoundingBox().inflate(.05);
        Vec3 target = box.clip(eye, box.getCenter()).orElse(box.getCenter());
        if (eye.distanceToSqr(target) > reach * reach) return false;
        client.gameMode.interact(client.player, entity, new EntityHitResult(entity, target), InteractionHand.MAIN_HAND);
        SwingAccess.swingMainHand(client.player);
        return true;
    }

    private static void useBlock(Minecraft client, BlockHitResult hit) {
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        SwingAccess.swingMainHand(client.player);
    }

    private static boolean simon(Minecraft client) {
        int tick = client.player.tickCount;
        if (tick % 4 == 0) {
            for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, SIMON_LABELS)) {
                if (stand.getName().getString().contains("Device Active")) {
                    if (simonStarted && simonStartedAt != 0) {
                        double seconds = (System.nanoTime() - simonStartedAt) / 1_000_000_000.0;
                        String message = String.format(java.util.Locale.ROOT, "Simon Says took %.2fs", seconds);
                        MessageUtils.sendMessage(message);
                        if (ConfigManager.simonAnnounce && client.getConnection() != null) client.getConnection().sendCommand("pc " + message);
                    }
                    resetSimon();
                    return false;
                }
            }
        }
        if (!ConfigManager.simonAuto) { simonTurn = null; return false; }
        if (tick < nextSimonClick) return false;
        if (!simonStarted) {
            if (!(client.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(SIMON_START)
                || !client.level.getBlockState(SIMON_START).is(Blocks.STONE_BUTTON)) return false;
            simonStarted = true;
            simonStartedAt = System.nanoTime();
        }
        if (simonStarts < ConfigManager.simonStartClicks) {
            if (!client.level.getBlockState(SIMON_START).is(Blocks.STONE_BUTTON)) return false;
            if (simonStarts < ConfigManager.simonStartClicks - 1) {
                simonSequence.clear();
                simonProgress = 0;
                simonDiscardedInitial = false;
            }
            if (client.hitResult instanceof BlockHitResult hit && SIMON_START.equals(hit.getBlockPos())) useBlock(client, hit);
            else useBlock(client, new BlockHitResult(Vec3.atCenterOf(SIMON_START).add(.4375, 0, 0), Direction.WEST, SIMON_START, false));
            simonStarts++;
            nextSimonClick = tick + Math.ceilDiv(ConfigManager.simonStartDelay, 50);
            if (simonStarts == ConfigManager.simonStartClicks) simonStartedAt = System.nanoTime();
            return true;
        }
        if (!simonDiscardedInitial && client.level.getBlockState(new BlockPos(110, 123, 92)).is(Blocks.STONE_BUTTON)) {
            if (simonSequence.size() == 3) simonSequence.removeFirst();
            simonDiscardedInitial = true;
        }
        if (!simonDiscardedInitial || simonProgress >= simonSequence.size()) return false;
        var pos = simonSequence.get(simonProgress);
        var state = client.level.getBlockState(pos);
        if (!state.is(Blocks.STONE_BUTTON) || state.getValue(BlockStateProperties.POWERED)) return false;
        var point = Vec3.atCenterOf(pos).add(.4375, 0, 0);
        if (client.player.getEyePosition().distanceToSqr(point) > 20.25) return false;
        BlockHitResult hit = new BlockHitResult(point, Direction.WEST, pos, false);
        if (ConfigManager.simonRotation != 0) {
            if (!turnForSimon(client, pos, point)) return false;
            if (!(client.player.pick(5, 1, false) instanceof BlockHitResult aimed) || !aimed.getBlockPos().equals(pos)
                    || aimed.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
            hit = aimed;
        }
        useBlock(client, hit);
        simonTurn = null;
        simonProgress++;
        nextSimonClick = tick + Math.ceilDiv(ConfigManager.simonDelayMs, 50);
        return true;
    }

    private static boolean turnForSimon(Minecraft client, BlockPos pos, Vec3 point) {
        long now = System.nanoTime();
        var player = client.player;
        int mode = ConfigManager.simonRotation;
        if (simonTurn == null || !simonTurn.pos().equals(pos) || simonTurn.mode() != mode) {
            var delta = point.subtract(player.getEyePosition());
            float yaw = player.getYRot(), pitch = player.getXRot();
            float dyaw = net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90 - yaw);
            float dpitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())) - pitch;
            double factor = Math.min(1.75, .8 + .2 * Math.sqrt(Math.hypot(dyaw, dpitch) / 30));
            if (mode == 1) factor *= java.util.concurrent.ThreadLocalRandom.current().nextDouble(.85, 1.16);
            simonTurn = new SimonTurn(pos, now, (long) (ConfigManager.simonRotationTime * factor * 1_000_000), yaw, pitch, dyaw, dpitch, mode);
        }
        var turn = simonTurn;
        double progress = Math.min(1, (now - turn.started()) / (double) turn.duration());
        double amount = mode == 1 ? progress * progress * (3 - 2 * progress)
            : progress * progress * progress * (progress * (6 * progress - 15) + 10);
        player.setYRot((float) (turn.yaw() + turn.dyaw() * amount));
        player.setXRot((float) (turn.pitch() + turn.dpitch() * amount));
        return progress >= 1;
    }

    public static void manualSimonStart(Minecraft client, BlockPos pos) {
        if (ConfigManager.simonAuto || !GameplayModule.SIMON_SAYS.enabled() || !SIMON_START.equals(pos)
                || !GameplayFeatures.isFloorSeven() || !PresenceWorld.isHypixel(client)) return;
        resetSimon();
        simonStarted = true;
        simonStartedAt = System.nanoTime();
    }

    public static void blockChanged(BlockPos pos, BlockState state) {
        if (!simonStarted || !GameplayModule.SIMON_SAYS.enabled() || pos.getY() < 120 || pos.getY() > 123
                || pos.getZ() < 92 || pos.getZ() > 95) return;
        if (pos.getX() == 110 && state.is(Blocks.STONE_BUTTON) && state.getValue(BlockStateProperties.POWERED)) {
            int index = simonSequence.indexOf(pos);
            if (index >= 0) simonProgress = Math.max(simonProgress, index + 1);
            return;
        }
        if (pos.getX() != 111 || !state.is(Blocks.SEA_LANTERN)) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.player.distanceToSqr(Vec3.atCenterOf(SIMON_START)) > 25
            || !PresenceWorld.isHypixel(client)) return;
        var button = pos.west();
        if (!simonSequence.isEmpty() && simonSequence.getFirst().equals(button)) {
            simonProgress = 0;
            if (simonSequence.size() == 2 && !simonDiscardedInitial) {
                simonDiscardedInitial = true;
                simonSequence.removeFirst();
            }
        }
        if (!simonSequence.contains(button)) {
            simonProgress = 0;
            simonSequence.add(button);
        }
    }

    static void renderSimon(LevelRenderContext context) {
        if (!simonStarted || !ConfigManager.simonSolver || !GameplayModule.SIMON_SAYS.enabled() || simonProgress >= simonSequence.size()) return;
        var camera = context.levelState().cameraRenderState.pos;
        if (SIMON_START.distToCenterSqr(camera) > 1600) return;
        var pose = new PoseStack();
        int alpha = (int) (ConfigManager.simonHintOpacity * 255) << 24;
        for (int i = simonProgress; i < simonSequence.size(); i++) {
            int color = alpha | switch (i - simonProgress) {
                case 0 -> ConfigManager.simonFirstColor;
                case 1 -> ConfigManager.simonSecondColor;
                default -> ConfigManager.simonThirdColor;
            };
            var pos = simonSequence.get(i);
            double x = pos.getX() - camera.x, y = pos.getY() - camera.y, z = pos.getZ() - camera.z;
            context.submitNodeCollector().submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrices, output) ->
                PartyGeometry.box(output, matrices.pose(), x + .875, y + .375, z + .3125, x + 1, y + .625, z + .6875, color));
        }
    }

    private static boolean align(Minecraft client, Vec3 eye) {
        if (ConfigManager.arrowSneakPause && client.player.isShiftKeyDown()) return false;
        Arrays.fill(arrows, null);
        for (ItemFrame frame : client.level.getEntitiesOfClass(ItemFrame.class, ARROW_FRAMES)) {
            var pos = frame.blockPosition();
            int row = pos.getY() - 120, column = pos.getZ() - 75;
            if (pos.getX() == -2 && row >= 0 && row < 5 && column >= 0 && column < 5 && frame.getItem().is(Items.ARROW))
                arrows[column * 5 + row] = frame;
        }
        for (String layout : ARROW_LAYOUTS) {
            if (!matchesBoard(layout)) continue;
            for (int i = 0; i < arrows.length; i++) {
                var frame = arrows[i];
                if (frame != null && frame.getRotation() != layout.charAt(i) - '0'
                    && (!ConfigManager.arrowLookOnly || aimedAt(client, frame))
                    && interact(client, eye, frame, ConfigManager.arrowRange)) return true;
            }
            break;
        }
        return false;
    }

    private static boolean matchesBoard(String layout) {
        for (int i = 0; i < arrows.length; i++) {
            if ((arrows[i] == null) != (layout.charAt(i) == '.')) return false;
        }
        return true;
    }

    private enum Relic {
        RED("Red", 51, 42), ORANGE("Orange", 57, 42), GREEN("Green", 49, 44),
        BLUE("Blue", 59, 44), PURPLE("Purple", 54, 41);

        private static final Relic[] ALL = values();
        private final String name;
        private final BlockPos cauldron, support;

        Relic(String color, int x, int z) {
            name = "Corrupted " + color + " Relic";
            cauldron = new BlockPos(x, 7, z);
            support = cauldron.below();
        }

        boolean matches(BlockPos pos) { return pos.equals(cauldron) || pos.equals(support); }

        boolean placed(Vec3 pos) {
            for (Relic relic : ALL) {
                double x = pos.x - relic.cauldron.getX() - 1, z = pos.z - relic.cauldron.getZ() - 1;
                if (x * x + z * z < 2.25) return true;
            }
            return false;
        }

        static Relic fromName(String name) {
            for (Relic relic : ALL) if (relic.name.equalsIgnoreCase(name)) return relic;
            return null;
        }
    }
}
