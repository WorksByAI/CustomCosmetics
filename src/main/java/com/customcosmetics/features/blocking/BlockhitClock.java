// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

/** A repeating render clock, independent of Minecraft's attacks and item-use state. */
public final class BlockhitClock {
    private int elapsed = -1, duration = 6;
    private float previous;
    public void tick(boolean held, int ticks) {
        if (!held) { elapsed = -1; previous = 0; return; }
        previous = elapsed < 0 ? 0 : phase(elapsed, duration);
        duration = Math.max(1, ticks);
        // Rapid repeats restart after the downswing, rather than wait for a full recovery.
        // This reproduces the visual timing only; it never invokes the player's swing method.
        int cycle = Math.max(1, duration / 2) + recoveryTicks(duration);
        elapsed = elapsed < 0 || elapsed >= cycle - 1 ? 0 : elapsed + 1;
    }
    private static int recoveryTicks(int duration) { return Math.max(1, Math.round(duration / 6f)); }
    private static float phase(int elapsed, int duration) {
        int forward = Math.max(1, duration / 2);
        float turn = Math.min(1, forward / (float)duration);
        return elapsed <= forward ? elapsed / (float)duration
            : turn + (1 - turn) * (elapsed - forward) / recoveryTicks(duration);
    }
    public float progress(float partial) {
        if (elapsed < 0) return 0;
        float current = phase(elapsed, duration), delta = current - previous;
        if (delta < 0) delta += 1;
        return Math.clamp(previous + delta * Math.clamp(partial, 0, 1), 0, 1);
    }
}
