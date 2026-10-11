// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Opening, clicking, typing and scrolling count as activity; drawing and closing do not. */
public final class ScreenSession {
    public static final long IDLE_MILLIS = 60_000;
    public record Position(String page, double scroll) {}
    private Position position = new Position("SMOL",0);
    private long lastInteraction = Long.MIN_VALUE;
    public Position open(long now) {
        if (lastInteraction == Long.MIN_VALUE || now - lastInteraction >= IDLE_MILLIS) position = new Position("SMOL",0);
        lastInteraction = now;
        return position;
    }
    public void interact(String page, double scroll, long now) { position = new Position(page,Math.max(0,scroll)); lastInteraction = now; }
    public void remember(String page, double scroll) { position = new Position(page,Math.max(0,scroll)); }
    public void touch(long now) { lastInteraction = now; }
}
