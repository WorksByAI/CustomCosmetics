// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Nebulune TrevorHelper, Copyright (c) 2025 Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.waypoint.Waypoints;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class TrevorHelper {
    private static final Pattern HUNT = Pattern.compile("^\\[NPC] Trevor: You can find your (\\w+) animal near the .+");
    private static final String COMPLETE = "Return to the Trapper soon to get a new animal to hunt!";
    private static final String ACCEPT = "Accept the trapper's task to hunt the animal?";
    private static long cooldown, callAt, acceptAt, nextMayorCheck;
    private static Rarity rarity;
    private static Boolean derpy;
    private static String acceptCommand;
    private static int displayed;
    private static Component hud;

    private TrevorHelper() {}

    public enum Rarity {
        TRACKABLE("Trackable Color", 100, 0xCDD6F4), UNTRACKABLE("Untrackable Color", 500, 0xA6E3A1),
        UNDETECTED("Undetected Color", 1000, 0x89B4FA), ENDANGERED("Endangered Color", 5000, 0xCBA6F7),
        ELUSIVE("Elusive Color", 10000, 0xF9E2AF);

        public final String label;
        final int health;
        private final int defaultColor;

        Rarity(String label, int health, int defaultColor) {
            this.label = label;
            this.health = health;
            this.defaultColor = defaultColor;
        }

        public int color() {
            var value = ConfigManager.trevorColors.get(name());
            return value == null ? defaultColor : value & 0xFFFFFF;
        }
    }

    private static boolean active() {
        return GameplayModule.TREVOR_HELPER.enabled() && "farming_1".equals(Waypoints.currentIslandMode());
    }

    static void message(Component message, String text) {
        if (!active()) return;
        long now = System.nanoTime();
        if (text.startsWith("[NPC] Trevor: ")) {
            var match = HUNT.matcher(text);
            if (match.matches()) {
                try { rarity = Rarity.valueOf(match.group(1).toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException unknown) { return; }
                cooldown = now + 20_000_000_000L;
                callAt = acceptAt = 0;
                acceptCommand = null;
            }
        } else if (COMPLETE.equals(text)) {
            rarity = null;
            if (ConfigManager.trevorAutoCall) {
                long early = (long) (ConfigManager.trevorCallEarly * 1_000_000_000L);
                callAt = now + Math.max(0, cooldown - now - early) + delay(ConfigManager.trevorCallDelay);
            }
        } else if (ConfigManager.trevorAutoAccept && text.stripLeading().startsWith(ACCEPT)) {
            var siblings = message.getSiblings();
            if (siblings.size() > 3 && siblings.get(3).getStyle().getClickEvent() instanceof ClickEvent.RunCommand click
                    && click.command().startsWith("/") && click.command().length() > 1) {
                acceptCommand = click.command().substring(1);
                acceptAt = now + delay(ConfigManager.trevorAcceptDelay);
            }
        }
    }

    static void tick(Minecraft client) {
        if (cooldown == 0 && callAt == 0 && acceptAt == 0 && rarity == null) return;
        if (!active()) { reset(); return; }
        long now = System.nanoTime();
        if (rarity != null && ConfigManager.trevorEsp && !ConfigManager.offlineMode && now - nextMayorCheck >= 0) {
            fetchMayor(client, now);
        }
        if (callAt != 0 && now - callAt >= 0) {
            callAt = 0;
            if (ConfigManager.trevorAutoCall && client.getConnection() != null) client.getConnection().sendCommand("call trevor");
        }
        if (acceptAt != 0 && now - acceptAt >= 0) {
            acceptAt = 0;
            if (ConfigManager.trevorAutoAccept && client.getConnection() != null) client.getConnection().sendCommand(acceptCommand);
            acceptCommand = null;
        }
        if (cooldown == 0) return;
        int remaining = (int) Math.max(0, (cooldown - now + 99_999_999L) / 100_000_000L);
        if (remaining == 0) {
            cooldown = 0;
            hud = null;
            if (ConfigManager.trevorCooldownAlert) ClientUiAccess.title(client,
                Component.literal(ConfigManager.trevorAlertMessage).withStyle(ChatFormatting.RED), Component.empty());
            String sound = ConfigManager.trevorAlertSound;
            if (sound != null && !sound.isBlank()) {
                var id = Identifier.tryParse(sound);
                if (id != null) client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1, 1));
            }
        } else if (remaining != displayed && ConfigManager.trevorCooldownTimer) {
            displayed = remaining;
            hud = cooldownLabel(remaining);
        }
    }

    static void reset() {
        cooldown = callAt = acceptAt = 0;
        rarity = null;
        acceptCommand = null;
        hud = null;
        displayed = 0;
    }

    static Component hud() { return ConfigManager.trevorCooldownTimer && GameplayModule.TREVOR_HELPER.enabled() ? hud : null; }
    static Rarity animal() { return ConfigManager.trevorEsp && GameplayModule.TREVOR_HELPER.enabled() ? rarity : null; }

    static boolean matches(Rarity target, float health) {
        return Boolean.TRUE.equals(derpy) ? health == target.health * 2
            : health == target.health || derpy == null && health == target.health * 2;
    }

    static Component cooldownLabel(int tenths) {
        return Component.literal("Cooldown: ").append(Component.literal(tenths / 10 + "." + tenths % 10 + "s").withStyle(ChatFormatting.RED));
    }

    private static long delay(int ticks) { return (ticks + ThreadLocalRandom.current().nextInt(3)) * 50_000_000L; }

    private static void fetchMayor(Minecraft client, long now) {
        nextMayorCheck = now + Duration.ofMinutes(5).toNanos();
        var request = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/resources/skyblock/election"))
            .timeout(Duration.ofSeconds(8)).build();
        MayorClient.HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() == 200) {
                var mayor = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("mayor");
                if (mayor != null && mayor.has("name")) {
                    boolean active = "Derpy".equalsIgnoreCase(mayor.get("name").getAsString());
                    client.execute(() -> derpy = active);
                }
            }
        }).exceptionally(failure -> null);
    }

    private static final class MayorClient {
        private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
}
