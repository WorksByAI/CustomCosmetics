// SPDX-License-Identifier: GPL-3.0-only
// Projectile integration adapted from quoi! Trajectories (jcnlk/quoi).
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

final class Trajectories {
    private static List<Vec3> points = List.of();
    private static BlockHitResult impact;

    private Trajectories() {}

    static void tick(Minecraft client) {
        var player = client.player;
        impact = null;
        if (!GameplayModule.TRAJECTORIES.enabled() || player == null || client.level == null) {
            points = List.of();
            return;
        }
        var held = player.getMainHandItem();
        boolean pearl = ConfigManager.trajectoryPearls && held.getItem() instanceof EnderpearlItem
            && !held.getHoverName().getString().contains("Spirit Leap");
        boolean bow = ConfigManager.trajectoryBows && held.getItem() instanceof BowItem && player.isUsingItem();
        if (!pearl && !bow) {
            points = List.of();
            return;
        }
        float charge = bow ? BowItem.getPowerForTime(player.getTicksUsingItem()) : 1;
        Vec3 position = player.getEyePosition().add(0, -.1, 0);
        Vec3 motion = player.getViewVector(1).scale(pearl ? 1.5 : 3 * charge);
        var next = new ArrayList<Vec3>(ConfigManager.trajectoryTicks + 1);
        next.add(position);
        for (int i = 0; i < ConfigManager.trajectoryTicks; i++) {
            Vec3 end = position.add(motion);
            var hit = client.level.clip(new ClipContext(position, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                impact = hit;
                next.add(hit.getLocation());
                break;
            }
            next.add(end);
            position = end;
            motion = motion.scale(.99).add(0, pearl ? -.03 : -.05, 0);
        }
        points = next;
    }

    static void render(LevelRenderContext context) {
        var path = points;
        if (!GameplayModule.TRAJECTORIES.enabled() || path.size() < 2) return;
        var camera = context.levelState().cameraRenderState.pos;
        var hit = impact;
        boolean line = ConfigManager.trajectoryLine, box = ConfigManager.trajectoryBox, plane = ConfigManager.trajectoryPlane;
        if (!line && (hit == null || !box && !plane)) return;
        int color = 0xFF000000 | ConfigManager.trajectoryColor;
        double width = .017 * ConfigManager.trajectoryLineWidth;
        var pipeline = ConfigManager.trajectoryDepth ? RenderTypes.debugQuads() : PartyRenderPipeline.type();
        context.submitNodeCollector().submitCustomGeometry(new PoseStack(), pipeline, (matrices, output) -> {
            for (int i = 1; line && i < path.size(); i++) {
                Vec3 a = path.get(i - 1).subtract(camera), b = path.get(i).subtract(camera);
                Vec3 side = b.subtract(a).cross(a).normalize().scale(width);
                Vec3 av = a.add(side), aw = a.subtract(side), bv = b.add(side), bw = b.subtract(side);
                var pose = matrices.pose();
                output.addVertex(pose, (float) av.x, (float) av.y, (float) av.z).setColor(color);
                output.addVertex(pose, (float) aw.x, (float) aw.y, (float) aw.z).setColor(color);
                output.addVertex(pose, (float) bw.x, (float) bw.y, (float) bw.z).setColor(color);
                output.addVertex(pose, (float) bv.x, (float) bv.y, (float) bv.z).setColor(color);
            }
            if (hit != null && box) {
                double size = ConfigManager.trajectoryBoxSize;
                var bounds = AABB.ofSize(hit.getLocation().subtract(camera), size, size, size);
                PartyGeometry.outline(output, matrices.pose(), bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ, .015, color);
            }
            if (hit != null && plane) {
                var face = hit.getDirection();
                var center = hit.getLocation().subtract(camera).add(face.getStepX() * .01, face.getStepY() * .01, face.getStepZ() * .01);
                var axis = face.getAxis();
                double size = ConfigManager.trajectoryPlaneSize;
                var bounds = AABB.ofSize(center, axis == net.minecraft.core.Direction.Axis.X ? .006 : size,
                    axis == net.minecraft.core.Direction.Axis.Y ? .006 : size,
                    axis == net.minecraft.core.Direction.Axis.Z ? .006 : size);
                PartyGeometry.box(output, matrices.pose(), bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ, 0x55000000 | ConfigManager.trajectoryColor);
            }
        });
    }
}
