// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import java.util.*;

/** Local visual preferences. SkyBlock identities never fall back to their vanilla material. */
public final class BlockingRules {
    public enum Mode {
        OFF("Off"), BLACKLIST("Blacklist"), WHITELIST("Whitelist");
        public final String label;
        Mode(String label) { this.label = label; }
        public String value() { return name().toLowerCase(Locale.ROOT); }
        public static Mode parse(String value) {
            if (value != null) for (Mode mode : values()) if (mode.value().equalsIgnoreCase(value.strip())) return mode;
            return OFF;
        }
    }
    private BlockingRules() {}
    public static String normalize(String value) {
        if (value == null) return "";
        value = value.strip();
        if (value.regionMatches(true, 0, "skyblock:", 0, 9)) {
            String id = value.substring(9).toUpperCase(Locale.ROOT);
            return id.matches("[A-Z0-9_]{1,128}") ? "skyblock:" + id : "";
        }
        value = value.toLowerCase(Locale.ROOT);
        return value.matches("minecraft:[a-z0-9_./-]{1,128}") ? value : "";
    }
    public static String identity(Optional<String> skyblock, String vanilla) {
        return skyblock.isPresent() ? normalize("skyblock:" + skyblock.get()) : normalize(vanilla);
    }
    public static String[] sanitize(String[] entries) {
        if (entries == null) return new String[0];
        return Arrays.stream(entries).map(BlockingRules::normalize).filter(id -> !id.isEmpty())
            .distinct().limit(4096).sorted().toArray(String[]::new);
    }
    public static boolean allows(Mode mode, String id, boolean sword, Set<String> blacklist, Set<String> whitelist) {
        return switch (mode) {
            case OFF -> sword;
            case BLACKLIST -> sword && !blacklist.contains(id);
            case WHITELIST -> !id.isEmpty() && whitelist.contains(id);
        };
    }

    /** An authenticated sender has already checked whether its held item can block. */
    public static boolean allowsRemote(boolean applyLists, Mode mode, String id, Set<String> blacklist, Set<String> whitelist) {
        if (!applyLists || mode == Mode.OFF) return true;
        return mode == Mode.BLACKLIST ? !blacklist.contains(id) : !id.isEmpty() && whitelist.contains(id);
    }
}
