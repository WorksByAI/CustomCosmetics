package com.customcosmetics.config;

/** Raw text limit: Minecraft color and format codes count toward the total. */
public final class NicknameLimit {
    public static final int MAX_LENGTH = 30;
    private NicknameLimit() {}
    public static String limit(String value) {
        if (value == null) return "";
        int end = Math.min(value.length(), MAX_LENGTH);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}
