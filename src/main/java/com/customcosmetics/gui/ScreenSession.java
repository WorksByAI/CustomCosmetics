// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Opening, clicking, typing and scrolling count as activity; drawing and closing do not. */
public final class ScreenSession {
    public static final long IDLE_MILLIS = 60_000;
    public record Position(String page, double scroll) {}
    private final String initialPage;
    private Position position;
    private long lastInteraction = Long.MIN_VALUE;
    public ScreenSession() { this("MAIN"); }
    public ScreenSession(String initialPage) {
        this.initialPage = initialPage;
        position = new Position(initialPage, 0);
    }
    public Position open(long now) {
        if (expired(now)) position = new Position(initialPage,0);
        lastInteraction = now;
        return position;
    }
    boolean expired(long now) { return lastInteraction == Long.MIN_VALUE || now - lastInteraction >= IDLE_MILLIS; }
    public void interact(String page, double scroll, long now) { position = new Position(page,Math.max(0,scroll)); lastInteraction = now; }
    public void remember(String page, double scroll) { position = new Position(page,Math.max(0,scroll)); }
    public void touch(long now) { lastInteraction = now; }
}
