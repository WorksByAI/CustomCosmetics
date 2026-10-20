// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.shortcuts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShortcutStoreTest {
    @TempDir Path directory;
    private ShortcutStore.Data example() {
        return new ShortcutStore.Data(false, List.of(new ShortcutRules.Entry("test", ShortcutRules.Kind.ALIAS, "myhome", "/warp home", true)));
    }
    @Test void roundTripKeepsDisabledEntriesAndMasterSwitch() throws Exception {
        var file = directory.resolve("shortcuts.json");
        var store = new ShortcutStore(file);
        assertEquals(List.of(), store.load().entries());
        store.save(example());
        assertEquals(example(), store.load());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void corruptFileCannotBeOverwrittenUntilSuccessfulReload() throws Exception {
        var file = directory.resolve("shortcuts.json");
        String original = "{\"version\":1,\"entries\":";
        Files.writeString(file, original);
        var store = new ShortcutStore(file);
        assertThrows(IOException.class, store::load);
        assertFalse(store.writable());
        assertThrows(IOException.class, () -> store.save(example()));
        assertEquals(original, Files.readString(file));
        Files.writeString(file, "{\"version\":1,\"enabled\":true,\"entries\":[]}");
        store.load(); store.save(example());
        assertEquals(example(), store.load());
    }
    @Test void unsupportedVersionAndInvalidTypesAreProtected() throws Exception {
        for (String content : List.of("{\"version\":2,\"enabled\":true,\"entries\":[]}",
            "{\"version\":1.2,\"enabled\":true,\"entries\":[]}",
            "{\"version\":1,\"enabled\":\"true\",\"entries\":[]}")) {
            var file = directory.resolve("shortcuts.json");
            Files.writeString(file, content);
            var store = new ShortcutStore(file);
            assertThrows(IOException.class, store::load);
            assertThrows(IOException.class, () -> store.save(example()));
            assertEquals(content, Files.readString(file));
        }
    }
    @Test void oversizedConfigurationIsProtectedBeforeParsing() throws Exception {
        var file = directory.resolve("shortcuts.json"); Files.writeString(file, " ".repeat(512 * 1024 + 1));
        var store = new ShortcutStore(file);
        assertThrows(IOException.class, store::load);
    }
}
