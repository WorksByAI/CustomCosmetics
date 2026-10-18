package com.customcosmetics.sync;

import java.util.EnumMap;

/** Warn once after three consecutive rate-limited refreshes of the same operation. */
public final class RateLimitNotice {
    private static final int FAILURE_THRESHOLD = 3;
    private final EnumMap<StartupSyncNotice.Operation, Integer> failures = new EnumMap<>(StartupSyncNotice.Operation.class);

    public synchronized boolean complete(StartupSyncNotice.Operation operation, boolean success, SyncFailure failure) {
        if (success || failure == null || !failure.rateLimited()) {
            failures.remove(operation);
            return false;
        }
        int count = Math.min(FAILURE_THRESHOLD + 1, failures.getOrDefault(operation, 0) + 1);
        failures.put(operation, count);
        return count == FAILURE_THRESHOLD;
    }

    public synchronized void reset() { failures.clear(); }
}
