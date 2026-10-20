// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Searchable settings without constructing screens, previews, widgets or touching stores.
 * Destinations are resolved against the actual page's indexed controls when opened.
 */
public final class SettingsCatalog {
    private static final List<SettingsSearch.Entry> ENTRIES = create();
    private static final SettingsSearch.Index INDEX = new SettingsSearch.Index(ENTRIES);
    private SettingsCatalog() {}
    public static List<SettingsSearch.Entry> entries() { return ENTRIES; }
    public static List<SettingsSearch.Entry> find(String query) { return INDEX.find(query); }

    private static List<SettingsSearch.Entry> create() {
        var entries = new ArrayList<SettingsSearch.Entry>();
        add(entries, "Player Size", "SIZE", "Proportions scale body",
            "Enabled|Size X|Size Y|Size Z|Reset Player Size|SmolPeople|Limb Swing Speed|Creature Model|Player|Zombie|Skeleton|Creeper|Enderman|Blaze|Iron Golem|Villager|Cow|Pig|Sheep|Chicken");
        add(entries, "NickTweaks", "NICK", "Nickname name color codes",
            "Enabled|Nick Changer|Bold Self Nick|Blocked Nicknames");
        add(entries, "NickTweaks", "COLOR", "Nickname gradient start end color picker HEX",
            "Custom Nick Color|Gradient Style|Shimmer Preset|Editing|Gradient Colors");
        add(entries, "NickTweaks", "COLOR", "Requires Segmented: select the Segmented gradient style first.",
            "Edit Gradient Segments");
        add(entries, "NickTweaks", "COLOR", "Requires Shimmer: select the Shimmer gradient style first.",
            "Shimmer Size|Shimmer Speed|Shimmer Interval|Shimmer Colors");
        add(entries, "NickTweaks", "CHROMA", "Rainbow nickname chroma color animation",
            "Chroma|Lightness|Size|Speed");
        add(entries, "NickTweaks", "PRESETS", "Saved nickname color presets",
            "Save Current Colors|Save / Update");
        add(entries, "Animations", "ANIMATIONS", "Sword blocking item whitelist blacklist pose",
            "Sword Blocking|Adjust Blocking|Blocking Style|Blocking List Mode|Edit Blocking Lists|Apply Lists to Other Players|Share Blocking");
        add(entries, "Animations", "ANIMATIONS", "Held item view model position rotation scale",
            "View Model|Item Scale|Position X|Position Y|Position Z|Rotation X|Rotation Y|Rotation Z|Swing X|Swing Y|Swing Z|Disable Hand Movement|Disable Equip Animation|Disable Swing Animation|Reset View Model");
        add(entries, "Animations", "ANIMATIONS", "Requires Disable Swing Animation to be enabled.", "Terminator Only");
        add(entries, "Animations", "ANIMATIONS", "Requires swing animation: turn off Disable Swing Animation or enable Terminator Only.",
            "Swing Speed|Ignore Haste");
        add(entries, "Damage Splash", "DAMAGE", "Local damage numbers animation critical normal",
            "Damage Splash|Animation|Size|Duration (seconds)|Color Mode|Critical Symbol|Bold|Style Normal Damage|Compact Numbers|Separator|Text Shadow");
        add(entries, "Damage Splash", "DAMAGE", "Requires Rainbow: select the Rainbow color mode first.", "Rainbow Speed");
        add(entries, "Damage Splash", "DAMAGE", "Requires Gradient: select the Gradient color mode first.", "Gradient Start|Gradient End");
        add(entries, "Item Customization", "ITEMS", "Custom item names and models resource packs", "Enabled|Customize Items");
        add(entries, "Item Customization", "ITEMS", "SkyBlock accessory bag collected upgrade tooltip tier", "Accessory Helper Tooltip|Clear This Profile's Bag Scan");
        add(entries, "Equipments (Beta)", "ACCESSORIES", "Accessories outfit wardrobe style colors size shoulder", "Blocked Equipments|Open Wardrobe|Clear Outfit");
        add(entries, "Cape", "CAPE", "Cape visibility local import texture upload approval",
            "Show Custom Capes|Choose Image & Crop|Choose Cape + Elytra|Submit for Review");
        add(entries, "Sharing", "PARTY", "Party teammates markers locations",
            "Party ESP|Chams|Connection Notifications|Max Display Distance (blocks)|ESP Style|ESP Color|Fill Opacity|Outline Width|Tracers|Tracer Color|Show Names|Nametag Style|Hide Names Within (blocks)|Nametag Scaling|Nametag Size|Distant Markers");
        add(entries, "Sharing", "PARTY", "Party chat messages", "IRC For Party Members|Chat Prefix");
        add(entries, "Sharing", "PARTY", "Party chat notification sound resource pack",
            "Chat Notification Sound|Chat Sound|Chat Sound Volume (%)|Chat Sound Pitch|Preview Chat Sound");
        add(entries, "Sharing", "PARTY", "Location position sharing audience selected players",
            "Share My Position|Audience|Selected Players|Show Shared Positions|Location Tracers|Location Tracer Color");
        add(entries, "Skins", "SKINS", "Local skin import PNG Alex Steve P2P",
            "Import Local Skin|Show My Local Skin|Remove Local Skin|Skin Model|Share Skin via P2P|Receive P2P Skins");
        add(entries, "Map Editor", "MAP", "Local visual map dungeon Kuudra rooms",
            "Freecam|Freecam Speed (blocks/s)|Map Editor|Dungeon Only|Force Dungeon|Force Kuudra|Edit Mode|Import FME Config|Choose Block / Favorites|Replace Materials|Editor HUD|Reset This Room|Block Material|Undo|Clear Current Edits|Automatically Restore Rooms|Preset Manager");
        add(entries, "Settings", "SYNC", "General preferences",
            "Module Manager|Check for Updates|Command Tooltips|Snaplook|Snaplook View|Snaplook Keybind…");
        add(entries, "Settings", "SYNC", "Connections automatic sync online offline",
            "Mode|Automatic Sync|Refresh Interval (seconds)|Notifications|Sync Now");
        add(entries, "Settings", "SYNC", "Advanced connections proxy",
            "Proxy Host|Proxy Port|Proxy Auth|Disable P2P Connections|P2P UDP Port|Fallback Direct|Proxy Source");
        add(entries, "Module Manager", "MODULES", "Module master switch enabled disabled settings",
            "Enable All Modules|Disable All Modules|Animations|Cape|Damage Splash|Equipments|Item Customization|Map Editor|NickTweaks|Player Size|SmolPeople|Sharing|Skins|Snaplook|Command Tooltips|Creature Model");
        return List.copyOf(entries);
    }
    private static void add(List<SettingsSearch.Entry> entries, String section, String page, String description, String titles) {
        for (String title : titles.split("\\|")) entries.add(new SettingsSearch.Entry(section, title, description, page, 0));
    }
}
