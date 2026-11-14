// SPDX-License-Identifier: BSD-3-Clause
// Structure signatures and quarter bounds adapted from Nebulune, Copyright (c) 2025 Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.features.party.PartyTracer;
import com.customcosmetics.features.waypoint.Waypoints;
import com.customcosmetics.utils.MessageUtils;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class CrystalHollowsScanner {
    private record Rule(Block block, SlabType slab) {
        boolean matches(BlockState state) {
            return state.is(block) && (slab == null || state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == slab);
        }
    }
    private record Structure(String key, String name, String quarter, BlockPos offset, Rule[] rules, int anchor, WorldScannerSettings.Target target) {}
    private record Point(Component name, BlockPos position, WorldScannerSettings.Target target, Component blockCount) {}
    private record JasperChunk(BlockPos center, int count) {}
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private static final int MAX_STRUCTURE_BASE_Y = 169;
    private static final long SCAN_BUDGET_NANOS = 750_000L;
    private static final Set<Integer> seen = new HashSet<>();
    private static final ArrayDeque<Integer> pending = new ArrayDeque<>();
    private static final Set<Structure> found = new HashSet<>();
    private static final List<Point> markers = new ArrayList<>();
    private static final Map<Integer, JasperChunk> jasperChunks = new HashMap<>();
    private static List<Point> grottos = List.of();
    private static Map<Block, List<Structure>> candidates;
    private static final Predicate<BlockState> HAS_CANDIDATE = CrystalHollowsScanner::isCandidate;
    private static int maxAnchorY;
    private static LevelChunk scanning;
    private static LevelChunkSection section;
    private static int minX, minZ, sectionIndex, sectionY, cell, jasper, jasperX, jasperY, jasperZ;
    private static boolean active;

    private CrystalHollowsScanner() {}

    static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (active) enqueue(chunk);
        });
    }

    static void reset() {
        active = false;
        markers.clear();
        jasperChunks.clear();
        grottos = List.of();
        seen.clear();
        found.clear();
        pending.clear();
        scanning = null;
        section = null;
    }

    private static void enqueue(LevelChunk chunk) {
        int x = chunk.getPos().getMinBlockX(), z = chunk.getPos().getMinBlockZ();
        if (x + 15 < 201 || z + 15 < 201 || x > 824 || z > 824) return;
        int key = (x >> 4) << 8 | (z >> 4);
        if (seen.add(key)) pending.addLast(key);
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.WORLD_SCANNER.enabled() || !"crystal_hollows".equals(Waypoints.currentIslandMode())) {
            if (active) reset();
            return;
        }
        var chunks = client.level.getChunkSource();
        if (!active) {
            active = true;
            int x = client.player.blockPosition().getX() >> 4, z = client.player.blockPosition().getZ() >> 4;
            int radius = Math.min(24, client.options.getEffectiveRenderDistance() + 3);
            for (int ring = 0; ring <= radius; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    enqueueLoaded(client, x + dx, z - ring);
                    if (ring > 0) enqueueLoaded(client, x + dx, z + ring);
                }
                for (int dz = 1 - ring; dz < ring; dz++) {
                    enqueueLoaded(client, x - ring, z + dz);
                    enqueueLoaded(client, x + ring, z + dz);
                }
            }
        }
        if (scanning == null && pending.isEmpty()) return;
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        do {
            if (scanning == null) {
                if (pending.isEmpty()) return;
                int next = pending.removeFirst();
                var chunk = chunks.getChunk(next >> 8, next & 255, ChunkStatus.FULL, false);
                if (chunk == null) { seen.remove(next); continue; }
                startChunk(chunk);
            }
            advanceChunk(client);
        } while (System.nanoTime() < deadline);
    }

    private static void enqueueLoaded(Minecraft client, int x, int z) {
        var chunk = client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
        if (chunk != null) enqueue(chunk);
    }

    private static void startChunk(LevelChunk chunk) {
        if (candidates == null) candidates = signatures();
        scanning = chunk;
        minX = chunk.getPos().getMinBlockX();
        minZ = chunk.getPos().getMinBlockZ();
        sectionIndex = Math.max(0, chunk.getSectionIndex(0));
        sectionY = chunk.getSectionYFromSectionIndex(sectionIndex) * 16;
        jasper = jasperX = jasperY = jasperZ = 0;
    }

    private static void advanceChunk(Minecraft client) {
        if (section != null) {
            scanSlice(client);
            return;
        }
        var sections = scanning.getSections();
        if (sectionIndex >= sections.length || sectionY > maxAnchorY) {
            finishChunk(client);
            return;
        }
        var next = sections[sectionIndex++];
        if (!next.hasOnlyAir() && next.maybeHas(HAS_CANDIDATE)) {
            section = next;
            cell = 0;
        } else sectionY += 16;
    }

    private static void finishChunk(Minecraft client) {
        if (jasper > 0) {
            var center = new BlockPos(jasperX / jasper, jasperY / jasper, jasperZ / jasper);
            int key = (minX >> 4) << 8 | (minZ >> 4);
            jasperChunks.put(key, new JasperChunk(center, jasper));
            updateGrottos(client, key);
        }
        scanning = null;
    }

    private static boolean isCandidate(BlockState state) {
        var matches = candidates.get(state.getBlock());
        if (matches == null) return false;
        for (int i = 0; i < matches.size(); i++) {
            var match = matches.get(i);
            if (match.target() == WorldScannerSettings.Target.FAIRY_GROTTO || !found.contains(match)
                && match.rules()[match.anchor()].matches(state)) return true;
        }
        return false;
    }

    private static void scanSlice(Minecraft client) {
        int end = cell + 128;
        for (; cell < end; cell++) {
            int dx = cell & 15, dz = (cell >> 4) & 15, dy = cell >> 8;
            int x = minX + dx, z = minZ + dz;
            if (x < 201 || x > 824 || z < 201 || z > 824) continue;
            var state = section.getBlockState(dx, dy, dz);
            var matches = candidates.get(state.getBlock());
            if (matches == null) continue;
            for (int i = 0; i < matches.size(); i++) {
                var match = matches.get(i);
                int y = sectionY + dy - match.anchor();
                if (y < 0 || y > MAX_STRUCTURE_BASE_Y) continue;
                if (match.target() == WorldScannerSettings.Target.FAIRY_GROTTO) {
                    if (!quarter("NUCLEUS", x, y, z)) {
                        jasper++;
                        jasperX += x; jasperY += y; jasperZ += z;
                    }
                } else if (!found.contains(match) && match.rules()[match.anchor()].matches(state) && quarter(match.quarter(), x, y, z)
                    && (!match.key().equals("WORM_FISHING") || x >= 513 && z >= 513 && y >= 80) && matches(match, x, y, z)) {
                    found.add(match);
                    var position = new BlockPos(x, y, z).offset(match.offset());
                    markers.add(new Point(Component.literal(match.name()), position, match.target(), null));
                    var options = match.target().options();
                    if (options.enabled() && options.chat()) announce(client, match.name(), position);
                }
            }
        }
        if (cell == 4096) {
            section = null;
            sectionY += 16;
        }
    }

    private static boolean quarter(String quarter, int x, int y, int z) {
        return switch (quarter) {
            case "JUNGLE" -> x <= 576 && z <= 576;
            case "PRECURSOR_REMNANTS" -> x > 448 && z > 448;
            case "GOBLIN_HOLDOUT" -> x <= 576 && z > 448;
            case "MITHRIL_DEPOSITS" -> x > 448 && z <= 576;
            case "MAGMA_FIELDS" -> y < 80;
            case "NUCLEUS" -> x >= 449 && x <= 576 && z >= 449 && z <= 576;
            default -> true;
        };
    }

    private static boolean matches(Structure structure, int x, int y, int z) {
        var rules = structure.rules();
        for (int i = 0; i < rules.length; i++) {
            Rule rule = rules[i];
            if (i != structure.anchor() && rule != null && !rule.matches(scanning.getBlockState(cursor.set(x, y + i, z)))) return false;
        }
        return true;
    }

    private static void announce(Minecraft client, String name, BlockPos position) {
        ClientUiAccess.chat(client).addClientSystemMessage(MessageUtils.createMessageWithPrefix(name + " · " + position.toShortString()));
    }

    private static void updateGrottos(Minecraft client, int newChunk) {
        var remaining = new HashSet<>(jasperChunks.keySet());
        var result = new ArrayList<Point>();
        var target = WorldScannerSettings.Target.FAIRY_GROTTO;
        while (!remaining.isEmpty()) {
            var queue = new ArrayDeque<Integer>();
            queue.add(remaining.iterator().next());
            int x = 0, y = 0, z = 0, blocks = 0, chunks = 0;
            boolean containsNew = false;
            while (!queue.isEmpty()) {
                int key = queue.removeFirst();
                if (!remaining.remove(key)) continue;
                var chunk = jasperChunks.get(key);
                x += chunk.center().getX(); y += chunk.center().getY(); z += chunk.center().getZ();
                blocks += chunk.count(); chunks++;
                containsNew |= key == newChunk;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int next = key + (dx << 8) + dz;
                    if (remaining.contains(next)) queue.add(next);
                }
            }
            var center = new BlockPos(x / chunks, y / chunks, z / chunks);
            result.add(new Point(Component.literal(target.label), center, target, Component.literal(Integer.toString(blocks))));
            if (containsNew && chunks == 1 && target.options().enabled() && target.options().chat()) announce(client, target.label, center);
        }
        grottos = List.copyOf(result);
    }

    private static Map<Block, List<Structure>> signatures() {
        var result = new IdentityHashMap<Block, List<Structure>>();
        maxAnchorY = MAX_STRUCTURE_BASE_Y;
        try (var input = CrystalHollowsScanner.class.getResourceAsStream("/assets/customcosmetics/gameplay/crystal-hollows-structures.json")) {
            if (input == null) throw new IllegalStateException("Missing Crystal Hollows structure definitions");
            for (var row : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var definition = row.getAsJsonObject();
                var blocks = definition.getAsJsonArray("blocks");
                var offset = definition.getAsJsonArray("offset");
                var rules = new Rule[blocks.size()];
                for (int i = 0; i < rules.length; i++) {
                    if (blocks.get(i).isJsonNull()) continue;
                    var value = blocks.get(i).getAsJsonObject();
                    var id = Identifier.withDefaultNamespace(value.get("block").getAsString());
                    var block = BuiltInRegistries.BLOCK.getOptional(id).orElseThrow();
                    var slab = value.has("slab") ? SlabType.valueOf(value.get("slab").getAsString().toUpperCase(java.util.Locale.ROOT)) : null;
                    rules[i] = new Rule(block, slab);
                }
                String key = definition.get("key").getAsString();
                int anchor = anchor(key, blocks);
                maxAnchorY = Math.max(maxAnchorY, MAX_STRUCTURE_BASE_Y + anchor);
                var structure = new Structure(key, definition.get("name").getAsString(), definition.get("quarter").getAsString(),
                    new BlockPos(offset.get(0).getAsInt(), offset.get(1).getAsInt(), offset.get(2).getAsInt()), rules, anchor, WorldScannerSettings.Target.of(key));
                result.computeIfAbsent(rules[anchor].block(), unused -> new ArrayList<>()).add(structure);
                if (structure.key().equals("FAIRY_GROTTO")) result.computeIfAbsent(rules[1].block(), unused -> new ArrayList<>()).add(structure);
            }
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        return result;
    }

    // Start on distinctive blocks so ordinary stone sections can be skipped via their palette.
    private static int anchor(String key, com.google.gson.JsonArray blocks) {
        String anchorBlock = switch (key) {
            case "QUEEN" -> "cauldron";
            case "DIVAN" -> "chiseled_stone_bricks";
            case "CITY" -> "dark_oak_stairs";
            case "TEMPLE" -> "green_terracotta";
            case "BAL" -> "barrier";
            case "CORLEONE_DOCK" -> "fire";
            case "CORLEONE_HOLE" -> "smooth_stone_slab";
            case "KEY_GUARDIAN_TOWER" -> "polished_granite";
            case "XALX" -> "coal_block";
            case "PETE" -> "fire";
            case "ODAWA" -> "yellow_terracotta";
            case "GOLDEN_DRAGON" -> "red_wool";
            default -> null;
        };
        if (anchorBlock != null) for (int i = 0; i < blocks.size(); i++) {
            if (!blocks.get(i).isJsonNull() && anchorBlock.equals(blocks.get(i).getAsJsonObject().get("block").getAsString())) return i;
        }
        return 0;
    }

    static void render(LevelRenderContext context) {
        if (!active || markers.isEmpty() && grottos.isEmpty() || !GameplayModule.WORLD_SCANNER.enabled()) return;
        var pose = new PoseStack();
        for (var marker : markers) render(context, pose, marker);
        for (var marker : grottos) render(context, pose, marker);
    }

    private static void render(LevelRenderContext context, PoseStack pose, Point marker) {
        var settings = marker.target().options();
        if (!settings.enabled()) return;
        var camera = context.levelState().cameraRenderState;
        var p = camera.pos;
        var box = new AABB(marker.position()).move(-p.x, -p.y, -p.z);
        int color = 0xFF000000 | settings.color();
        var queue = context.submitNodeCollector();
        queue.submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrices, output) -> {
            if (settings.style() != 0) PartyGeometry.box(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0x44000000 | settings.color());
            if (settings.style() != 1) PartyGeometry.outline(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, .035, color);
        });
        var center = box.getCenter();
        if (settings.tracer()) {
            var f = new org.joml.Vector3f(0, 0, -1).rotate(camera.orientation);
            var r = new org.joml.Vector3f(1, 0, 0).rotate(camera.orientation);
            var ribbon = PartyTracer.ribbon(new Vec3(f.x, f.y, f.z), new Vec3(r.x, r.y, r.z), center, Math.max(8, camera.depthFar * .5), 2);
            if (!ribbon.isEmpty()) queue.submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrices, output) -> {
                for (var vertex : ribbon) output.addVertex(matrices.pose(), (float) vertex.x, (float) vertex.y, (float) vertex.z).setColor(color);
            });
        }
        if (settings.scale() == 0) return;
        if (settings.name()) drawLabel(context, marker.name(), marker.blockCount() == null ? center : center.add(0, 10, 0), settings.scale(), settings.background(), color);
        if (marker.blockCount() != null && ConfigManager.scannerShowJasperCount)
            drawLabel(context, marker.blockCount(), center, settings.scale(), ConfigManager.scannerJasperBackground, color);
    }

    private static void drawLabel(LevelRenderContext context, Component text, Vec3 pos, double size, double background, int color) {
        var pose = new PoseStack();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(new org.joml.Matrix4f().rotation(context.levelState().cameraRenderState.orientation));
        float scale = (float) (.025 * size * Math.max(1, pos.length() / 14));
        pose.scale(scale, -scale, scale);
        var font = Minecraft.getInstance().font;
        context.submitNodeCollector().submitText(pose, -font.width(text) / 2f, 0, text.getVisualOrderText(), true,
            Font.DisplayMode.SEE_THROUGH, 0xF000F0, color, (int) (255 * background) << 24, 0);
    }
}
