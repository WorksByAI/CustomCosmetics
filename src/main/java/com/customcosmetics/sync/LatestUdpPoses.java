// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.blocking.BlockHand;
import java.util.*;

/** Separate latest-state streams per player: reordering one player's batch cannot discard another's update. */
public final class LatestUdpPoses {
    private final Map<UUID, Long> sequences = new HashMap<>();
    private final Map<UUID, BlockingTransport.Pose> poses = new HashMap<>();
    public Map<UUID, BlockingTransport.Pose> accept(long sequence, Map<UUID, BlockingTransport.Pose> updates, long now) {
        for (var entry : updates.entrySet()) {
            if (sequence <= sequences.getOrDefault(entry.getKey(), 0L)) continue;
            sequences.put(entry.getKey(), sequence);
            if (entry.getValue().hand() == BlockHand.NONE) poses.remove(entry.getKey());
            else poses.put(entry.getKey(), entry.getValue());
        }
        poses.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        return Map.copyOf(poses);
    }
    public void clear() { sequences.clear(); poses.clear(); }
}
