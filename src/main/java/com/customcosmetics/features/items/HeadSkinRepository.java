// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonParser;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Worker-thread metadata refresh. HTTP validators save bandwidth, never skip a launch's check. */
public final class HeadSkinRepository {
    public static final URI REPOSITORY = URI.create("https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master");
    public static final URI ITEMS = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
    private static final int MAX_CACHE = 16 * 1024 * 1024;
    private final Path directory;
    private final Fetcher fetcher;
    @FunctionalInterface interface Fetcher { Response get(URI uri, String etag) throws Exception; }
    record Response(int status, String etag, InputStream body) implements AutoCloseable {
        @Override public void close() throws IOException { body.close(); }
    }
    public record Result(HeadSkinCatalog.Data data, List<String> failures) {}
    private record Cached(HeadSkinCatalog.Data data, String etag) {}
    public HeadSkinRepository(Path directory) {
        this.directory = directory;
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
        fetcher = (uri, etag) -> {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40)).header("User-Agent", "CustomCosmetics-head-skins");
            if (!etag.isEmpty()) request.header("If-None-Match", etag);
            var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            return new Response(response.statusCode(), response.headers().firstValue("ETag").orElse(""), response.body());
        };
    }
    HeadSkinRepository(Path directory, Fetcher fetcher) { this.directory = directory; this.fetcher = fetcher; }
    public Result load(boolean offline, Consumer<HeadSkinCatalog.Data> publish) {
        var failures = new ArrayList<String>();
        Cached repository = cached("head-skins-neu.json"), api = cached("head-skins-api.json");
        if (api.data().entries().isEmpty()) try {
            Path legacy = directory.resolve("head-skins.json");
            if (Files.isRegularFile(legacy)) api = new Cached(new HeadSkinCatalog.Data(HeadSkinCatalog.parse(read(legacy)), Map.of()), "");
        } catch (Exception ignored) { /* Invalid legacy cache does not prevent refreshing. */ }
        publish.accept(HeadSkinCatalog.combine(repository.data(), api.data().entries()));
        if (!offline) {
            // Publish the much smaller API first; the complete repository can arrive independently later.
            api = refresh(ITEMS, "head-skins-api.json", api, false, failures);
            publish.accept(HeadSkinCatalog.combine(repository.data(), api.data().entries()));
            repository = refresh(REPOSITORY, "head-skins-neu.json", repository, true, failures);
        }
        var data = HeadSkinCatalog.combine(repository.data(), api.data().entries());
        publish.accept(data);
        return new Result(data, List.copyOf(failures));
    }
    private Cached cached(String name) {
        try {
            var json = read(directory.resolve(name));
            var root = JsonParser.parseString(json).getAsJsonObject();
            var data = HeadSkinCatalog.cached(json);
            String etag = root.has("_etag") ? root.get("_etag").getAsString() : "";
            if (etag.length() > 256 || etag.codePoints().anyMatch(Character::isISOControl)) etag = "";
            return new Cached(data, data.entries().isEmpty() ? "" : etag);
        } catch (Exception invalidOrMissing) { return new Cached(HeadSkinCatalog.EMPTY, ""); }
    }
    private Cached refresh(URI uri, String name, Cached previous, boolean repository, List<String> failures) {
        try (var response = fetcher.get(uri, previous.etag())) {
            if (response.status() == 304 && !previous.data().entries().isEmpty()) return previous;
            if (response.status() != 200) throw new IOException("HTTP " + response.status());
            HeadSkinCatalog.Data data;
            if (repository) data = HeadSkinCatalog.parseRepository(new LimitedInput(response.body(), 64L * 1024 * 1024));
            else {
                byte[] bytes = response.body().readNBytes(MAX_CACHE + 1);
                if (bytes.length > MAX_CACHE) throw new IOException("Head item feed is too large.");
                data = new HeadSkinCatalog.Data(HeadSkinCatalog.parse(new String(bytes, StandardCharsets.UTF_8)), Map.of());
            }
            if (data.entries().isEmpty()) throw new IOException("Head item feed is empty.");
            var loaded = new Cached(data, response.etag());
            try { save(name, loaded); }
            catch (IOException cacheFailure) { failures.add(name + " cache: " + cacheFailure.getMessage()); }
            return loaded;
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            failures.add(name + ": " + error.getMessage()); return previous;
        }
    }
    private static String read(Path path) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(MAX_CACHE + 1);
            if (bytes.length > MAX_CACHE) throw new IOException("Head cache is too large.");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    private void save(String name, Cached cached) throws IOException {
        var root = JsonParser.parseString(HeadSkinCatalog.cache(cached.data())).getAsJsonObject();
        root.addProperty("_etag", cached.etag());
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "head-skins-", ".tmp");
        try {
            Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static final class LimitedInput extends FilterInputStream {
        private long remaining;
        LimitedInput(InputStream input, long limit) { super(input); remaining = limit; }
        @Override public int read() throws IOException { int result = in.read(); if (result >= 0 && --remaining < 0) throw new IOException("Head download is too large."); return result; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int count = in.read(b, off, (int) Math.min(len, remaining + 1));
            if (count > 0 && (remaining -= count) < 0) throw new IOException("Head download is too large."); return count;
        }
    }
}
