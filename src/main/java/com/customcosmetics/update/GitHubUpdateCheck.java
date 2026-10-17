// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

/** One anonymous background check per game process, with one notice held until a world is ready. */
public final class GitHubUpdateCheck {
    public static final URI API = URI.create("https://api.github.com/repos/WorksByAI/CustomCosmetics/releases/latest");
    public static final URI RELEASE_PAGE = URI.create("https://github.com/WorksByAI/CustomCosmetics/releases/latest");
    private static final int MAX_RESPONSE = 65_536;
    private final String installed;
    private final BooleanSupplier enabled;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<String> pending = new AtomicReference<>();

    public GitHubUpdateCheck(String installed) { this(installed,()->true); }
    public GitHubUpdateCheck(String installed,BooleanSupplier enabled) { this.installed = installed;this.enabled=enabled; }
    public String installed() { return installed; }
    public void start() {
        start(() -> {
            try { return fetch(API, installed); }
            catch (IOException | RuntimeException unavailable) { return Optional.empty(); }
        }, ForkJoinPool.commonPool());
    }
    void start(Supplier<Optional<String>> fetch, Executor executor) {
        if (!enabled.getAsBoolean() || !started.compareAndSet(false, true)) return;
        executor.execute(() -> {
            if(!enabled.getAsBoolean()){started.set(false);return;}
            try { fetch.get().filter(value->enabled.getAsBoolean()).ifPresent(pending::set); }
            catch (RuntimeException unavailable) { /* An update check must never interrupt the game. */ }
            finally{if(!enabled.getAsBoolean())started.set(false);}
        });
    }
    public String poll(boolean worldReady) { if(!enabled.getAsBoolean()){pending.set(null);return null;}return worldReady ? pending.getAndSet(null) : null; }

    static Optional<String> fetch(URI endpoint, String installed) throws IOException {
        HttpURLConnection connection = com.customcosmetics.sync.RemoteAccess.manage((HttpURLConnection) endpoint.toURL().openConnection());
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
        connection.setRequestProperty("User-Agent", "CustomCosmetics-UpdateChecker");
        try {
            if (connection.getResponseCode() != 200) return Optional.empty();
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAX_RESPONSE + 1);
                if (bytes.length > MAX_RESPONSE) return Optional.empty();
                return newerRelease(new String(bytes, StandardCharsets.UTF_8), installed);
            }
        } finally { connection.disconnect(); }
    }
    static Optional<String> newerRelease(String json, String installed) {
        try {
            var release = JsonParser.parseString(json).getAsJsonObject();
            for (String flag : new String[]{"draft", "prerelease"}) {
                var value = release.get(flag);
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean() || value.getAsBoolean())
                    return Optional.empty();
            }
            var tag = release.get("tag_name");
            if (tag == null || !tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) return Optional.empty();
            String latest = normalize(tag.getAsString());
            String current = normalize(installed);
            if (latest == null || current == null) return Optional.empty();
            var target = SemanticVersion.parse(latest);
            if (target.getPrereleaseKey().isPresent() || target.compareTo(SemanticVersion.parse(current)) <= 0)
                return Optional.empty();
            return Optional.of(latest);
        } catch (RuntimeException | VersionParsingException malformed) { return Optional.empty(); }
    }
    private static String normalize(String version) {
        if (version == null || version.length() > 64) return null;
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        return value.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?") ? value : null;
    }
}
