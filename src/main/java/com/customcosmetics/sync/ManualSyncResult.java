package com.customcosmetics.sync;

import java.util.EnumMap;

/** A manual result waits for both download and upload, including already running requests. */
public final class ManualSyncResult {
    public record Result(boolean success, SyncFailure failure) {}
    private final EnumMap<StartupSyncNotice.Operation, Result> results = new EnumMap<>(StartupSyncNotice.Operation.class);
    private long endpointVersion;
    private boolean pending;
    public synchronized void begin(long version) {
        endpointVersion = version;
        pending = true;
        results.clear();
    }
    public synchronized void cancel() { pending = false; results.clear(); }
    public synchronized boolean needs(long version, StartupSyncNotice.Operation operation) {
        return pending && version == endpointVersion && !results.containsKey(operation);
    }
    public synchronized Result complete(long version, StartupSyncNotice.Operation operation, boolean success, SyncFailure failure) {
        if (!pending || version != endpointVersion) return null;
        results.putIfAbsent(operation, new Result(success, failure));
        if (results.size() != StartupSyncNotice.Operation.values().length) return null;
        pending = false;
        // A transient 429 must not hide an actionable error from the other operation.
        for (var part : results.values()) if (!part.success() && (part.failure() == null || !part.failure().rateLimited())) return part;
        for (var part : results.values()) if (!part.success()) return part;
        return new Result(true, null);
    }
}
