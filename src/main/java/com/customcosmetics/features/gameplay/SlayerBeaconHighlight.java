// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi's BeaconESP by jcnlk and contributors.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.DungeonSidebarReader;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.features.party.PartyTracer;
import com.customcosmetics.features.waypoint.Waypoints;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class SlayerBeaconHighlight {
    private static final HashMap<BlockPos, Long> placed = new HashMap<>();
    private static List<AABB> glyphs = List.of();
    private static boolean killing;

    private SlayerBeaconHighlight() {}

    static void reset() { placed.clear(); glyphs = List.of(); killing = false; }

    public static void blockChanged(BlockPos pos, BlockState state) {
        if (!killing || !GameplayModule.BEACON_ESP.enabled()) return;
        if (state.is(Blocks.BEACON)) placed.put(pos.immutable(), System.nanoTime());
        else placed.remove(pos);
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.BEACON_ESP.enabled() || !"combat_3".equals(Waypoints.currentIslandMode())) {
            if (killing) reset();
            return;
        }
        var rows = DungeonSidebarReader.read(client).rows();
        if (!rows.stream().anyMatch(row -> row.strip().equals("Slay the boss!"))
                || !rows.stream().anyMatch(row -> row.contains("Voidgloom Seraph"))) { reset(); return; }
        killing = true;
        long now = System.nanoTime();
        placed.values().removeIf(placedAt -> now - placedAt > 7_000_000_000L);
        var next = new ArrayList<AABB>();
        for (var pos : placed.keySet()) next.add(new AABB(pos));
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(32))) {
            if (stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.BEACON)) next.add(stand.getBoundingBox());
        }
        glyphs = next;
    }

    static void render(LevelRenderContext context) {
        if (glyphs.isEmpty() || !GameplayModule.BEACON_ESP.enabled()) return;
        boolean outline = ConfigManager.beaconOutline, fill = ConfigManager.beaconFill, tracer = ConfigManager.beaconTracer;
        if (!outline && !fill && !tracer) return;
        var camera = context.levelState().cameraRenderState;
        var pose = new PoseStack();
        var pipeline = ConfigManager.beaconDepth ? RenderTypes.debugQuads() : PartyRenderPipeline.type();
        var forward = tracer ? new Vector3f(0, 0, -1).rotate(camera.orientation) : null;
        var right = tracer ? new Vector3f(1, 0, 0).rotate(camera.orientation) : null;
        for (var bounds : glyphs) {
            var box = bounds.move(-camera.pos.x, -camera.pos.y, -camera.pos.z);
            if (outline || fill) context.submitNodeCollector().submitCustomGeometry(pose, pipeline, (matrix, output) -> {
                if (outline) PartyGeometry.outline(output, matrix.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    ConfigManager.beaconLineWidth * .009, 0xFF000000 | ConfigManager.beaconColor);
                if (fill) PartyGeometry.box(output, matrix.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    0x55000000 | ConfigManager.beaconFillColor);
            });
            if (tracer) {
                var points = PartyTracer.ribbon(new Vec3(forward.x, forward.y, forward.z), new Vec3(right.x, right.y, right.z),
                    box.getCenter(), Math.max(8, camera.depthFar * .5), ConfigManager.beaconTracerWidth / 2.0);
                if (!points.isEmpty()) context.submitNodeCollector().submitCustomGeometry(pose, pipeline, (matrix, output) -> {
                    for (var point : points) output.addVertex(matrix.pose(), (float) point.x, (float) point.y, (float) point.z)
                        .setColor(0xFF000000 | ConfigManager.beaconTracerColor);
                });
            }
        }
    }
}
