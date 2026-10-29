// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HeadSkinRepositoryTest {
    static final String FIRST = "ce44f9094c01997ee6433f39bafe8d9d67f1adc48548f6ec87dd20ba506c74ff";
    static final String SECOND = "dc325030df0efc64deb0fdf674f67f95b6c5c93bb839bd33714db055062ff3c4";
    @TempDir Path directory;
    private static String property(String hash) { return HeadTexture.property(hash); }
    private static String animation() {
        return "{\"skins\":{\"GOLDOR_CELESTIAL\":{\"ticks\":3,\"textures\":[\"" + UUID.randomUUID() + ":" + property(FIRST)
            + "\",\"" + UUID.randomUUID() + ":" + property(SECOND) + "\"]}}}";
    }
    private static byte[] archive() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            var item = new com.google.gson.JsonObject(); item.addProperty("itemid", "minecraft:skull");
            item.addProperty("displayname", "§dCelestial Goldor's Helmet Skin");
            item.addProperty("nbttag", "{SkullOwner:{Properties:{textures:[{Value:\""+property(FIRST)+"\"}]}}}");
            put(zip, "repo/items/GOLDOR_CELESTIAL.json", item.toString());
            put(zip, "repo/items/BAD.json", "{\"itemid\":\"minecraft:skull\"}");
            put(zip, "repo/ignored/items/NOT_A_REAL_ITEM.json", item.toString());
            put(zip, "repo/constants/animatedskulls.json", animation());
        }
        return bytes.toByteArray();
    }
    private static void put(ZipOutputStream zip, String path, String data) throws IOException {
        zip.putNextEntry(new ZipEntry(path)); zip.write(data.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static String api() { return "{\"success\":true,\"items\":[{\"id\":\"NEW_SKIN\",\"name\":\"New Skin\",\"material\":\"SKULL_ITEM\",\"skin\":\""+SECOND+"\"}]}"; }
    @Test void repositoryAddsMissingSkinAndDistinctAnimatedVariantWithCorrectTickCadence() throws Exception {
        var data = HeadSkinCatalog.parseRepository(new ByteArrayInputStream(archive()));
        var combined = HeadSkinCatalog.combine(data, HeadSkinCatalog.parse(api()));
        assertEquals(Set.of("GOLDOR_CELESTIAL", "NEW_SKIN", "animated:GOLDOR_CELESTIAL"), new HashSet<>(combined.entries().stream().map(HeadSkinCatalog.Entry::id).toList()));
        assertEquals("Celestial Goldor's Helmet Skin", combined.entries().getFirst().name());
        var animation = combined.animations().get("GOLDOR_CELESTIAL");
        assertArrayEquals(new int[]{0,0,0,1,1,1,0}, new int[]{animation.frame(0),animation.frame(1),animation.frame(2),animation.frame(3),animation.frame(4),animation.frame(5),animation.frame(6)});
        assertEquals(data, HeadSkinCatalog.cached(HeadSkinCatalog.cache(data)));
        assertThrows(UnsupportedOperationException.class, () -> animation.frames().add(FIRST));
    }
    @Test void everyLaunchChecksBothSourcesEvenWithAFreshCacheAndUsesHttpValidators() throws Exception {
        var requests = new AtomicInteger(); var repo = archive();
        var first = new HeadSkinRepository(directory, (uri, etag) -> {
            requests.incrementAndGet(); assertEquals("", etag);
            return new HeadSkinRepository.Response(200, "\"revision-1\"", new ByteArrayInputStream(uri.equals(HeadSkinRepository.ITEMS) ? api().getBytes(StandardCharsets.UTF_8) : repo));
        }).load(false, ignored -> {});
        assertTrue(first.failures().isEmpty()); assertEquals(2, requests.get());
        var publications = new ArrayList<HeadSkinCatalog.Data>();
        var second = new HeadSkinRepository(directory, (uri, etag) -> {
            requests.incrementAndGet(); assertEquals("\"revision-1\"", etag);
            return new HeadSkinRepository.Response(304, "", InputStream.nullInputStream());
        }).load(false, publications::add);
        assertEquals(4, requests.get()); assertEquals(first.data(), second.data());
        assertEquals(first.data(), publications.getFirst()); assertTrue(second.failures().isEmpty());
        var failed = new HeadSkinRepository(directory, (uri, etag) -> { throw new IOException("offline"); }).load(false, ignored -> {});
        assertEquals(second.data(), failed.data()); assertEquals(2, failed.failures().size());
        var offline = new HeadSkinRepository(directory, (uri, etag) -> { fail("Offline mode contacted the network"); return null; }).load(true, ignored -> {});
        assertEquals(second.data(), offline.data());
    }
    @Test void brokenRefreshDoesNotReplaceValidCacheAndOtherSourceStillUpdates() throws Exception {
        byte[] repo = archive();
        var original = new HeadSkinRepository(directory, (uri, etag) -> new HeadSkinRepository.Response(200, "v1", new ByteArrayInputStream(uri.equals(HeadSkinRepository.ITEMS) ? api().getBytes(StandardCharsets.UTF_8) : repo))).load(false, ignored -> {});
        var refreshed = new HeadSkinRepository(directory, (uri, etag) -> new HeadSkinRepository.Response(200, "v2", new ByteArrayInputStream(uri.equals(HeadSkinRepository.ITEMS) ? api().replace("NEW_SKIN", "NEWER_SKIN").getBytes(StandardCharsets.UTF_8) : "not a zip".getBytes(StandardCharsets.UTF_8)))).load(false, ignored -> {});
        assertEquals(original.data().animations(), refreshed.data().animations());
        assertTrue(refreshed.data().entries().stream().anyMatch(e -> e.id().equals("NEWER_SKIN")));
        assertEquals(1, refreshed.failures().size());
        assertTrue(Files.readString(directory.resolve("head-skins-neu.json")).contains("v1"));
    }
    @Test void malformedAnimationsAreIsolatedAndNonpositiveTicksAreClamped() {
        String good = animation();
        var json = com.google.gson.JsonParser.parseString(good).getAsJsonObject();
        var skins = json.getAsJsonObject("skins");
        skins.getAsJsonObject("GOLDOR_CELESTIAL").addProperty("ticks", 0);
        skins.add("EMPTY", com.google.gson.JsonParser.parseString("{\"ticks\":1,\"textures\":[]}"));
        skins.add("INVALID", com.google.gson.JsonParser.parseString("{\"ticks\":1,\"textures\":[\"bad\"]}"));
        assertEquals(Set.of("GOLDOR_CELESTIAL"), HeadSkinCatalog.parseAnimations(json.toString()).keySet());
        assertEquals(1, HeadSkinCatalog.parseAnimations(json.toString()).get("GOLDOR_CELESTIAL").ticks());
    }
    @Test void animatedSelectionPersistsWithFirstFrameFallbackWithoutAnyRepository() throws Exception {
        String selection = "animated:GOLDOR_CELESTIAL:" + FIRST;
        assertEquals(selection, HeadTexture.normalize(selection));
        assertEquals(FIRST, HeadTexture.normalize(HeadTexture.property(selection)));
        assertEquals("GOLDOR_CELESTIAL", HeadTexture.animationId(selection));
        var store = new ItemAppearanceStore(directory.resolve("appearances.json"));
        String uuid = "12345678-1234-1234-1234-123456789abc";
        store.put(uuid, new ItemAppearanceStore.Appearance("", "", "", null, selection)); store.save();
        var loaded = new ItemAppearanceStore(directory.resolve("appearances.json")); loaded.load();
        assertEquals(selection, loaded.get(uuid).headTexture());
        for (String malformed : List.of("animated:GOLDOR_CELESTIAL:", "animated:../invalid:"+FIRST, "animated:GOLDOR_CELESTIAL:not-a-texture"))
            assertThrows(IllegalArgumentException.class, () -> HeadTexture.normalize(malformed));
    }
}
