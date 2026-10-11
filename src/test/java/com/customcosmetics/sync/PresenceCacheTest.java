package com.customcosmetics.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresenceCacheTest {
    @Test void cachedRecordsContainOnlyOneUuidAndNoExtraOriginMetadata() {
        var root = PresenceCache.clean("""
            {"authVersion":2,"serviceOrigin":"https://cosmetics.example:24443","users":{
             "12345678-1234-4234-8234-123456789abc":{
               "uuid":"12345678-1234-4234-8234-123456789abc","name":"Alice","unusedInstallationMarker":true,
               "features":{"smolPeople":{"enabled":true,"limbSwingSpeed":2.5}},
               "meta":{"lastSeenAt":"2026-10-11T00:00:00Z","authVersion":2}}}}
            """);
        var user = root.getAsJsonObject("users").entrySet().iterator().next().getValue().getAsJsonObject();
        assertEquals(java.util.Set.of("name", "features", "meta"), user.keySet());
        assertEquals("Alice", user.get("name").getAsString());
        assertTrue(user.getAsJsonObject("features").getAsJsonObject("smolPeople").get("enabled").getAsBoolean());
        assertEquals("2026-10-11T00:00:00Z", user.getAsJsonObject("meta").get("lastSeenAt").getAsString());
        assertEquals(java.util.Set.of("authVersion","users"),root.keySet());
    }
    @Test void cachePathsIsolateDifferentServiceAddressesWithoutJsonMetadata() {
        var directory = java.nio.file.Path.of("test-cache");
        assertEquals(PresenceCache.path(directory,"https://a.example:24443"),PresenceCache.path(directory,"https://a.example:24443"));
        assertNotEquals(PresenceCache.path(directory,"https://a.example:24443"),PresenceCache.path(directory,"https://b.example:24443"));
        assertEquals(directory,PresenceCache.path(directory,"https://a.example:24443").getParent());
    }
}
