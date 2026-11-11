// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.RemoteServerConfig;
import com.customcosmetics.gui.UiFonts;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Short-lived authorization for the official service; it is never saved to disk. */
public final class ExperimentalAccess {
    public enum Status { UNCHECKED, CHECKING, GRANTED, DENIED, UNAVAILABLE }
    private static final String ORIGIN = "https://" + RemoteServerConfig.DEFAULT.host() + ":" + RemoteServerConfig.DEFAULT.port();
    private static final long POLL = Duration.ofMinutes(1).toNanos();
    private static final long LEASE = Duration.ofSeconds(75).toNanos();
    private static final long RETRY = Duration.ofSeconds(15).toNanos();

    private static volatile Status status = Status.UNCHECKED;
    private static UUID account;
    private static boolean checking;
    private static long nextCheck, deadline, request;

    private ExperimentalAccess() {}

    public static boolean allowed() { return status == Status.GRANTED && RemoteAccess.online(); }
    public static Status status() { return status; }

    public static void refresh(Minecraft client) {
        ConfigManager.checkExperimentalAccess = true;
        ConfigManager.requestSave();
        if (!checking) nextCheck = 0;
        tick(client);
    }

    public static void tick(Minecraft client) {
        if (!ConfigManager.checkExperimentalAccess) return;
        var current = client.getUser().getProfileId();
        if (!RemoteAccess.online() || !ORIGIN.equals(PresenceSync.serviceBaseUrl())) {
            if (status != Status.UNAVAILABLE || checking) reset(client, Status.UNAVAILABLE);
            return;
        }
        if (!current.equals(account)) {
            reset(client, Status.UNCHECKED);
            account = current;
        }
        long now = System.nanoTime();
        if (status == Status.GRANTED && now - deadline >= 0) set(client, Status.UNAVAILABLE);
        if (checking || nextCheck != 0 && now - nextCheck < 0) return;
        checking = true;
        if (!allowed()) set(client, Status.CHECKING);
        long id = ++request;
        long network = RemoteAccess.generation();
        PresenceSync.authenticatedSession().thenApplyAsync(ExperimentalAccess::check)
            .whenComplete((result, error) -> client.execute(() -> {
                if (id != request) return;
                checking = false;
                if (!RemoteAccess.current(network) || !ORIGIN.equals(PresenceSync.serviceBaseUrl())
                        || !current.equals(client.getUser().getProfileId())) {
                    reset(client, Status.UNAVAILABLE);
                    return;
                }
                long finished = System.nanoTime();
                var outcome = error == null ? result : Status.UNAVAILABLE;
                deadline = outcome == Status.GRANTED ? finished + LEASE : 0;
                nextCheck = finished + (outcome == Status.UNAVAILABLE ? RETRY : POLL);
                set(client, outcome);
            }));
    }

    private static void reset(Minecraft client, Status next) {
        request++;
        account = null;
        checking = false;
        nextCheck = deadline = 0;
        set(client, next);
    }

    private static void set(Minecraft client, Status next) {
        var previous = status;
        status = next;
        if (previous == Status.GRANTED && next != previous) UiFonts.clear();
        if (next == Status.GRANTED && previous != next) UiFonts.restore(client);
    }

    private static Status check(PresenceAuthClient.Session session) {
        if (!ORIGIN.equals(session.baseUrl())) return Status.UNAVAILABLE;
        try {
            var connection = PresenceSync.openActionConnection(ORIGIN + "/auth/experimental");
            try {
                connection.setRequestProperty("Authorization", "Bearer " + session.token());
                if (connection.getResponseCode() != 200) return Status.UNAVAILABLE;
                try (var stream = connection.getInputStream()) {
                    var bytes = stream.readNBytes(1025);
                    if (bytes.length > 1024) return Status.UNAVAILABLE;
                    var value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                        .getAsJsonObject().get("allowed");
                    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                        return Status.UNAVAILABLE;
                    }
                    return value.getAsBoolean() ? Status.GRANTED : Status.DENIED;
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception unavailable) {
            return Status.UNAVAILABLE;
        }
    }
}
