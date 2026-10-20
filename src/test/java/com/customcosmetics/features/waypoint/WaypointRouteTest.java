// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.customcosmetics.features.waypoint.WaypointData.*;

class WaypointRouteTest {
    private final List<Point> points = List.of(new Point("A", 0, 70, 0, 1, true),
            new Point("B", 10, 70, 0, 2, true), new Point("C", 20, 70, 0, 3, true),
            new Point("D", 30, 70, 0, 4, true));
    @Test void manualNavigationWrapsAndResetReturnsFirst() {
        var route = new WaypointRoute();
        route.action("previous", 4); assertEquals(3, route.current());
        route.action("next", 4); assertEquals(0, route.current());
        route.action("next", 4); route.action("first", 4); assertEquals(0, route.current());
        route.action("next", 4); route.reset(); assertEquals(0, route.current());
    }
    @Test void proximityAdvancesOnlyToTheNextPointAndDoesNotSkipAcrossTheMap() {
        var route = new WaypointRoute();
        route.tick(points, 20.5, 70.5, .5); assertEquals(0, route.current());
        route.tick(points, 10.5, 73, .5); assertEquals(0, route.current());
        route.tick(points, 10.5, 70.5, .5); assertEquals(1, route.current());
        assertEquals(-1, route.relation(0, 4));
        assertEquals(0, route.relation(1, 4));
        assertEquals(1, route.relation(2, 4));
        assertEquals(2, route.relation(3, 4));
    }
    @Test void deletingRoutePointsCannotLeaveAnInvalidIndex() {
        var route = new WaypointRoute();
        route.action("previous", 4);
        route.tick(points.subList(0, 1), 100, 70, 100);
        assertEquals(0, route.current());
        route.tick(List.of(), 0, 0, 0);
        assertEquals(0, route.current());
    }
    @Test void skyblockerSkippingAndBacktrackingOptionsControlProximity() {
        var route = new WaypointRoute();
        route.tick(points, 20.5, 70.5, .5, 2, true, false);
        assertEquals(0, route.current(), "Disallowing backwards also prevents jumping past the next point");
        route.tick(points, 20.5, 70.5, .5, 2, true, true);
        assertEquals(2, route.current());
        route.tick(points, 10.5, 70.5, .5, 2, false, false);
        assertEquals(2, route.current());
        route.tick(points, 10.5, 70.5, .5, 2, false, true);
        assertEquals(1, route.current());
    }
    @Test void skyblockWaypointsFollowIslandAcrossServerInstances() {
        assertEquals("hypixel:hub", hypixelScope("hub", "mini1"));
        assertEquals("hypixel:dynamic", hypixelScope("dynamic", "mini1"));
        assertEquals("hypixel:crystal_hollows", hypixelScope("crystal_hollows", ""));
        assertEquals("hypixel:unknown", hypixelScope("", "mini1"));
    }
}
