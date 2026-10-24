// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import java.util.*;

/** Local visual preferences. SkyBlock identities never fall back to their vanilla material. */
public final class BlockingRules {
    public enum Mode {
        OFF("Off"), BLACKLIST("Blacklist"), WHITELIST("Whitelist");
        public final String label;
        private final String value;
        private static final Mode[] OPTIONS = values();
        Mode(String label) { this.label = label; this.value = name().toLowerCase(Locale.ROOT); }
        public String value() { return value; }
        public static Mode parse(String value) {
            if (value != null) { value=value.strip();for (Mode mode : OPTIONS) if (mode.value.equalsIgnoreCase(value)) return mode; }
            return OFF;
        }
    }
    private BlockingRules() {}
    public static String normalize(String value) {
        if (value == null) return "";
        value = value.strip();
        if (value.regionMatches(true, 0, "skyblock:", 0, 9)) {
            String id = value.substring(9).toUpperCase(Locale.ROOT);
            return validPath(id, true) ? "skyblock:" + id : "";
        }
        value = value.toLowerCase(Locale.ROOT);
        return value.startsWith("minecraft:") && validPath(value.substring(10), false) ? value : "";
    }
    private static boolean validPath(String value,boolean skyblock){
        if(value.isEmpty()||value.length()>128)return false;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c>='0'&&c<='9'||c=='_'||(skyblock?c>='A'&&c<='Z':c>='a'&&c<='z'||c=='.'||c=='/'||c=='-'))continue;
            return false;
        }
        return true;
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
