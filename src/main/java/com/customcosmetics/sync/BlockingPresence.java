// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.features.blocking.BlockHand;
import java.util.Set;
import java.util.UUID;
interface BlockingPresence extends AutoCloseable {
    void update(String world, Set<UUID> players, BlockHand hand, String itemId);
    void setPose(BlockHand hand, String itemId);
    boolean closed();
    @Override void close();
}
