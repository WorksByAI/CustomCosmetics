// SPDX-License-Identifier: GPL-3.0-only
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.DungeonSidebarReader;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.features.party.PartyTracer;
import com.customcosmetics.gui.UiText;
import com.customcosmetics.utils.MessageUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.Locale;
import java.util.regex.Pattern;

/** Slayer state transitions and split reporting adapted from Quoi's Slayers module. */
final class SlayerSplits {
    private static final Pattern BOSS_LABEL = Pattern.compile(".* (I{1,3}|IV|V) \\d+.*❤$");
    private static long questStarted, bossSpawned;
    private static boolean wasKilling;
    private static Object sidebar;
    private static Mob boss;

    private SlayerSplits() {}

    static void reset() {
        questStarted = bossSpawned = 0;
        wasKilling = false;
        sidebar = null;
        boss = null;
    }

    private static boolean enabled() {
        return GameplayModule.BOSS_SPAWN_ALERT.enabled() || GameplayModule.SLAYER_SPLITS.enabled()
            || GameplayModule.SLAYER_BOSS_HIGHLIGHT.enabled();
    }

    static void message(Minecraft client, String text) {
        if (!enabled()) return;
        switch (text) {
            case "SLAYER QUEST STARTED!" -> questStarted = System.nanoTime();
            case "SLAYER QUEST COMPLETE!", "YOU COCOONED YOUR SLAYER BOSS" -> finish(client);
            case "SLAYER QUEST FAILED!" -> reset();
        }
    }

    static void tick(Minecraft client) {
        if (!enabled()) {
            if (sidebar != null) reset();
            return;
        }
        var snapshot = DungeonSidebarReader.read(client);
        if (sidebar == snapshot) {
            updateBoss(client);
            return;
        }
        sidebar = snapshot;
        boolean killing = false, slain = false;
        for (String row : snapshot.rows()) {
            String line = row.strip();
            if (line.equals("Slay the boss!")) killing = true;
            if (line.equals("Boss slain!")) slain = true;
        }
        if (killing && !wasKilling) {
            bossSpawned = System.nanoTime();
            if (questStarted != 0 && GameplayModule.SLAYER_SPLITS.enabled() && ConfigManager.slayerSpawnTime)
                report(client, "Spawn Time", bossSpawned - questStarted);
            if (GameplayModule.BOSS_SPAWN_ALERT.enabled())
                ClientUiAccess.title(client, UiText.component("Slayer Boss Spawned"), Component.empty());
        } else if (slain && wasKilling) finish(client);
        if (!killing && !slain && wasKilling) bossSpawned = 0;
        wasKilling = killing;
        updateBoss(client);
    }

    private static void updateBoss(Minecraft client) {
        if (!wasKilling || !GameplayModule.SLAYER_BOSS_HIGHLIGHT.enabled()) {
            boss = null;
            return;
        }
        if (boss != null && boss.isAlive()) return;
        boss = null;
        String ownerLabel = client.player.getGameProfile().name() + "'s SLAYER BOSS";
        var stands = client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(36), ArmorStand::hasCustomName);
        for (var owner : stands) {
            if (!ownerLabel.equals(owner.getCustomName().getString().strip())) continue;
            Mob closest = null;
            double distance = 1;
            for (var label : stands) {
                double distanceSquared = label.distanceToSqr(owner);
                if (distanceSquared > distance || !BOSS_LABEL.matcher(label.getCustomName().getString().strip()).matches()) continue;
                if (!(client.level.getEntity(label.getId() - 1) instanceof Mob candidate) || !candidate.isAlive()
                    || candidate.distanceToSqr(owner) > 16) continue;
                closest = candidate;
                distance = distanceSquared;
            }
            if (closest != null) {
                boss = closest;
                return;
            }
        }
    }

    static void render(LevelRenderContext context) {
        if (boss == null || !boss.isAlive() || boss.isInvisible() || !GameplayModule.SLAYER_BOSS_HIGHLIGHT.enabled()) return;
        var camera = context.levelState().cameraRenderState;
        var box = boss.getBoundingBox().move(-camera.pos.x, -camera.pos.y, -camera.pos.z);
        var pose = new PoseStack();
        int color = 0xFF000000 | ConfigManager.slayerBossColor;
        if (ConfigManager.slayerBossOutline || ConfigManager.slayerBossFill) {
            context.submitNodeCollector().submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrix, output) -> {
                if (ConfigManager.slayerBossFill) PartyGeometry.box(output, matrix.pose(),
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0x43000000 | ConfigManager.slayerBossColor);
                if (ConfigManager.slayerBossOutline) PartyGeometry.outline(output, matrix.pose(),
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, .009 * ConfigManager.slayerBossWidth, color);
            });
        }
        if (!ConfigManager.slayerBossTracer) return;
        var forward = new Vector3f(0, 0, -1).rotate(camera.orientation);
        var right = new Vector3f(1, 0, 0).rotate(camera.orientation);
        var points = PartyTracer.ribbon(new Vec3(forward.x, forward.y, forward.z), new Vec3(right.x, right.y, right.z),
            box.getCenter(), Math.max(8, camera.depthFar * .5), ConfigManager.slayerBossWidth / 2.0);
        if (!points.isEmpty()) context.submitNodeCollector().submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrix, output) -> {
            for (var point : points) output.addVertex(matrix.pose(), (float) point.x, (float) point.y, (float) point.z).setColor(color);
        });
    }

    private static void finish(Minecraft client) {
        if (bossSpawned != 0 && GameplayModule.SLAYER_SPLITS.enabled() && ConfigManager.slayerKillTime)
            report(client, "Kill Time", System.nanoTime() - bossSpawned);
        questStarted = bossSpawned = 0;
        boss = null;
    }

    private static void report(Minecraft client, String label, long duration) {
        ClientUiAccess.chat(client).addClientSystemMessage(MessageUtils.createMessageWithPrefix(
            UiText.tr(label) + " " + String.format(Locale.ROOT, "%.1fs", duration / 1_000_000_000.0)));
    }
}
