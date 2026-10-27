package com.customcosmetics.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsCatalogTest {
    @Test void catalogueCoversEveryStudioSectionIncludingModuleControls() {
        for (String page : new String[]{"SIZE", "NICK", "COLOR", "CHROMA", "PRESETS", "ANIMATIONS", "DAMAGE",
            "ITEMS", "ACCESSORIES", "CAPE", "PARTY", "SKINS", "MAP", "SYNC", "MODULES"}) {
            assertTrue(SettingsCatalog.entries().stream().anyMatch(entry -> entry.page().equals(page)), page);
        }
        assertTrue(SettingsCatalog.find("模块").stream().anyMatch(entry -> entry.page().equals("MODULES")));
        assertTrue(SettingsCatalog.find("地图替换材质").stream().anyMatch(entry -> entry.title().equals("Replace Materials")));
    }
    @Test void hiddenConditionalSettingsRemainDiscoverableWithGuidance() {
        for (String title : new String[]{"Shimmer Size", "Edit Gradient Segments", "Terminator Only", "Swing Speed",
            "Rainbow Speed", "Gradient Start"}) {
            assertTrue(SettingsCatalog.find(title).stream().anyMatch(entry -> entry.title().equals(title)
                && entry.description().startsWith("Requires ")), title);
        }
    }
    @Test void changingDisplayValuesResolveToTheSameActualControl() {
        assertTrue(SettingsSearch.sameSetting("Chat Sound: Note Block", "Chat Sound"));
        assertTrue(SettingsSearch.sameSetting("Gradient Style: Shimmer", "Gradient Style"));
        assertFalse(SettingsSearch.sameSetting("Show Names", "Show Shared Positions"));
    }
    @Test void selectedCreatureChoicesKeepTheirExactSearchDestination() {
        assertTrue(SettingsSearch.sameSetting("✓ Player", "Player"));
        assertTrue(SettingsSearch.sameSetting("✓ Chicken", "Chicken"));
        assertTrue(SettingsSearch.sameSetting("Chicken", "Chicken"));
        assertFalse(SettingsSearch.sameSetting("✓ Chicken", "Player"));
        assertFalse(SettingsSearch.sameSetting("✓Chicken", "Chicken"));
        assertFalse(SettingsSearch.sameSetting("Chicken ✓", "Chicken"));
        for (String title : new String[]{"Player", "Chicken", "Ender Dragon", "Warden", "Sulfur Cube"})
            assertTrue(SettingsCatalog.find(title).stream().anyMatch(entry -> entry.page().equals("SIZE")
                && SettingsSearch.sameSetting("✓ " + title, entry.title())), title);
    }
    @Test void blockingStyleOptionNamesResolveToTheirDropdown() {
        var results = SettingsCatalog.find("Jigsaw");
        assertEquals(1, results.size());
        assertEquals("Blocking Style", results.getFirst().title());
        assertEquals("ANIMATIONS", results.getFirst().page());
        assertTrue(SettingsCatalog.find("Default").stream().anyMatch(entry -> entry.title().equals("Blocking Style")));
        assertEquals(1, SettingsCatalog.entries().stream().filter(entry -> entry.page().equals("ANIMATIONS")
            && entry.title().equals("Blocking Style")).count());
    }
    @Test void nativeSearchQueriesKeepTheirSpecificPageDestinations() {
        String[][] cases = {
            {"location tracer", "Location Tracers", "PARTY"},
            {"伤害 大小", "Size", "DAMAGE"},
            {"鎏金大小", "Shimmer Size", "COLOR"},
            {"预设", "Save Current Colors", "PRESETS"},
            {"预设", "Save / Update", "PRESETS"},
            {"jigsaw", "Blocking Style", "ANIMATIONS"}
        };
        for (var sample : cases)
            assertEquals(1, SettingsCatalog.find(sample[0]).stream().filter(entry -> entry.title().equals(sample[1])
                && entry.page().equals(sample[2])).count(), sample[0] + " -> " + sample[2] + " / " + sample[1]);
        assertTrue(SettingsCatalog.find("no_matching_setting_123").isEmpty());
        assertTrue(SettingsCatalog.find("鎏金大小").getFirst().description().startsWith("Requires Shimmer:"));
    }
    @Test void commandToolsStayOutOfTheSettingsNavigation() {
        assertTrue(SettingsCatalog.find("waypoint").isEmpty());
        assertTrue(SettingsCatalog.find("shortcuts").isEmpty());
    }
}
