// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayDeque;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Client-thread render invalidations, deduplicated and discarded on world changes. */
final class SectionUpdateQueue {
    private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
    private final ArrayDeque<LongIterator> batches = new ArrayDeque<>();
    private long unstaged;
    private Object world;

    void context(Object next) {
        if (world != next) { pending.clear(); batches.clear(); unstaged = 0; world = next; }
    }
    /** Transfers a stable batch; indexing it must not monopolize the main thread either. */
    void add(Object source, LongCollection sections) {
        if (source != null && source == world && !sections.isEmpty()) {
            batches.add(sections.iterator()); unstaged += sections.size();
        }
    }
    long size() { return pending.size() + unstaged; }
    int drain(int maximum, long budgetNanos, LongSupplier clock, LongConsumer update) {
        if (size() == 0 || maximum <= 0 || budgetNanos <= 0) return 0;
        long start = clock.getAsLong();
        int count = 0;
        do {
            if (pending.isEmpty()) {
                int staged = 0;
                while (!batches.isEmpty() && staged < 64) {
                    var batch = batches.peek();
                    if (!batch.hasNext()) { batches.remove(); continue; }
                    pending.add(batch.nextLong()); unstaged--; staged++;
                    if ((staged & 15) == 0 && clock.getAsLong() - start >= budgetNanos) break;
                }
            }
            if (pending.isEmpty()) break;
            update.accept(pending.removeFirstLong());
            count++;
        } while (size() != 0 && count < maximum && clock.getAsLong() - start < budgetNanos);
        if (size() == 0) batches.clear();
        return count;
    }
}
