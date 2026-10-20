// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.List;
import static com.customcosmetics.features.waypoint.WaypointData.*;

/** Explicit next/previous navigation and proximity advance without skipping a route. */
public final class WaypointRoute {
    private int current;
    public int current() { return current; }
    public void reset() { current = 0; }
    public void action(String action, int count) {
        if (count == 0) { reset(); return; }
        current = switch (action) {
            case "next" -> Math.floorMod(current + 1, count);
            case "previous" -> Math.floorMod(current - 1, count);
            case "first", "reset" -> 0;
            default -> throw new IllegalArgumentException("Unknown route action.");
        };
    }
    public void tick(List<Point> points, double x, double y, double z) {
        tick(points, x, y, z, 1.5);
    }
    public void tick(List<Point> points, double x, double y, double z, double radius) {
        tick(points, x, y, z, radius, false, false);
    }
    public void tick(List<Point> points, double x, double y, double z, double radius, boolean allowSkip, boolean allowBackwards) {
        if (current >= points.size()) current = 0;
        if (points.size() < 2) return;
        int next = (current + 1) % points.size();
        double radiusSquared = radius * radius;
        if (!allowSkip && !allowBackwards) {
            if (points.get(next).enabled() && points.get(next).distanceSquared(x, y, z) <= radiusSquared) current = next;
            return;
        }
        int previous = Math.floorMod(current - 1, points.size());
        for (int i = 0; i < points.size(); i++) {
            if ((!allowSkip && i != next && i != previous) || (!allowBackwards && i != next)) continue;
            Point point = points.get(i);
            if (point.enabled() && point.distanceSquared(x, y, z) <= radiusSquared) current = i;
        }
    }
    public int relation(int index, int count) {
        if (index == current) return 0;
        if (index == (current + 1) % count) return 1;
        if (index == Math.floorMod(current - 1, count)) return -1;
        return 2;
    }
}
