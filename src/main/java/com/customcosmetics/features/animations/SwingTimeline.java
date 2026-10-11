// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.animations;

/** The native swing state machine used by Noamm's duration hook, kept entirely in render-only state. */
public final class SwingTimeline {
    private boolean swinging;
    private int elapsed, duration = 6;
    private float previous, current;
    public boolean request() { return request(duration); }
    public boolean request(int ticks) {
        // Check the state when vanilla calls swing, before the next animation update.
        // Vanilla/Noamm starts at -1, then reaches zero on the first update.
        if (swinging && elapsed >= 0 && elapsed < Math.max(1, ticks) / 2) return false;
        elapsed = -1;
        swinging = true;
        return true;
    }
    public void clear() { swinging = false; elapsed = 0; previous = current = 0; }
    public void tick(int ticks) {
        previous = current;
        duration = Math.max(1, ticks);
        if (swinging) {
            if (++elapsed >= duration) { elapsed = 0; swinging = false; }
        } else elapsed = 0;
        current = elapsed / (float) duration;
    }
    public float progress(float partial) {
        float difference = current - previous;
        if (difference < 0) difference += 1;
        return previous + difference * Math.clamp(partial, 0, 1);
    }
}
