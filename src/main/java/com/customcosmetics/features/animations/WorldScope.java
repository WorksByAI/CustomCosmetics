// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.animations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public final class WorldScope {
    private WorldScope() {}
    public record Candidate(UUID id, double distanceSquared) {}
    public static String hypixelKey(int environment, String instance) {
        return environment < 0 || instance == null || !instance.matches("[A-Za-z0-9_~-]{1,64}")
            ? "" : "$hypixel" + environment + ":" + instance.toLowerCase(Locale.ROOT);
    }
    public static String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static Set<UUID> visible(Collection<Candidate> candidates, double range) {
        if (!Double.isFinite(range) || range <= 0) return Set.of();
        return candidates.stream().filter(p -> Double.isFinite(p.distanceSquared()) && p.distanceSquared() >= 0 && p.distanceSquared() <= range * range)
            .sorted(Comparator.comparingDouble(Candidate::distanceSquared).thenComparing(Candidate::id))
            .map(Candidate::id).distinct().collect(Collectors.toUnmodifiableSet());
    }
}
