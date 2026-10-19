// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.damage;

/** A small shared palette keeps animated world labels free of per-glyph color allocations. */
public final class DamageColors {
    public static final int PALETTE_SIZE = 256;
    private static final int[] RAINBOW = new int[PALETTE_SIZE];
    static {
        for (int i = 0; i < PALETTE_SIZE; i++) RAINBOW[i] = net.minecraft.util.Mth.hsvToRgb(i / (float) PALETTE_SIZE, .85f, 1f) & 0xFFFFFF;
    }
    private DamageColors() {}
    public static String normalize(String mode) { return "rainbow".equalsIgnoreCase(mode) ? "rainbow" : "gradient"; }
    public static double speed(double speed) { return DamageAnimation.bounded(speed, 1, 0, 4); }
    public static int phase(double seconds, double speed) {
        double bounded = speed(speed);
        if (bounded == 0 || !Double.isFinite(seconds)) return 0;
        double period = 4 / bounded;
        return (int) Math.floor((seconds % period) / period * PALETTE_SIZE) & (PALETTE_SIZE - 1);
    }
    public static int index(int character, int count, int phase) {
        return (int) (phase + (long) character * PALETTE_SIZE / Math.max(1, count)) & (PALETTE_SIZE - 1);
    }
    public static int rgb(int index) { return RAINBOW[index & (PALETTE_SIZE - 1)]; }
}
