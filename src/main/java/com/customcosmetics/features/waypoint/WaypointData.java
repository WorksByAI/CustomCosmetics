// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshots keep disk writes and rendering independent from GUI edits. */
public final class WaypointData {
    public static final int MAX_GROUPS = 128, MAX_POINTS = 4096;
    private WaypointData() {}

    public record Point(String name, int x, int y, int z, int color, boolean enabled) {
        public Point {
            name = text(name, 80, "Waypoint name");
            if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || y < -2048 || y > 2048)
                throw new IllegalArgumentException("Coordinates are outside the supported world range.");
            color &= 0xffffff;
        }
        public Point enabled(boolean value) { return new Point(name, x, y, z, color, value); }
        public double distanceSquared(double px, double py, double pz) {
            double dx = x + .5 - px, dy = y + .5 - py, dz = z + .5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Group(String id, String name, String scope, boolean enabled, boolean ordered,
                        boolean throughWalls, List<Point> points) {
        public Group {
            UUID.fromString(Objects.requireNonNull(id));
            name = text(name, 80, "Group name");
            scope = text(scope, 512, "World / area");
            points = List.copyOf(points);
            if (points.size() > MAX_POINTS) throw new IllegalArgumentException("Too many waypoints.");
        }
        public static Group create(String name, String scope) {
            return new Group(UUID.randomUUID().toString(), name, scope, true, false, true, List.of());
        }
        public Group points(List<Point> value) { return new Group(id, name, scope, enabled, ordered, throughWalls, value); }
        public Group enabled(boolean value) { return new Group(id, name, scope, value, ordered, throughWalls, points); }
    }

    public record Snapshot(boolean enabled, List<Group> groups) {
        public Snapshot {
            groups = List.copyOf(groups);
            if (groups.size() > MAX_GROUPS) throw new IllegalArgumentException("At most 128 groups are supported.");
            int count = 0;
            var ids = new HashSet<String>();
            for (Group group : groups) {
                if (!ids.add(group.id())) throw new IllegalArgumentException("Duplicate group identifier.");
                count += group.points().size();
            }
            if (count > MAX_POINTS) throw new IllegalArgumentException("At most 4096 waypoints are supported.");
        }
        public static Snapshot empty() { return new Snapshot(true, List.of()); }
    }

    static String text(String value, int limit, String label) {
        if (value == null || value.isBlank() || value.length() > limit || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + " must contain 1–" + limit + " visible characters.");
        return value.strip();
    }

    public static String hypixelScope(String island, String instance) {
        return "hypixel:" + SkyblockIslands.normalize(island);
    }

    /** Existing instance-bound waypoint files remain usable after switching to island scopes. */
    public static String normalizeScope(String scope) {
        if (scope.startsWith("hypixel:")) {
            String id = scope.substring(8).split(":", 2)[0];
            return hypixelScope(id, "");
        }
        return scope;
    }
}
