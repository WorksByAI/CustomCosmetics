// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.items.ItemNameText;
import com.customcosmetics.utils.MessageUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public final class ModModeration {
    private static final Set<String> COMMANDS = Set.of("csbkick", "csbban", "csbunban");
    private static Connection observedConnection;
    private static ClientPacketListener listener;
    private static String connectionId, scope;
    private static long nextPoll;
    private static boolean polling, administrator;
    private static Pending pending;
    private static BanScreen disconnectScreen;
    private static final String LINK_TOKEN = "\u0001";

    private record Pending(String id, long due, BanScreen screen, String base, long generation) {}

    public record BanScreen(Component title, Component body, Component button) {
        public DisconnectedScreen create(Screen parent) {
            return new DisconnectedScreen(parent == null ? new TitleScreen() : parent, title, body, button);
        }
    }

    private record Ban(String id, String reason, String helpUrl, boolean permanent, boolean pending,
                       long seconds, long remaining, JsonObject design) {
        static Ban read(JsonObject response) {
            if (!response.has("ban") || !response.get("ban").isJsonObject()) return null;
            var ban = response.getAsJsonObject("ban");
            boolean permanent = ban.get("permanent").getAsBoolean();
            return new Ban(text(ban, "id"), text(ban, "reason"), text(ban, "helpUrl"), permanent,
                ban.get("pending").getAsBoolean(), permanent ? 0 : ban.get("seconds").getAsLong(),
                permanent ? 0 : ban.get("remainingSeconds").getAsLong(),
                ban.has("screen") && ban.get("screen").isJsonObject() ? ban.getAsJsonObject("screen") : new JsonObject());
        }

        BanScreen screen(long remainingSeconds) {
            String title = text(design, "title"), body = text(design, "body"), button = text(design, "button");
            if (body.isBlank()) {
                body = (permanent ? "&cYour account has been blocked."
                    : "&cYou are temporarily banned for &f{time} &cfrom this server!")
                    + "\n\n&7Reason: &f{reason}\n&7Find out more: &b&n{link}\n\n&7"
                    + (permanent ? "Block" : "Ban") + " ID: &f#{id}\n&7Sharing your Ban ID may affect the processing of your "
                    + (permanent ? "case!" : "appeal!");
            }
            return new BanScreen(formatted(title.isBlank() ? "Connection Lost" : title, remainingSeconds),
                formatted(body, remainingSeconds),
                formatted(button.isBlank() ? "Back to Server List" : button, remainingSeconds));
        }

        private Component formatted(String template, long seconds) {
            URI link = null;
            try {
                if (helpUrl.startsWith("https://")) link = URI.create(helpUrl);
            } catch (IllegalArgumentException ignored) { /* A malformed URL cannot be opened. */ }
            if (link == null) template = template.lines().filter(line -> !line.contains("{link}"))
                .collect(java.util.stream.Collectors.joining("\n"));
            var parsed = ItemNameText.parse(template.replace("{time}", duration(seconds)).replace("{id}", id)
                .replace("{link}", LINK_TOKEN).replace("{reason}", reason));
            if (link == null) return parsed;
            var click = new ClickEvent.OpenUrl(link);
            var result = Component.empty();
            parsed.visit((style, part) -> {
                String[] segments = part.split(LINK_TOKEN, -1);
                for (int i = 0; i < segments.length; i++) {
                    if (i != 0) result.append(Component.literal(helpUrl).setStyle(style.withClickEvent(click)));
                    if (!segments[i].isEmpty()) result.append(Component.literal(segments[i]).setStyle(style));
                }
                return Optional.empty();
            }, Style.EMPTY);
            return result;
        }
    }

    private ModModeration() {}

    public static void init() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(ModModeration::handleCommand);
    }

    public static boolean administrator() {
        return administrator && RemoteAccess.online();
    }

    public static void tick(Minecraft client) {
        var current = client.getConnection();
        var server = client.getCurrentServer();
        var connection = current == null ? null : current.getConnection();
        long now = System.nanoTime();
        if (!RemoteAccess.online() || client.hasSingleplayerServer()) {
            reset();
            return;
        }
        if (server == null || connection == null) {
            // Limbo can reconfigure the same network connection without a LocalPlayer for a few ticks.
            if (observedConnection != null && observedConnection.isConnected() && pending != null) {
                if (now >= pending.due()) beginActivation(observedConnection);
            } else reset();
            return;
        }
        if (connection != observedConnection) {
            reset();
            observedConnection = connection;
            connectionId = UUID.randomUUID().toString();
            scope = scope(ServerAddress.parseString(server.ip));
        }
        listener = current;
        if (pending != null && now >= pending.due()) {
            beginActivation(connection);
            return;
        }
        if (polling || pending != null || now < nextPoll) return;
        polling = true;
        String base = PresenceSync.serviceBaseUrl();
        long generation = RemoteAccess.generation();
        var request = scoped(scope);
        request.addProperty("connection", connectionId);
        var location = PresenceWorld.gameLocation(client);
        if (location != null) {
            var reported = new JsonObject();
            reported.addProperty("serverId", location.serverId());
            reported.addProperty("area", location.area());
            request.add("location", reported);
        }
        request(base, generation, "poll", request).whenComplete((result, error) -> client.execute(() -> {
            if (observedConnection != connection) return;
            polling = false;
            nextPoll = System.nanoTime() + TimeUnit.SECONDS.toNanos(error == null ? 4 : 15);
            if (error != null || !currentService(base, generation) || listener == null || client.getConnection() != listener) return;
            administrator = result.get("administrator").getAsBoolean();
            var ban = Ban.read(result);
            if (ban != null) {
                if (ban.pending()) {
                    // This is the only game command a moderation message can trigger.
                    if (PresenceWorld.isHypixel(client)) listener.sendCommand("limbo");
                    pending = new Pending(ban.id(), System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                        ban.screen(Math.max(0, ban.seconds() - 1)), base, generation);
                } else disconnectBan(connection, ban.screen(ban.remaining()));
            } else if (result.has("kick") && result.get("kick").isJsonObject()) {
                String reason = text(result.getAsJsonObject("kick"), "reason");
                disconnect(kickMessage(reason));
            }
        }));
    }

    private static void beginActivation(Connection connection) {
        var action = pending;
        pending = null;
        if (!currentService(action.base(), action.generation()) || !connection.isConnected()) return;
        var body = scoped(scope);
        body.addProperty("id", action.id());
        request(action.base(), action.generation(), "activate", body);
        disconnectBan(connection, action.screen());
    }

    private static void disconnectBan(Connection connection, BanScreen screen) {
        disconnectScreen = screen;
        connection.disconnect(screen.body());
    }

    public static BanScreen takeBanScreen(Component reason) {
        var screen = disconnectScreen;
        if (screen == null || screen.body() != reason) return null;
        disconnectScreen = null;
        return screen;
    }

    private static Component kickMessage(String reason) {
        return reason.isBlank() ? Component.literal("Disconnected") : ItemNameText.parse(reason);
    }

    private static void disconnect(Component message) {
        if (listener != null) listener.getConnection().disconnect(message);
    }

    private static void reset() {
        observedConnection = null;
        listener = null;
        connectionId = scope = null;
        pending = null;
        nextPoll = 0;
        polling = administrator = false;
    }

    public static void beforeConnect(Minecraft client, Screen connecting, Screen parent, ServerAddress address, Runnable proceed) {
        if (!RemoteAccess.online()) {
            proceed.run();
            return;
        }
        String base = PresenceSync.serviceBaseUrl();
        long generation = RemoteAccess.generation();
        request(base, generation, "check", scoped(scope(address))).orTimeout(6, TimeUnit.SECONDS)
            .whenComplete((result, error) -> client.execute(() -> {
                if (ClientUiAccess.screen(client) != connecting) return;
                var ban = error == null && currentService(base, generation) ? Ban.read(result) : null;
                if (ban != null) ClientUiAccess.setScreen(client, ban.screen(ban.remaining()).create(parent));
                else proceed.run();
            }));
    }

    private static boolean handleCommand(String command) {
        String[] parts = command.strip().split("\\s+", 2);
        if (!COMMANDS.contains(parts[0])) return true;
        if (!administrator() || scope == null) {
            unknownCommand(parts[0]);
            return false;
        }
        if (parts.length == 2) {
            var options = parts[1].split("\\s+", 2);
            if (options[0].matches("[A-Za-z0-9_]{3,16}")) {
                executeCommand(parts[0], options[0], options.length == 1 ? "" : options[1]);
                return false;
            }
        }
        notice(switch (parts[0]) {
            case "csbkick" -> "Usage: /csbkick <player> [reason]";
            case "csbunban" -> "Usage: /csbunban <player> [ban ID]";
            default -> "Usage: /csbban <player> <template> [time] [ban ID]";
        });
        return false;
    }

    private static void executeCommand(String command, String name, String arguments) {
        var body = scoped(scope);
        body.addProperty("action", command.substring(3));
        body.addProperty("name", name);
        if (command.equals("csbkick")) body.addProperty("reason", arguments);
        else if (command.equals("csbunban") && !arguments.isBlank()) body.addProperty("id", arguments);
        else if (command.equals("csbban")) {
            String[] fields = arguments.isBlank() ? new String[0] : arguments.split("\\s+");
            if (fields.length == 0 || fields[0].equalsIgnoreCase("templates")) {
                service("templates", new JsonObject()).whenComplete((result, error) -> {
                    if (error != null) { commandError(command, error); return; }
                    var keys = new java.util.ArrayList<String>();
                    for (var template : result.getAsJsonArray("templates")) keys.add(text(template.getAsJsonObject(), "key"));
                    notice("Ban templates: " + String.join(", ", keys));
                });
                return;
            }
            if (fields.length > 3) { notice("Usage: /csbban <player> <template> [time] [ban ID]"); return; }
            body.addProperty("template", fields[0].toLowerCase(Locale.ROOT));
            if (fields.length >= 2) {
                if (fields.length == 2 && fields[1].matches("[A-Za-z0-9]{8}") && !fields[1].matches("(?i)(?:[0-9]+(?:min|sec|[dhms]))+"))
                    body.addProperty("id", fields[1]);
                else body.addProperty("time", fields[1]);
            }
            if (fields.length == 3) body.addProperty("id", fields[2]);
        }
        service("action", body).whenComplete((result, error) -> {
            if (error != null) commandError(command, error);
            else if (command.equals("csbban")) notice("Restricted " + text(result, "target") + " · #" + text(result, "id"));
            else if (command.equals("csbunban")) notice(result.get("removed").getAsInt() > 0
                ? "Removed the restriction for " + text(result, "target") : "No matching restriction.");
            else notice("Kick requested for " + text(result, "target"));
        });
    }

    private static CompletableFuture<JsonObject> service(String operation, JsonObject body) {
        return request(PresenceSync.serviceBaseUrl(), RemoteAccess.generation(), operation, body);
    }

    private static CompletableFuture<JsonObject> request(String base, long generation, String operation, JsonObject body) {
        return PresenceSync.authenticatedSession().thenApplyAsync(identity -> {
            HttpURLConnection connection = null;
            try {
                if (!currentService(base, generation)) throw new IOException("Moderation request cancelled");
                connection = PresenceSync.openActionConnection(base + "/mod/moderation/" + operation);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + identity.token());
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (var output = connection.getOutputStream()) { output.write(data); }
                int status = connection.getResponseCode();
                try (var input = status == 200 ? connection.getInputStream() : connection.getErrorStream()) {
                    if (input == null) throw new IOException("Moderation service unavailable");
                    var result = JsonParser.parseString(new String(input.readNBytes(65537), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (status != 200) throw new IOException(text(result, "error"));
                    return result;
                }
            } catch (Exception error) { throw new CompletionException(error); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    private static boolean currentService(String base, long generation) {
        return RemoteAccess.current(generation) && base.equals(PresenceSync.serviceBaseUrl());
    }

    private static JsonObject scoped(String scope) {
        var result = new JsonObject();
        result.addProperty("scope", scope);
        return result;
    }

    static String scope(ServerAddress address) {
        String host = address.getHost().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        if (address.getPort() == 25565 && (host.equals("hypixel.net") || host.endsWith(".hypixel.net"))) host = "hypixel.net";
        String canonical = host + ":" + address.getPort();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unreachable) { throw new IllegalStateException(unreachable); }
    }

    static String duration(long value) {
        long[] scales = {86400, 3600, 60, 1};
        String[] suffixes = {"d", "h", "m", "s"};
        var result = new StringBuilder();
        for (int i = 0; i < scales.length; i++) {
            long part = value / scales[i];
            if (part != 0 || !result.isEmpty() || i == 3) {
                if (!result.isEmpty()) result.append(' ');
                result.append(part).append(suffixes[i]);
            }
            value %= scales[i];
        }
        return result.toString();
    }

    private static String text(JsonObject body, String key) {
        return body.has(key) && body.get(key).isJsonPrimitive() ? body.get(key).getAsString() : "";
    }

    private static void unknownCommand(String command) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) ClientUiAccess.chat(client).addClientSystemMessage(
                Component.literal("Unknown command. Type \"help\" for help. ('" + command + "')"));
        });
    }

    private static void commandError(String command, Throwable failure) {
        Throwable error = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if ("mod_admin_required".equals(error.getMessage())) unknownCommand(command);
        else notice(errorText(error));
    }

    private static String errorText(Throwable error) {
        return switch (String.valueOf(error.getMessage())) {
            case "minecraft_profile_not_found" -> "Minecraft account not found.";
            case "moderation_target_offline" -> "That player is not active in CustomSkyBlock.";
            case "invalid_ban_duration" -> "Invalid time. Examples: 30d, 1min, 29d59m58s.";
            case "invalid_ban_template" -> "Unknown template. Use /csbban <player> templates.";
            case "moderation_already_banned" -> "That player already has an active restriction.";
            case "invalid_ban_id", "ban_id_in_use" -> "Ban ID must be an unused eight-character alphanumeric ID.";
            default -> "Moderation request failed.";
        };
    }

    private static void notice(String message) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) ClientUiAccess.chat(client).addClientSystemMessage(
                MessageUtils.createMessageWithPrefix(Component.literal(message)));
        });
    }
}
