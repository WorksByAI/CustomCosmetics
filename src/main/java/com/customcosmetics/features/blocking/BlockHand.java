// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

public enum BlockHand {
    NONE("none"), MAIN("main"), OFF("off");
    public final String wire;
    BlockHand(String wire) { this.wire = wire; }
    public static BlockHand fromWire(String value) {
        return switch (value) { case "main" -> MAIN; case "off" -> OFF; default -> NONE; };
    }
    public static BlockHand choose(boolean enabled, boolean inWorld, boolean menuOpen, boolean focused,
                                   boolean alive, boolean spectator, boolean useDown, boolean usingOtherItem,
                                   boolean mainSword, boolean offSword) {
        if (!enabled || !inWorld || menuOpen || !focused || !alive || spectator || !useDown || usingOtherItem) return NONE;
        return mainSword ? MAIN : offSword ? OFF : NONE;
    }
}
