package com.customcosmetics.utils;

/** Text normalization needed for SkyBlock mirror players and their armor stands. */
public final class LocateUtils {
    private static final java.util.regex.Pattern RGB = java.util.regex.Pattern.compile("(?i)\u00A7x(\u00A7[0-9a-f]){6}");
    private static final java.util.regex.Pattern LEGACY = java.util.regex.Pattern.compile("\u00a7[0-9a-fk-or]");
    private LocateUtils() {}

    public static String toPlainText(String text) {
        if (text == null) return "";
        if (text.indexOf('\u00a7') < 0) return text.trim();
        return LEGACY.matcher(RGB.matcher(text).replaceAll("")).replaceAll("").trim();
    }
}
