/* Adapted from Baity FancyDmgSplash, Copyright (C) 2025 raueyhs.
 * Modifications Copyright (C) 2026 CustomCosmetics contributors.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package com.customcosmetics.features.damage;

import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import java.util.*;

/** Client-thread-only visual replacement. Does not remove entities or send any packets. */
public final class DamageSplash {
    private static final int MAX_LABELS = 256, MAX_SEEN = 2048;
    private static final Map<UUID, Seen> SEEN = new LinkedHashMap<>();
    private static final Deque<Label> LABELS = new ArrayDeque<>();
    private static Object level;
    private static boolean allowed;
    private record Seen(int id, String text) {}
    private record Label(Vec3 position, Component text, boolean critical, long started, double driftX, double driftZ, double phase) {}
    private DamageSplash() {}
    public static void init() { LevelRenderEvents.COLLECT_SUBMITS.register(DamageSplash::render); }

    public static boolean isSkyblock(Minecraft client) {
        if (client.level == null || client.player == null || client.hasSingleplayerServer()) return false;
        var server = client.getCurrentServer();
        boolean hypixel = server != null && server.ip.toLowerCase(Locale.ROOT).matches("(?:.*\\.)?hypixel\\.net(?::[0-9]+)?")
            || com.customcosmetics.features.animations.PresenceWorld.hasHypixelLocation(client);
        var objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return hypixel && objective != null && objective.getDisplayName().getString().replaceAll("§.", "").toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }

    public static void tick(Minecraft client) {
        boolean next = ConfigManager.damageSplashEnabled && isSkyblock(client);
        if (level != client.level || !next) { SEEN.clear(); LABELS.clear(); }
        level = client.level; allowed = next;
        if (!next) return;
        SEEN.entrySet().removeIf(entry -> {
            var entity = client.level.getEntity(entry.getValue().id());
            return entity == null || !entity.getUUID().equals(entry.getKey()) || !entity.hasCustomName()
                || !entity.getCustomName().getString().equals(entry.getValue().text());
        });
        long now = System.nanoTime();
        LABELS.removeIf(label -> progress(label.started(), now) >= 1);
    }

    public static boolean capture(ArmorStand stand) {
        if (!ConfigManager.damageSplashEnabled || !allowed || stand.level() != level || !stand.isInvisible() || !stand.hasCustomName()) return false;
        String name = stand.getCustomName().getString();
        var previous = SEEN.get(stand.getUUID());
        if (previous != null && previous.text().equals(name)) return true;
        var hit = DamageText.parse(name);
        if (hit == null) return false;
        SEEN.put(stand.getUUID(), new Seen(stand.getId(), name));
        if (SEEN.size() > MAX_SEEN) SEEN.remove(SEEN.keySet().iterator().next());
        var random = java.util.concurrent.ThreadLocalRandom.current();
        LABELS.addLast(new Label(stand.position().add(0, stand.getBbHeight()+.5, 0), styled(hit, originalColor(stand.getCustomName())),
            hit.critical(), System.nanoTime(), random.nextDouble(-.6, .6), random.nextDouble(-.6, .6), random.nextDouble(Math.PI*2)));
        if (LABELS.size() > MAX_LABELS) LABELS.removeFirst();
        return true;
    }

    public static Component styled(DamageText.Hit hit, int originalColor) {
        var text = Component.empty();
        for (var glyph : DamageText.glyphs(hit, ConfigManager.damageSplashCompact, ConfigManager.damageSplashSeparator,
                ConfigManager.damageSplashSymbol, ConfigManager.damageSplashStyleNormal,
                ConfigManager.damageSplashStartColor, ConfigManager.damageSplashEndColor, originalColor))
            text.append(Component.literal(glyph.text()).withStyle(style -> style.withColor(glyph.color())
                .withBold(ConfigManager.damageSplashBold && (hit.critical() || ConfigManager.damageSplashStyleNormal))));
        return text;
    }

    private static int originalColor(Component text) {
        return text.<Integer>visit((style, value) -> value.isEmpty() || style.getColor() == null ? Optional.empty() : Optional.of(style.getColor().getValue()), net.minecraft.network.chat.Style.EMPTY).orElse(0xFFFFFF);
    }
    private static double progress(long start, long now) { return (now-start)/1_000_000_000.0/DamageAnimation.bounded(ConfigManager.damageSplashDuration, 1.2, .3, 3); }
    private static void render(LevelRenderContext context) {
        if (!ConfigManager.damageSplashEnabled || !allowed || level != Minecraft.getInstance().level || LABELS.isEmpty()) return;
        var client = Minecraft.getInstance(); var camera = context.levelState().cameraRenderState;
        long now = System.nanoTime();
        for (var label : LABELS) {
            double p = progress(label.started(), now);
            if (p >= 1 || label.position().distanceToSqr(camera.pos) > 128*128) continue;
            var frame = DamageAnimation.frame(ConfigManager.damageSplashStyle, p, label.critical(), label.driftX(), label.driftZ(), label.phase());
            if (frame.alpha() <= .01f) continue;
            var relative = label.position().add(frame.x(), frame.y(), frame.z()).subtract(camera.pos);
            var pose = new PoseStack(); pose.translate(relative.x, relative.y, relative.z);
            pose.mulPose(new org.joml.Matrix4f().rotation(camera.orientation));
            float scale = (float) (.0325*DamageAnimation.bounded(ConfigManager.damageSplashSize, 1, .25, 3)*frame.scale());
            pose.scale(scale, -scale, scale);
            context.submitNodeCollector().submitText(pose, -client.font.width(label.text())/2f, 0, label.text().getVisualOrderText(),
                ConfigManager.damageSplashShadow, Font.DisplayMode.NORMAL, 0xF000F0, ((int) (frame.alpha()*255) << 24) | 0xFFFFFF, 0, 0);
        }
    }
}
