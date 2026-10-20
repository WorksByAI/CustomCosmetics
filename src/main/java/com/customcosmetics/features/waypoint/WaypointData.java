// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable snapshots keep disk writes and rendering independent from GUI edits. */
public final class WaypointData {
    public static final int MAX_GROUPS = 128, MAX_POINTS = 4096;
    private WaypointData() {}

    public record Point(String name, int x, int y, int z, int color, boolean enabled, double alpha) {
        public Point {
            name = text(name, 80, "Waypoint name");
            if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || y < -2048 || y > 2048)
                throw new IllegalArgumentException("Coordinates are outside the supported world range.");
            color &= 0xffffff;
            if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1)
                throw new IllegalArgumentException("Waypoint alpha must be between 0 and 1.");
        }
        public Point(String name, int x, int y, int z, int color, boolean enabled) {
            this(name, x, y, z, color, enabled, .5);
        }
        public Point enabled(boolean value) { return new Point(name, x, y, z, color, value, alpha); }
        public double distanceSquared(double px, double py, double pz) {
            double dx = x + .5 - px, dy = y + .5 - py, dz = z + .5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Group(String id, String name, String scope, boolean enabled, boolean ordered,
                        boolean throughWalls, List<Point> points, String style) {
        public Group {
            UUID.fromString(Objects.requireNonNull(id));
            name = text(name, 80, "Group name");
            scope = text(scope, 512, "World / area");
            points = List.copyOf(points);
            style = normalizeStyle(style);
            if (points.size() > MAX_POINTS) throw new IllegalArgumentException("Too many waypoints.");
        }
        public Group(String id, String name, String scope, boolean enabled, boolean ordered,
                     boolean throughWalls, List<Point> points) {
            this(id, name, scope, enabled, ordered, throughWalls, points, "waypoint");
        }
        public static Group create(String name, String scope) {
            return new Group(UUID.randomUUID().toString(), name, scope, true, false, true, List.of());
        }
        public Group points(List<Point> value) { return new Group(id, name, scope, enabled, ordered, throughWalls, value, style); }
        public Group enabled(boolean value) { return new Group(id, name, scope, value, ordered, throughWalls, points, style); }
    }

    public static final List<String> STYLES = List.of("waypoint", "outlined_waypoint", "highlight", "outlined_highlight", "outline");
    public static String normalizeStyle(String style) {
        return STYLES.contains(style) ? style : "waypoint";
    }
    public static String styleName(String style) {
        return switch (normalizeStyle(style)) {
            case "outlined_waypoint" -> "Outlined Waypoint";
            case "highlight" -> "Highlight";
            case "outlined_highlight" -> "Outlined Highlight";
            case "outline" -> "Outline";
            default -> "Waypoint";
        };
    }
    /** Check edits and imports without making older files with duplicates unreadable. */
    public static void requireUniqueCoordinates(List<Group> groups) {
        Set<Coordinate> seen = new HashSet<>();
        for (Group group : groups) for (Point point : group.points())
            if (!seen.add(new Coordinate(group.scope(), point.x(), point.y(), point.z())))
                throw new IllegalArgumentException("A waypoint already exists at " + point.x() + ", " + point.y() + ", " + point.z() + " on this island.");
    }
    private record Coordinate(String scope, int x, int y, int z) {}

    public record ImportResult(Snapshot snapshot, int groups, int points, int skipped) {}

    /** Keep existing waypoints and the first imported point at each island coordinate. */
    public static ImportResult mergeImport(Snapshot current, List<Group> imported) {
        Set<Coordinate> seen = new HashSet<>();
        for (Group group : current.groups()) for (Point point : group.points())
            seen.add(new Coordinate(group.scope(), point.x(), point.y(), point.z()));
        var groups = new java.util.ArrayList<>(current.groups());
        int addedGroups = 0, addedPoints = 0, skipped = 0;
        for (Group group : imported) {
            var unique = new java.util.ArrayList<Point>();
            for (Point point : group.points()) {
                if (seen.add(new Coordinate(group.scope(), point.x(), point.y(), point.z()))) unique.add(point);
                else skipped++;
            }
            // A group emptied solely by duplicate points adds nothing useful to the manager.
            if (!group.points().isEmpty() && unique.isEmpty()) continue;
            groups.add(group.points(unique));
            addedGroups++;
            addedPoints += unique.size();
        }
        return new ImportResult(new Snapshot(current.enabled(), groups), addedGroups, addedPoints, skipped);
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
