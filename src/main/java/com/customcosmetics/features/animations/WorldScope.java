// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.animations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class WorldScope {
    private WorldScope() {}
    public record Candidate(UUID id, double distanceSquared) {}
    public static String hypixelKey(int environment, String instance) {
        return environment < 0 || !validInstance(instance)
            ? "" : "$hypixel" + environment + ":" + instance.toLowerCase(Locale.ROOT);
    }
    private static boolean validInstance(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                || c == '_' || c == '~' || c == '-')) return false;
        }
        return true;
    }
    public static boolean isHypixelHost(String address) {
        if (address == null) return false;
        String host = address.toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            if (colon == host.length() - 1) return false;
            for (int i = colon + 1; i < host.length(); i++)
                if (host.charAt(i) < '0' || host.charAt(i) > '9') return false;
            host = host.substring(0, colon);
        }
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
    public static String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static Set<UUID> visible(Collection<Candidate> candidates, double range) {
        if (!Double.isFinite(range) || range <= 0) return Set.of();
        // Interest is a set, not an ordered/limited list. Sorting it adds work without changing membership.
        var visible = new HashSet<UUID>();
        double maximum = range * range;
        for (var candidate : candidates) {
            double distance = candidate.distanceSquared();
            if (Double.isFinite(distance) && distance >= 0 && distance <= maximum) visible.add(candidate.id());
        }
        return Set.copyOf(visible);
    }
}
