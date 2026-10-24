// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.config;

import com.customcosmetics.sync.SyncEndpoint;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.LoggerFactory;

/** Empty values use the bundled service; filled values are validated and applied together. */
public final class RemoteServerConfig {
    public record Endpoint(String host, int port, boolean https, boolean sni, String udpHost, int udpPort, String transport) {}
    public static final Endpoint DEFAULT = new Endpoint("cc.toryhi.com", 443, true, true, "cc.toryhi.com", 25578, "auto");
    private static Path file;
    private static String previousText;
    private static long nextCheck;
    private static final java.util.concurrent.ExecutorService READER = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task,"CustomCosmetics-config-reader");thread.setDaemon(true);return thread;
    });
    private record Read(String text, Endpoint endpoint, boolean invalid) {}
    private static java.util.concurrent.CompletableFuture<Read> pending;
    private RemoteServerConfig() {}

    public static synchronized void init(Path path) {
        file = path;
        previousText = null;
        apply(DEFAULT);
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path, template(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException error) { LoggerFactory.getLogger("CustomCosmetics/RemoteServer").warn("Cannot create custom remote server file"); }
        reload(true);
    }
    public static String template() {
        var json = new JsonObject();
        for (String key : new String[]{"syncServerIp", "syncServerPort", "syncServerHttps", "syncServerSni", "syncUdpHost", "syncUdpPort", "syncBlockingTransport"})
            json.addProperty(key, "");
        return new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n";
    }
    public static Endpoint parse(String text) {
        var element = text == null || text.isBlank() ? new JsonObject() : JsonParser.parseString(text);
        if (!element.isJsonObject()) throw new IllegalArgumentException("Remote server configuration must be an object");
        var json = element.getAsJsonObject();
        String host = host(json, "syncServerIp", DEFAULT.host());
        int port = port(json, "syncServerPort", DEFAULT.port());
        boolean https = flag(json, "syncServerHttps", DEFAULT.https());
        boolean sni = flag(json, "syncServerSni", true);
        String transport = value(json, "syncBlockingTransport").toLowerCase(java.util.Locale.ROOT);
        if (transport.isEmpty()) transport = DEFAULT.transport();
        if (!transport.equals("wss") && !transport.equals("udp") && !transport.equals("auto")) throw new IllegalArgumentException("Invalid blocking transport");
        return new Endpoint(host, port, https, sni, host(json, "syncUdpHost", DEFAULT.udpHost()), port(json, "syncUdpPort", DEFAULT.udpPort()), transport);
    }
    private static String value(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || value.isJsonNull()) return "";
        if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Invalid remote server setting");
        return value.getAsString().strip();
    }
    private static String host(JsonObject json, String key, String fallback) {
        String raw = value(json,key);
        if (raw.isEmpty()) return fallback;
        String host = SyncEndpoint.normalizeHost(raw);
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("Invalid remote server host");
        return host;
    }
    private static int port(JsonObject json, String key, int fallback) {
        String raw = value(json,key);
        if (raw.isEmpty()) return fallback;
        int port = Integer.parseInt(raw);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid remote server port");
        return port;
    }
    private static boolean flag(JsonObject json, String key, boolean fallback) {
        String raw = value(json,key);
        if (raw.isEmpty()) return fallback;
        if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) throw new IllegalArgumentException("Invalid remote server flag");
        return Boolean.parseBoolean(raw);
    }
    public static synchronized boolean reload(boolean force) {
        if (file == null) return false;
        if (force) {
            if (pending != null) { pending.cancel(false);pending=null; }
            nextCheck=System.nanoTime()+1_000_000_000L;
            return accept(read(file,previousText));
        }
        // Apply completed immutable settings on the client thread, but never wait for filesystem I/O.
        if (pending != null) {
            if (!pending.isDone()) return false;
            var result=pending.join();pending=null;
            return accept(result);
        }
        if (System.nanoTime()<nextCheck) return false;
        nextCheck=System.nanoTime()+1_000_000_000L;
        Path path=file;String previous=previousText;
        pending=java.util.concurrent.CompletableFuture.supplyAsync(()->read(path,previous),READER);
        return false;
    }
    private static Read read(Path path,String previous) {
        try {
            String text = Files.exists(path) ? Files.readString(path,StandardCharsets.UTF_8) : "";
            if (Objects.equals(text,previous)) return new Read(text,null,false);
            try { return new Read(text,parse(text),false); }
            catch (RuntimeException malformed) { return new Read(text,null,true); }
        } catch (java.io.IOException | RuntimeException error) {
            return new Read(previous,null,true);
        }
    }
    private static boolean accept(Read result) {
        previousText=result.text();
        if (result.invalid()) {
            LoggerFactory.getLogger("CustomCosmetics/RemoteServer").warn("Invalid custom remote server file; keeping the last valid connection settings");
            return false;
        }
        if (result.endpoint()==null) return false;
        Endpoint old=current();apply(result.endpoint());return !result.endpoint().equals(old);
    }
    public static Endpoint current() { return new Endpoint(ConfigManager.syncServerIp,ConfigManager.syncServerPort,ConfigManager.syncServerHttps,
        ConfigManager.syncServerSni,ConfigManager.syncUdpHost,ConfigManager.syncUdpPort,ConfigManager.syncBlockingTransport); }
    private static void apply(Endpoint endpoint) {
        ConfigManager.syncServerIp = endpoint.host(); ConfigManager.syncServerPort = endpoint.port();
        ConfigManager.syncServerHttps = endpoint.https(); ConfigManager.syncServerSni = endpoint.sni();
        ConfigManager.syncUdpHost = endpoint.udpHost(); ConfigManager.syncUdpPort = endpoint.udpPort();
        ConfigManager.syncBlockingTransport = endpoint.transport();
    }
}
