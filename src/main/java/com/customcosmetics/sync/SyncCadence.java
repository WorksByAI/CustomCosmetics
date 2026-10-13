package com.customcosmetics.sync;

/** Tick-driven scheduling: both clients keep reading, even when their own settings have not changed. */
public final class SyncCadence {
    public static final long MIN_POLL_MS = 30_000;
    public static final long MAX_POLL_MS = 7_200_000;
    public static final long HEARTBEAT_MS = 60_000;
    private static final long MANUAL_COOLDOWN_NANOS = 30_000_000_000L;
    private long lastRead, nextCheck, nextHeartbeat, lastManualNanos;
    private boolean readStarted, manualStarted;
    public record Due(boolean read, boolean checkWrite, boolean heartbeat) {}
    public Due tick(long now, boolean active, long pollMs) {
        if (!active) return new Due(false, false, false);
        boolean read = !readStarted || now - lastRead >= Math.clamp(pollMs, MIN_POLL_MS, MAX_POLL_MS);
        boolean check = now >= nextCheck;
        boolean heartbeat = now >= nextHeartbeat;
        if (read) noteRead(now);
        if (check) nextCheck = now + 250;
        if (heartbeat) nextHeartbeat = now + HEARTBEAT_MS;
        return new Due(read, check, heartbeat);
    }
    public void noteRead(long now) { lastRead = now; readStarted = true; }

    public synchronized boolean tryManualSync(long nowNanos) {
        if (manualCooldownSeconds(nowNanos) > 0) return false;
        lastManualNanos = nowNanos;
        manualStarted = true;
        return true;
    }

    public synchronized int manualCooldownSeconds(long nowNanos) {
        if (!manualStarted) return 0;
        long remaining = MANUAL_COOLDOWN_NANOS - (nowNanos - lastManualNanos);
        return remaining <= 0 ? 0 : (int) ((remaining + 999_999_999L) / 1_000_000_000L);
    }

    // Leaving the world, switching servers or toggling sync never resets manual cooldown.
    public void reset() { readStarted = false; lastRead = nextCheck = nextHeartbeat = 0; }
}
