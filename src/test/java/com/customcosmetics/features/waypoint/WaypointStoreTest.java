// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.customcosmetics.features.waypoint.WaypointData.*;

class WaypointStoreTest {
    @TempDir Path directory;
    @Test void corruptOrFutureFilesCannotBeOverwrittenByAnEmptyDefault() throws Exception {
        Path path = directory.resolve("waypoints.json");
        String bad = "{\"version\":99,\"groups\":[]}";
        Files.writeString(path, bad);
        var store = new WaypointStore(path);
        assertThrows(IOException.class, store::load);
        assertFalse(store.writable());
        assertThrows(IOException.class, () -> store.save(Snapshot.empty()));
        assertEquals(bad, Files.readString(path));
    }
    @Test void saveRoundTripAtomicallyReplacesOnlyAValidLoadedStore() throws Exception {
        Path path = directory.resolve("customcosmetics/waypoints.json");
        var store = new WaypointStore(path);
        assertEquals(Snapshot.empty(), store.load());
        Snapshot value = new Snapshot(false, List.of(Group.create("Home", "local:world|overworld")
                .points(List.of(new Point("Door", 4, 65, -8, 0xff0088, true)))));
        store.save(value);
        assertEquals(value, new WaypointStore(path).load());
        assertFalse(Files.exists(path.resolveSibling("waypoints.json.tmp")));
    }
    @Test void overLimitFileStaysReadOnly() throws Exception {
        Path path = directory.resolve("waypoints.json");
        Files.write(path, new byte[WaypointCodec.MAX_BYTES + 1]);
        var store = new WaypointStore(path);
        assertThrows(IOException.class, store::load);
        assertFalse(store.writable());
    }
    @Test void invalidUtf8IsNotSilentlyReplacedAndSaved() throws Exception {
        Path path = directory.resolve("waypoints.json");
        byte[] bytes = new byte[]{(byte) 0xc3, (byte) 0x28};
        Files.write(path, bytes);
        var store = new WaypointStore(path);
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(Snapshot.empty()));
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }
    @Test void failedTemporaryWriteLeavesTheLastSuccessfulSnapshotIntact() throws Exception {
        Path path = directory.resolve("waypoints.json");
        var store = new WaypointStore(path); store.load();
        Snapshot saved = new Snapshot(true, List.of(Group.create("Keep me", "hypixel:hub")));
        store.save(saved);
        Files.createDirectory(path.resolveSibling("waypoints.json.tmp"));
        assertThrows(IOException.class, () -> store.save(Snapshot.empty()));
        assertEquals(saved, new WaypointStore(path).load());
    }
}
