// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.customcosmetics.features.waypoint.WaypointData.*;

class WaypointCodecTest {
    private static Group example() {
        return new Group(UUID.randomUUID().toString(), "采矿路线", "hypixel:mining_3", true, true, false,
                List.of(new Point("开始", -20, 101, 30, 0x12abff, true), new Point("Second", 50, 120, -30, 0xff5577, false)));
    }
    private static String share(String json) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) { gzip.write(json.getBytes(StandardCharsets.UTF_8)); }
        return WaypointCodec.PREFIX + Base64.getEncoder().encodeToString(out.toByteArray());
    }
    @Test void localRoundTripKeepsIdsScopeGroupAndPointSwitches() {
        Group source = example().enabled(false);
        Snapshot saved = new Snapshot(false, List.of(source));
        assertEquals(saved, WaypointCodec.decode(WaypointCodec.encode(saved)));
        assertTrue(saved.groups().getFirst().points().getFirst().enabled(), "Disabling a group must preserve point settings");
    }
    @Test void waypointStylesSurviveLocalStorageAndSkyblockerShare() throws Exception {
        Group group = new Group(UUID.randomUUID().toString(), "Styled", "hypixel:hub", true, false, true,
                List.of(new Point("A", 1, 70, 2, 0x55ff99, true, .25)), "outlined_highlight");
        assertEquals("outlined_highlight", WaypointCodec.decode(WaypointCodec.encode(new Snapshot(true, List.of(group))))
                .groups().getFirst().style());
        Group imported = WaypointCodec.importGroups(WaypointCodec.exportGroups(List.of(group)), "").getFirst();
        assertEquals("outlined_highlight", imported.style());
        assertEquals(.25, imported.points().getFirst().alpha());
        assertEquals("waypoint", WaypointData.normalizeStyle("future_skyblocker_style"));
    }
    @Test void oneIslandCannotAddTheSameCoordinatesTwice() {
        Point point = new Point("First", 4, 70, -8, 0xff0000, true);
        Group hub = Group.create("Hub", "hypixel:hub").points(List.of(point));
        Group sameIsland = Group.create("Other group", "hypixel:hub").points(List.of(point.enabled(false)));
        Group differentIsland = Group.create("Mining", "hypixel:mining_3").points(List.of(point));
        assertThrows(IllegalArgumentException.class, () -> WaypointData.requireUniqueCoordinates(List.of(hub, sameIsland)));
        assertThrows(IllegalArgumentException.class, () -> WaypointData.requireUniqueCoordinates(List.of(hub.points(List.of(point, point)))));
        assertDoesNotThrow(() -> WaypointData.requireUniqueCoordinates(List.of(hub, differentIsland)));
    }
    @Test void skyblockerImportSkipsRepeatedCoordinatesAfterClearAll() {
        Point first = new Point("First", -693, 119, 85, 0x00ff00, true);
        Point duplicate = new Point("Duplicate", -693, 119, 85, 0xff0000, false);
        Point another = new Point("Another", -698, 102, 31, 0x0000ff, true);
        Group source = Group.create("Foraging", "hypixel:foraging_2")
                .points(List.of(first, duplicate, first, another));
        Group repeated = Group.create("Repeated group", "hypixel:foraging_2").points(List.of(duplicate));
        var result = WaypointData.mergeImport(Snapshot.empty(), List.of(source, repeated));
        assertEquals(1, result.groups());
        assertEquals(2, result.points());
        assertEquals(3, result.skipped());
        assertEquals(List.of(first, another), result.snapshot().groups().getFirst().points());
        assertDoesNotThrow(() -> WaypointData.requireUniqueCoordinates(result.snapshot().groups()));
    }
    @Test void skyblockerImportPreservesExistingPointsAndIslandBoundaries() {
        Point original = new Point("Existing", 1, 70, 2, 0xff0000, true);
        Group hub = Group.create("Hub", "hypixel:hub").points(List.of(original));
        Group importedHub = Group.create("Imported Hub", "hypixel:hub")
                .points(List.of(new Point("Duplicate", 1, 70, 2, 0x00ff00, false),
                        new Point("New", 3, 70, 4, 0x00ff00, true)));
        Group mining = Group.create("Mining", "hypixel:mining_3").points(List.of(original));
        var result = WaypointData.mergeImport(new Snapshot(true, List.of(hub)), List.of(importedHub, mining));
        assertEquals(2, result.groups());
        assertEquals(2, result.points());
        assertEquals(1, result.skipped());
        assertEquals(original, result.snapshot().groups().getFirst().points().getFirst());
        assertDoesNotThrow(() -> WaypointData.requireUniqueCoordinates(result.snapshot().groups()));
    }
    @Test void skyblockerV1SharePreservesColorCoordinatesAndOrder() throws Exception {
        Group source = example();
        Group result = WaypointCodec.importGroups(WaypointCodec.exportGroups(List.of(source)), "hypixel:hub").getFirst();
        assertNotEquals(source.id(), result.id(), "Imports must not overwrite existing groups with matching IDs");
        assertEquals(source.points(), result.points());
        assertEquals(source.name(), result.name());
        assertEquals(source.scope(), result.scope());
        assertTrue(result.ordered());
        assertFalse(result.throughWalls());
    }
    @Test void disabledGroupsExportDisabledPointsWithoutMutatingStoredPointSettings() throws Exception {
        Group source = example().enabled(false);
        Group imported = WaypointCodec.importGroups(WaypointCodec.exportGroups(List.of(source)), "").getFirst();
        assertTrue(imported.points().stream().noneMatch(Point::enabled));
        assertTrue(source.points().getFirst().enabled());
    }
    @Test void acceptsActualSkyblockerComponentNameAndFloatColorSchema() throws Exception {
        String json = """
            [{"name":"Route","island":"hub","ordered":true,"renderThroughWalls":true,"waypointType":"waypoint",
              "waypoints":[{"pos":[1,70,-3],"name":{"text":"A","extra":[{"text":" B","color":"red"}]},
               "colorComponents":[1.0,0.5,0],"alpha":0.5,"shouldRender":true}]}]
            """;
        Group result = WaypointCodec.importGroups(share(json), "").getFirst();
        assertEquals("A B", result.points().getFirst().name());
        assertEquals(0xff8000, result.points().getFirst().color());
    }
    @Test void importsSkyblockerConfigFileGroupsByIslandWithoutChangingExistingData() {
        String json = """
            [{"name":"Hub route","island":"hub","waypoints":[{"pos":[1,70,-3],"name":"Start",
               "colorComponents":[1,0,0],"shouldRender":true}]},
             {"name":"Mining route","island":"mining_3","ordered":true,"renderThroughWalls":false,
              "waypoints":[{"pos":[9,105,5],"name":{"text":"Ore"},"colorComponents":[0,0.5,1],"shouldRender":false}]}]
            """;
        var imported = WaypointCodec.importConfig(json, "hypixel:hub");
        assertEquals(2, imported.size());
        assertEquals("hypixel:hub", imported.get(0).scope());
        assertEquals("hypixel:mining_3", imported.get(1).scope());
        assertTrue(imported.get(1).ordered());
        assertFalse(imported.get(1).throughWalls());
        assertFalse(imported.get(1).points().getFirst().enabled());
        assertEquals(0x0080ff, imported.get(1).points().getFirst().color());
        assertNotEquals(imported.get(0).id(), imported.get(1).id());
    }
    @Test void invalidSkyblockerConfigDoesNotProducePartialGroups() {
        String invalid = "[{\"name\":\"One\",\"island\":\"hub\",\"waypoints\":[]}," +
                "{\"name\":\"Broken\",\"island\":\"hub\",\"waypoints\":[{\"pos\":[1,2],\"name\":\"X\",\"colorComponents\":[1,1,1]}]}]";
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.importConfig(invalid, "hypixel:hub"));
    }
    @Test void unnamedSkyblockerWaypointsReceiveCoordinateLabels() {
        String json = "[{\"name\":\"Route\",\"island\":\"mining_3\",\"waypoints\":[{" +
                "\"pos\":[175,42,-470],\"name\":\"\",\"colorComponents\":[0,1,0]}]}]";
        assertEquals("Waypoint (175, 42, -470)", WaypointCodec.importConfig(json, "").getFirst().points().getFirst().name());
    }
    @Test void importedAndPreviouslySavedInstanceScopesFollowTheIsland() throws Exception {
        Group source = new Group(UUID.randomUUID().toString(), "Mining", "hypixel:crystal_hollows:mini1",
                true, false, true, example().points());
        String share = WaypointCodec.exportGroups(List.of(source));
        assertEquals("hypixel:crystal_hollows", WaypointCodec.importGroups(share, "hypixel:crystal_hollows:mini2").getFirst().scope());
        assertEquals("hypixel:crystal_hollows", WaypointCodec.importGroups(share, "hypixel:hub").getFirst().scope());
        assertEquals("hypixel:crystal_hollows", WaypointCodec.decode(WaypointCodec.encode(new Snapshot(true, List.of(source)))).groups().getFirst().scope());
    }
    @Test void unknownIslandStaysInUnknownBucket() throws Exception {
        Group source = new Group(UUID.randomUUID().toString(), "Local", "local:world1", true, false, true, List.of());
        String share = WaypointCodec.exportGroups(List.of(source));
        assertEquals("hypixel:unknown", WaypointCodec.importGroups(share, "server:example|overworld").getFirst().scope());
        assertEquals("hypixel:unknown", WaypointCodec.importGroups(share, "").getFirst().scope());
        assertEquals("hypixel:unknown", hypixelScope("future_island", "mini1"));
        assertEquals("hypixel:hub", hypixelScope("hub", "mini1"));
    }
    @Test void rejectsFractionsOutOfRangeColorAndInvalidGroupIds() throws Exception {
        String valid = """
            [{"name":"Route","island":"hub","waypoints":[{"pos":[1,70,-3],"name":"A",
               "colorComponents":[1.0,0.5,0],"alpha":0.5,"shouldRender":true}]}]
            """;
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.importGroups(share(valid.replace("[1,70,-3]", "[1.2,70,-3]")), ""));
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.importGroups(share(valid.replace("1.0,0.5", "1.1,0.5")), ""));
        assertThrows(IllegalArgumentException.class, () -> new Point("P", Integer.MIN_VALUE, 70, 0, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new Group("bad", "G", "hub", true, false, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.decode(WaypointCodec.encode(Snapshot.empty()).replace("\"version\": 1", "\"version\": 2")));
    }
    @Test void rejectsCompressedBombBeforeParsingAndExcessiveNesting() throws Exception {
        String bomb = share(" ".repeat(WaypointCodec.MAX_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.importGroups(bomb, ""));
        String nested = share("[".repeat(100) + "]".repeat(100));
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.importGroups(nested, ""));
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.importGroups(WaypointCodec.PREFIX + "!", ""));
    }
    @Test void aggregateImportLimitsCannotBeBypassedWithManySmallGroups() {
        Group source = example();
        var groups = new ArrayList<Group>();
        for (int i = 0; i < MAX_GROUPS + 1; i++) groups.add(Group.create("Group " + i, "hypixel:hub"));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(true, groups));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(true, List.of(source, source)));
        List<Point> many = Collections.nCopies(MAX_POINTS, source.points().getFirst());
        Group full = source.points(many);
        Group extra = Group.create("Extra", "hypixel:hub").points(source.points());
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(true, List.of(full, extra)));
    }
}
