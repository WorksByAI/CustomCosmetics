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
    @Test void commandToolsStayOutOfTheSettingsNavigation() {
        assertTrue(SettingsCatalog.find("waypoint").isEmpty());
        assertTrue(SettingsCatalog.find("shortcuts").isEmpty());
    }
}
