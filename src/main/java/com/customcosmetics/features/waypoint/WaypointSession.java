// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.function.BooleanSupplier;

/** Server identity and SkyBlock island come from Hypixel Mod API packets, never the connection address. */
final class WaypointSession {
    private Object connection;
    private boolean hypixel, skyblock;
    private String island = "";

    void reset(Object nextConnection) {
        connection = nextConnection;
        hypixel = skyblock = false;
        island = "";
    }

    boolean follow(Object currentConnection) {
        if (connection == currentConnection) return false;
        reset(currentConnection);
        return true;
    }

    void hello(Object source) {
        if (source == null) return;
        follow(source);
        hypixel = true;
    }

    void location(Object source, String serverType, String mode) {
        if (source == null) return;
        follow(source);
        hypixel = true;
        skyblock = "SKYBLOCK".equals(serverType);
        island = skyblock ? SkyblockIslands.normalize(mode) : "";
    }

    boolean locraw(Object source, String gameType, String mode) {
        if (!isHypixel(source)) return false;
        skyblock = "SKYBLOCK".equals(gameType);
        island = skyblock ? SkyblockIslands.normalize(mode) : "";
        return true;
    }

    boolean isHypixel(Object source) {
        return source != null && source == connection && hypixel;
    }

    boolean hasSkyblockLocation(Object source) {
        return isHypixel(source) && skyblock;
    }

    String scope(Object source, BooleanSupplier skyblockSidebar) {
        if (!isHypixel(source)) return "";
        if (skyblock) return WaypointData.hypixelScope(island, "");
        return skyblockSidebar.getAsBoolean() ? WaypointData.hypixelScope("unknown", "") : "";
    }
}
