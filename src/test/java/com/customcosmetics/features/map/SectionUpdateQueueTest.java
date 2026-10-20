package com.customcosmetics.features.map;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SectionUpdateQueueTest {
    @Test void batchesRespectCountAndTimeBudgetsWithoutLosingOrRepeatingSections() {
        var queue = new SectionUpdateQueue();
        var world = new Object();
        queue.context(world);
        queue.add(world, LongArrayList.of(1, 2, 2, 3, 4, 5));
        var visited = new ArrayList<Long>();
        assertEquals(2, queue.drain(2, 100, () -> 0, visited::add));
        assertEquals(List.of(1L, 2L), visited);
        var clock = new AtomicLong();
        assertEquals(1, queue.drain(64, 10, clock::get, value -> { visited.add(value); clock.addAndGet(10); }));
        assertEquals(2, queue.drain(64, 100, () -> 0, visited::add));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), visited);
        assertEquals(0, queue.size());
    }
    @Test void delayedOldWorldResultsAreDiscardedButSameWorldDisableCanFinishCleanup() {
        var queue = new SectionUpdateQueue();
        var oldWorld = new Object();
        var nextWorld = new Object();
        queue.context(oldWorld);
        queue.add(oldWorld, LongArrayList.of(1));
        queue.context(oldWorld);
        assertEquals(1, queue.size());
        queue.context(nextWorld);
        queue.add(oldWorld, LongArrayList.of(2, 3));
        assertEquals(0, queue.size());
        queue.add(nextWorld, LongArrayList.of(4));
        queue.context(null);
        assertEquals(0, queue.drain(64, 100, () -> 0, value -> fail("Old world leaked")));
    }
    @Test void idleQueueDoesNotEvenReadTheClock() {
        var queue = new SectionUpdateQueue();
        assertEquals(0, queue.drain(64, 100, () -> { throw new AssertionError(); }, value -> fail()));
    }
    @Test void aLargeBatchIsStagedGraduallyInsideTheTimeBudget() {
        var queue = new SectionUpdateQueue();
        var world = new Object();
        var reads = new AtomicLong();
        queue.context(world);
        queue.add(world, new it.unimi.dsi.fastutil.longs.AbstractLongCollection() {
            public int size() { return 1_000_000; }
            public it.unimi.dsi.fastutil.longs.LongIterator iterator() {
                return new it.unimi.dsi.fastutil.longs.LongIterator() {
                    private long next;
                    public boolean hasNext() { return next < size(); }
                    public long nextLong() { reads.incrementAndGet(); return next++; }
                };
            }
        });
        assertEquals(0, reads.get(), "Enqueue must not traverse a million section IDs");
        var visited = new ArrayList<Long>();
        queue.drain(64, 16, reads::get, visited::add);
        assertEquals(16, reads.get(), "Staging itself must yield when its time budget expires");
        assertEquals(List.of(0L), visited);
        assertEquals(999_999, queue.size());
        queue.context(null);
        assertEquals(0, queue.size());
    }
}
