package com.customcosmetics.utils;

/** Text normalization needed for SkyBlock mirror players and their armor stands. */
public final class LocateUtils {
    private LocateUtils() {}

    public static String toPlainText(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)\u00A7x(\u00A7[0-9a-f]){6}", "")
            .replaceAll("\u00a7[0-9a-fk-or]", "").trim();
    }
}
