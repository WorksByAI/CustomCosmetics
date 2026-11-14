// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi's Mirrorverse solvers by jcnlk and contributors.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.features.waypoint.Waypoints;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class Mirrorverse {
    private static final GameplayInput input = new GameplayInput();
    private static final AABB CRAFT_ROOM = new AABB(-117, 51, -128, -108, 58, -106);
    private static final AABB DANCE_ROOM = new AABB(-270, 39, -114, -256, 48, -101);
    private static final float COMPLETE_PITCH = .7460317611694336f;
    private static final float[] BEAT_PITCHES = {.523809552192688f, 1.047619104385376f, .6984127163887024f, .8888888955116272f};
    private static final int SNEAK = 1, JUMP = 2, PUNCH = 4;
    private static final int[] DANCE = {0, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0,
        3, 2, 1, 0, 3, 2, 1, 0, 3, 2, 1, 0, 3, 2, 1, 0, 3, 2, 1, 0,
        7, 6, 5, 4, 7, 6, 5, 4, 7, 6, 5, 4, 7, 6, 5, 4};
    private static final double[] SEGMENTS = {-105, -262, -107, -264};
    private enum Route {
        MAZE(MirrorverseRoutes.MAZE), PARKOUR(MirrorverseRoutes.PARKOUR), TUBULATOR(MirrorverseRoutes.TUBULATOR);
        final List<Vec3> points;
        Route(List<Vec3> points) { this.points = points; }
        boolean enabled() {
            return switch (this) {
                case MAZE -> ConfigManager.mirrorverseLavaMaze;
                case PARKOUR -> ConfigManager.mirrorverseLavaParkour;
                case TUBULATOR -> ConfigManager.mirrorverseTubulator;
            };
        }
        Vec3 target(int index) {
            Vec3 point = points.get(index);
            return this == PARKOUR ? point : point.add(.5, 0, .5);
        }
    }

    private static Route route;
    private static Object starting;
    private static MirrorverseRoutes.Room room;
    private static boolean inRift, crafting, dancing, interactAimed, awaitingSound;
    private static KeyMapping danceDirection;
    private static int dwell, step, waiting, timeout, beats, danceStep, sneakAt, jumpAt, punchAt;
    private static List<Guide> craftGuides = List.of();
    private record Guide(AABB box, int color) {}
    private Mirrorverse() {}

    static void reset() {
        inRift = crafting = false;
        craftGuides = List.of();
        stop();
    }

    private static void stop() {
        input.release();
        route = null;
        room = null;
        starting = null;
        dancing = interactAimed = awaitingSound = false;
        danceDirection = null;
        dwell = step = waiting = timeout = beats = danceStep = 0;
        sneakAt = jumpAt = punchAt = -1;
    }

    static void message(String text) {
        if (route != null && (text.equals("EEK! THE LAVA OOFED YOU!")
                || text.equals("OH NO! THE LAVA OOFED YOU BACK TO THE START!"))) stop();
    }

    static void subtitle(Minecraft client, String text) {
        if (!inRift || !GameplayModule.MIRRORVERSE.enabled() || !ConfigManager.mirrorverseTinyDancer
            || !ready(client) || dancing || !DANCE_ROOM.contains(client.player.position()) || !text.contains("Move!")) return;
        stop();
        dancing = true;
        client.player.setYRot(90);
        client.player.setXRot(90);
    }

    static void sound(Minecraft client, ClientboundSoundPacket packet) {
        if (!inRift || !GameplayModule.MIRRORVERSE.enabled() || !ready(client)) return;
        var sound = packet.getSound().value();
        if (room != null && awaitingSound && sound.equals(SoundEvents.GENERIC_EXPLODE.value()) && packet.getPitch() == 1.7936507f) {
            awaitingSound = false;
            step++;
            timeout = 0;
        }
        if (!dancing) return;
        if (sound.equals(SoundEvents.PLAYER_BURP) || sound.equals(SoundEvents.NOTE_BLOCK_BASS.value()) && packet.getPitch() == COMPLETE_PITCH) {
            stop();
            return;
        }
        if (packet.getVolume() != 1 || !sound.equals(SoundEvents.NOTE_BLOCK_BASS.value())) return;
        for (float pitch : BEAT_PITCHES) if (pitch == packet.getPitch()) {
            if (++beats % 2 == 1 && danceDirection == null) {
                danceDirection = switch (danceStep % 4) {
                    case 0 -> client.options.keyLeft;
                    case 1 -> client.options.keyDown;
                    case 2 -> client.options.keyRight;
                    default -> client.options.keyUp;
                };
                input.set(danceDirection, true);
            }
            return;
        }
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.MIRRORVERSE.enabled() || !"rift".equals(Waypoints.currentIslandMode())) {
            if (inRift) reset();
            return;
        }
        inRift = true;
        crafting = CRAFT_ROOM.contains(client.player.position());
        if (crafting && ConfigManager.mirrorverseShowMobs) {
            if (client.player.tickCount % 3 == 0) updateCraftGuides(client);
        } else if (!craftGuides.isEmpty()) craftGuides = List.of();
        if (!ready(client)) {
            if (starting != null || dancing || route != null || room != null) stop();
            return;
        }
        if (dancing) {
            if (ConfigManager.mirrorverseTinyDancer && DANCE_ROOM.contains(client.player.position())) dance(client);
            else stop();
        } else if (room != null) {
            if (ConfigManager.mirrorverseRedGreen && ++timeout <= 200) redGreen(client);
            else stop();
        } else if (route != null) {
            if (route.enabled() && ++timeout <= 100) followRoute(client);
            else stop();
        } else findStart(client);
    }

    private static boolean ready(Minecraft client) {
        return client.player != null && client.level != null && client.gameMode != null && client.isWindowActive()
            && ClientUiAccess.screen(client) == null && !Freecam.active() && client.player.isAlive();
    }

    private static void findStart(Minecraft client) {
        var player = client.player;
        Vec3 pos = player.position();
        Object found = null;
        if (player.onGround()) {
            for (var candidate : Route.values()) {
                if (!candidate.enabled()) continue;
                var start = candidate.target(0);
                double y = candidate == Route.TUBULATOR ? start.y - 1 : start.y;
                if (horizontal(pos, start) < 1 && Math.abs(pos.y - y) <= 1) {
                    found = candidate;
                    break;
                }
            }
            if (found == null && ConfigManager.mirrorverseRedGreen) {
                for (var candidate : MirrorverseRoutes.RED_GREEN) if (player.blockPosition().equals(candidate.start())) {
                    found = candidate;
                    break;
                }
            }
        }
        if (found != starting) dwell = 0;
        starting = found;
        if (found == null || ++dwell < 5) return;
        step = 0;
        if (found instanceof Route selected) {
            route = selected;
            step = 1;
        } else room = (MirrorverseRoutes.Room) found;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double x = a.x - b.x, z = a.z - b.z;
        return x * x + z * z;
    }

    private static void face(Minecraft client, Vec3 target) {
        var player = client.player;
        float pitch = player.getXRot();
        player.lookAt(Anchor.EYES, target);
        player.setXRot(pitch);
    }

    private static void followRoute(Minecraft client) {
        var player = client.player;
        Vec3 from = route.target(step - 1), target = route.target(step);
        if (player.getY() < from.y - 2 || horizontal(player.position(), from) > 81) {
            stop();
            return;
        }
        if (waiting > 0) {
            if (--waiting == 0 && ++step >= route.points.size()) stop();
            return;
        }
        double distance = horizontal(player.position(), target);
        if (player.onGround() && Math.abs(player.getY() - target.y) <= (route == Route.TUBULATOR ? 1.5 : 1)
                && distance <= (route == Route.TUBULATOR ? .09 : .25)) {
            input.release();
            timeout = 0;
            waiting = route == Route.TUBULATOR ? 5 : route == Route.PARKOUR ? 3 : 1;
            return;
        }
        face(client, target);
        input.set(client.options.keyUp, distance > .04);
        input.set(client.options.keySprint, route == Route.TUBULATOR);
        boolean edge = horizontal(player.position(), from) >= .49 && distance > horizontal(from, target) * .25;
        input.set(client.options.keyJump, route != Route.MAZE && player.onGround() && edge);
        input.set(client.options.keyShift, route == Route.TUBULATOR && distance < .36 && !edge);
    }

    private static void redGreen(Minecraft client) {
        if (awaitingSound || waiting > 0 && --waiting > 0) return;
        if (step >= room.actions().size()) {
            stop();
            return;
        }
        var action = room.actions().get(step);
        switch (action) {
            case MirrorverseRoutes.Move move -> {
                if (horizontal(client.player.position(), move.position()) >= .2) {
                    face(client, move.position());
                    input.set(client.options.keyUp, true);
                    input.set(client.options.keySprint, true);
                    return;
                }
                input.release();
                waiting = ConfigManager.mirrorverseRedGreenActionDelay;
            }
            case MirrorverseRoutes.Interact interact -> {
                if (!interactAimed) {
                    client.player.lookAt(Anchor.EYES, Vec3.atCenterOf(interact.position()));
                    interactAimed = true;
                    waiting = ConfigManager.mirrorverseRedGreenInteractDelay;
                    return;
                }
                clickBlock(client, interact.position());
                interactAimed = false;
                waiting = ConfigManager.mirrorverseRedGreenActionDelay;
            }
            case MirrorverseRoutes.Swap swap -> {
                Vec3 pos = client.player.position();
                BlockPos left = BlockPos.containing(pos.x, pos.y, -104);
                var target = swap.side() == MirrorverseRoutes.Side.RIGHT || swap.side() == null
                    && !client.level.getBlockState(left).is(Blocks.STAINED_GLASS.red())
                        ? BlockPos.containing(pos.x, pos.y, -110) : left;
                if (client.level.getBlockState(target).is(Blocks.STAINED_GLASS.red())) {
                    client.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target));
                    clickBlock(client, target);
                    waiting = ConfigManager.mirrorverseRedGreenActionDelay;
                }
            }
            case MirrorverseRoutes.Pause pause -> waiting = pause.ticks();
            case MirrorverseRoutes.AwaitSound ignored -> { awaitingSound = true; return; }
        }
        timeout = 0;
        step++;
    }

    private static void clickBlock(Minecraft client, BlockPos target) {
        var eye = client.player.getEyePosition();
        var point = Vec3.atCenterOf(target);
        if (eye.distanceToSqr(point) >= 25) return;
        var hit = client.level.clip(new ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target))
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
    }

    private static void dance(Minecraft client) {
        int tick = client.player.tickCount;
        if (danceDirection != null) {
            int segment = danceStep % 4;
            double pos = segment % 2 == 0 ? client.player.getZ() : client.player.getX();
            if (pos >= SEGMENTS[segment] - 1 && pos <= SEGMENTS[segment]) {
                input.set(danceDirection, false);
                danceDirection = null;
                if (++danceStep >= DANCE.length) { stop(); return; }
                int action = DANCE[danceStep];
                if ((action & SNEAK) != 0) sneakAt = tick + ConfigManager.mirrorverseSneakDelay;
                if ((action & JUMP) != 0) jumpAt = tick + ConfigManager.mirrorverseJumpDelay;
                if ((action & PUNCH) != 0) punchAt = tick + ConfigManager.mirrorversePunchDelay;
            }
        }
        input.set(client.options.keyShift, sneakAt >= 0 && tick >= sneakAt && tick < sneakAt + 10);
        input.set(client.options.keyJump, jumpAt >= 0 && tick >= jumpAt && tick < jumpAt + 2);
        if (punchAt >= 0 && tick >= punchAt) {
            punchAt = -1;
            ((GameplayFeatures.ClickActions) client).csb$attack();
        }
    }

    private static void updateCraftGuides(Minecraft client) {
        var guides = new java.util.ArrayList<Guide>();
        for (var entity : client.level.getEntitiesOfClass(LivingEntity.class, CRAFT_ROOM)) {
            int color = entity instanceof Zombie ? 0x806D4427 : entity instanceof Slime ? 0x8055FF55
                : entity instanceof CaveSpider ? 0x80FFFFFF : 0;
            if (color == 0 || !entity.isAlive()) continue;
            var box = entity.getBoundingBox();
            guides.add(new Guide(new AABB(box.minX, box.minY, -233 - box.maxZ, box.maxX, box.maxY, -233 - box.minZ), color));
        }
        craftGuides = guides;
    }

    static void render(LevelRenderContext context) {
        if (!inRift || !GameplayModule.MIRRORVERSE.enabled()) return;
        if (ConfigManager.mirrorverseShowMobs && crafting) {
            for (var guide : craftGuides) drawGuide(context, guide.box(), guide.color());
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 pos = player.position();
        if (route != null) {
            drawGuide(context, new AABB(BlockPos.containing(route.target(step)).below()), 0x6055FF55);
        } else if (room != null && step < room.actions().size()) {
            var action = room.actions().get(step);
            if (action instanceof MirrorverseRoutes.Move move) drawGuide(context, new AABB(BlockPos.containing(move.position())), 0x6055FF55);
            if (action instanceof MirrorverseRoutes.Interact interact) drawGuide(context, new AABB(interact.position()), 0x60FFFF55);
        } else {
            for (var candidate : Route.values())
                if (candidate.enabled() && candidate.target(0).distanceToSqr(pos) < 625)
                    drawGuide(context, new AABB(BlockPos.containing(candidate.target(0)).below()), 0x6055FF55);
            if (ConfigManager.mirrorverseRedGreen) for (var candidate : MirrorverseRoutes.RED_GREEN)
                if (candidate.start().distToCenterSqr(pos) < 625) drawGuide(context, new AABB(candidate.start()), 0x6055FF55);
        }
    }

    private static void drawGuide(LevelRenderContext context, AABB bounds, int color) {
        var camera = context.levelState().cameraRenderState;
        var box = bounds.move(-camera.pos.x, -camera.pos.y, -camera.pos.z);
        context.submitNodeCollector().submitCustomGeometry(new PoseStack(), PartyRenderPipeline.type(), (matrix, output) ->
            PartyGeometry.box(output, matrix.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color));
    }

    private static final class Recipes {
        private static final Item S = Items.STICK, T = Items.STRING;
        static final ItemStack[][] GRIDS = {
            grid(S, null, null, T, S, null, T, null, S),
            grid(T, S, null, T, null, S, T, S, null),
            grid(null, T, T, null, Items.SLIME_BALL, T, T, null, null),
            grid(Items.LEAD, Items.BOW, null, Items.BOW, Items.FISHING_ROD, null, null, Items.FISHING_ROD, null)
        };
        private static ItemStack[] grid(Item... items) {
            var grid = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) grid[i] = items[i] == null ? ItemStack.EMPTY : new ItemStack(items[i]);
            return grid;
        }
    }

    static void drawRecipes(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen) {
        if (!crafting || !ConfigManager.mirrorverseShowRecipes || !GameplayModule.MIRRORVERSE.enabled()
            || !screen.getTitle().getString().contains("Craft")) return;
        int columns = graphics.guiWidth() >= 350 ? 4 : 2;
        int left = (graphics.guiWidth() - columns * 64) / 2;
        for (int i = 0; i < Recipes.GRIDS.length; i++) {
            int x = left + i % columns * 64, y = 8 + i / columns * 64;
            graphics.fill(x - 2, y - 2, x + 56, y + 56, 0xAD20242A);
            for (int slot = 0; slot < 9; slot++) {
                int sx = x + slot % 3 * 18, sy = y + slot / 3 * 18;
                graphics.fill(sx, sy, sx + 16, sy + 16, 0x26FFFFFF);
                var stack = Recipes.GRIDS[i][slot];
                if (!stack.isEmpty()) graphics.item(stack, sx, sy);
            }
        }
    }
}
