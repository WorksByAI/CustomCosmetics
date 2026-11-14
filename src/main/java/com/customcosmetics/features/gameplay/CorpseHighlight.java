// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Nebulune CorpseESP, Copyright (c) 2025 Starred. See NOTICE.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.features.party.PartyTracer;
import com.customcosmetics.features.waypoint.Waypoints;
import com.customcosmetics.gui.UiText;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

final class CorpseHighlight {
    private enum Type {
        LAPIS("Lapis", "LAPIS_ARMOR_HELMET", "lapis armor helmet", () -> ConfigManager.lapisCorpse, () -> ConfigManager.lapisCorpseColor),
        TUNGSTEN("Tungsten", "MINERAL_HELMET", "mineral helmet", () -> ConfigManager.tungstenCorpse, () -> ConfigManager.tungstenCorpseColor),
        UMBER("Umber", "YOG_HELMET", "yog helmet", () -> ConfigManager.umberCorpse, () -> ConfigManager.umberCorpseColor),
        VANGUARD("Vanguard", "VANGUARD_HELMET", "vanguard helmet", () -> ConfigManager.vanguardCorpse, () -> ConfigManager.vanguardCorpseColor);
        final String label, helmetId, helmetName;
        final BooleanSupplier enabled;
        final IntSupplier color;

        Type(String label, String helmetId, String helmetName, BooleanSupplier enabled, IntSupplier color) {
            this.label = label;
            this.helmetId = helmetId;
            this.helmetName = helmetName;
            this.enabled = enabled;
            this.color = color;
        }
    }

    private record Corpse(ArmorStand stand, int color, Component label) {}
    private static final Type[] TYPES = Type.values();
    private static final ArrayList<Vec3> CLAIMED = new ArrayList<>();
    private static List<Corpse> corpses = List.of();
    private static boolean inMineshaft;

    private CorpseHighlight() {}

    static void reset() {
        inMineshaft = false;
        corpses = List.of();
        CLAIMED.clear();
    }

    static void message(Minecraft client, String line) {
        if (inMineshaft && GameplayModule.CORPSE_ESP.enabled() && client.player != null && line.contains("CORPSE LOOT!"))
            CLAIMED.add(client.player.position());
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.CORPSE_ESP.enabled() || client.player == null || client.level == null) {
            if (inMineshaft) reset();
            return;
        }
        String island = Waypoints.currentIslandMode();
        if (!"mineshaft".equals(island) && !"glacite_mineshaft".equals(island)) {
            if (inMineshaft) reset();
            return;
        }
        inMineshaft = true;
        var next = new ArrayList<Corpse>();
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(64))) {
            var head = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (!stand.isAlive() || head.isEmpty()) continue;
            String id = LegacyPackItems.id(head);
            String name = head.getHoverName().getString().toLowerCase(Locale.ROOT);
            for (var type : TYPES) {
                if (!type.helmetId.equalsIgnoreCase(id) && !name.contains(type.helmetName)) continue;
                if (!type.enabled.getAsBoolean()) break;
                boolean claimed = isClaimed(stand.position());
                if (claimed && ConfigManager.corpseHideClaimed) break;
                String label = ConfigManager.corpseName ? UiText.tr(type.label) + (claimed ? " (" + UiText.tr("Looted") + ")" : "") : "";
                if (ConfigManager.corpseDistance) label += (label.isEmpty() ? "" : " ") + "[" + (int) client.player.distanceTo(stand) + "m]";
                next.add(new Corpse(stand, claimed ? 0x808080 : type.color.getAsInt(), Component.literal(label)));
                break;
            }
        }
        corpses = next;
    }

    private static boolean isClaimed(Vec3 position) {
        for (var point : CLAIMED) if (point.distanceToSqr(position) < 25) return true;
        return false;
    }

    static void render(LevelRenderContext context) {
        if (corpses.isEmpty() || !GameplayModule.CORPSE_ESP.enabled()) return;
        var camera = context.levelState().cameraRenderState;
        var position = camera.pos;
        var pipeline = ConfigManager.corpseDepth ? RenderTypes.debugQuads() : PartyRenderPipeline.type();
        var pose = new PoseStack();
        var font = Minecraft.getInstance().font;
        var queue = context.submitNodeCollector();
        boolean tracer = ConfigManager.corpseTracer;
        var forward = tracer ? new org.joml.Vector3f(0, 0, -1).rotate(camera.orientation) : null;
        var right = tracer ? new org.joml.Vector3f(1, 0, 0).rotate(camera.orientation) : null;
        for (var corpse : corpses) {
            var stand = corpse.stand();
            if (stand.isRemoved()) continue;
            var box = (ConfigManager.corpseCube ? new AABB(stand.blockPosition()) : stand.getBoundingBox())
                .move(-position.x, -position.y, -position.z);
            double width = .008 * ConfigManager.corpseLineWidth;
            int color = 0xFF000000 | corpse.color();
            if (ConfigManager.corpseFill || ConfigManager.corpseOutline) queue.submitCustomGeometry(pose, pipeline, (matrices, output) -> {
                if (ConfigManager.corpseFill) PartyGeometry.box(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0x44000000 | corpse.color());
                if (ConfigManager.corpseOutline) PartyGeometry.outline(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, width, color);
            });
            if (tracer) {
                var ribbon = PartyTracer.ribbon(new Vec3(forward.x, forward.y, forward.z), new Vec3(right.x, right.y, right.z),
                    box.getCenter(), Math.max(8, camera.depthFar * .5), ConfigManager.corpseLineWidth / 2);
                if (!ribbon.isEmpty()) queue.submitCustomGeometry(pose, pipeline, (matrices, output) -> {
                    for (var point : ribbon) output.addVertex(matrices.pose(), (float) point.x, (float) point.y, (float) point.z).setColor(color);
                });
            }
            var label = corpse.label();
            if (!label.getString().isEmpty()) {
                var point = box.getCenter();
                var labelPose = new PoseStack();
                labelPose.translate(point.x, box.maxY + .3, point.z);
                labelPose.mulPose(new org.joml.Matrix4f().rotation(camera.orientation));
                float scale = (float) (.025 * Math.max(1, point.length() / 14));
                labelPose.scale(scale, -scale, scale);
                queue.submitText(labelPose, -font.width(label) / 2f, 0, label.getVisualOrderText(), true,
                    ConfigManager.corpseDepth ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH, 0xF000F0, color, 0x60000000, 0);
            }
        }
    }
}
