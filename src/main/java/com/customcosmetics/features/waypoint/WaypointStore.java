// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.*;
import static com.customcosmetics.features.waypoint.WaypointData.*;

/** Never replace an unreadable source with a default empty snapshot. */
public final class WaypointStore {
    private final Path path;
    private boolean writable;
    public WaypointStore(Path path) { this.path = path; }
    public Snapshot load() throws IOException {
        writable = false;
        if (Files.notExists(path)) { writable = true; return Snapshot.empty(); }
        try {
            byte[] bytes;
            try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(WaypointCodec.MAX_BYTES + 1); }
            if (bytes.length > WaypointCodec.MAX_BYTES) throw new IOException("Waypoint file exceeds 2 MiB.");
            Snapshot value = WaypointCodec.decode(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            writable = true;
            return value;
        } catch (RuntimeException bad) {
            throw new IOException("Waypoint file is invalid; the original has been preserved.", bad);
        }
    }
    public boolean writable() { return writable; }
    public void save(Snapshot snapshot) throws IOException {
        if (!writable) throw new IOException("Waypoint file is read-only after a load failure. Repair it and restart.");
        byte[] bytes = WaypointCodec.encode(snapshot).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > WaypointCodec.MAX_BYTES) throw new IOException("Waypoint file exceeds 2 MiB.");
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
