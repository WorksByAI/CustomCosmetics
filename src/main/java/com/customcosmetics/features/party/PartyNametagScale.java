// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

/** Compensates for the shortened render anchor without changing a teammate's real position. */
public final class PartyNametagScale {
    private PartyNametagScale() {}

    public static String mode(String value) {
        return "distance".equals(value) ? "distance" : "fixed";
    }

    public static double size(double value) {
        return Double.isFinite(value) ? Math.clamp(value, .5, 3) : 1;
    }

    public static float scale(String mode, double size, double distance, double renderedDistance) {
        double anchor = Math.max(.01, renderedDistance);
        double reference = mode(mode).equals("fixed") ? 8 : Math.max(.01, distance);
        return (float) (.025 * size(size) * anchor / reference);
    }
}
