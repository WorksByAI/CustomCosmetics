// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PurseDisplay {
    public static final int MAX_OFFSET_LENGTH = 31;
    private static final String AMOUNT = "[+-]?(?:[0-9]{1,20}|[0-9]{1,3}(?:,[0-9]{3}){1,6})(?:\\.[0-9]{1,2})?";
    private static final Pattern OFFSET = Pattern.compile(AMOUNT);
    private static final Pattern ROW = Pattern.compile("^\\h*Purse:\\h*(?<balance>" + AMOUNT + ")(?=$|\\h|\\()");
    private static Component cachedLine;
    private static String cachedOffset;
    private static MutableComponent cachedResult;

    private PurseDisplay() {}

    public static BigDecimal parseOffset(String value) {
        if (value == null) return null;
        String text = value.strip();
        if (text.isEmpty()) return BigDecimal.ZERO;
        return OFFSET.matcher(text).matches() ? new BigDecimal(text.replace(",", "")) : null;
    }

    public static String normalizeOffset(String value) {
        return parseOffset(value) == null || value.isBlank() ? "0" : value.strip();
    }

    public static MutableComponent apply(MutableComponent line, String offset) {
        if (offset == null || "0".equals(offset)) return line;
        if (line.equals(cachedLine) && offset.equals(cachedOffset)) return cachedResult;

        String raw = line.getString();
        boolean legacy = raw.indexOf('§') >= 0;
        String display = legacy ? StringDecomposer.getPlainText(line) : raw;
        if (!display.stripLeading().startsWith("Purse:")) return line;
        var result = rewrite(line, display, parseOffset(offset), legacy);
        cachedLine = line;
        cachedOffset = offset;
        cachedResult = result;
        return result;
    }

    private static MutableComponent rewrite(MutableComponent line, String display, BigDecimal offset, boolean legacy) {
        var row = ROW.matcher(display);
        if (offset == null || offset.signum() == 0 || !row.find()) return line;
        int start = row.start("balance");
        int end = row.end("balance");
        var balance = new BigDecimal(row.group("balance").replace(",", ""));
        var adjusted = balance.add(offset);
        String[] parts = adjusted.abs().toPlainString().split("\\.", 2);
        String digits = String.format(Locale.ROOT, "%,d", new BigInteger(parts[0]));
        String amount = (adjusted.signum() < 0 ? "-" : "") + digits + (parts.length == 2 ? "." + parts[1] : "");

        var result = Component.empty();
        int[] cursor = {0};
        var visible = legacy ? decodeLegacy(line) : line;
        visible.visit((style, text) -> {
            int from = cursor[0];
            int to = from + text.length();
            if (from < start) append(result, text.substring(0, Math.min(to, start) - from), style);
            if (from <= start && start < to) append(result, amount, style);
            if (to > end) append(result, text.substring(Math.max(from, end) - from), style);
            cursor[0] = to;
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static MutableComponent decodeLegacy(Component line) {
        var decoded = Component.empty();
        var buffer = new StringBuilder();
        Style[] active = {Style.EMPTY};
        // Vanilla drops synthetic row markers such as §s and applies the legacy colors before rendering.
        StringDecomposer.iterateFormatted(line, Style.EMPTY, (index, style, codePoint) -> {
            if (!style.equals(active[0])) {
                if (!buffer.isEmpty()) append(decoded, buffer.toString(), active[0]);
                buffer.setLength(0);
                active[0] = style;
            }
            buffer.appendCodePoint(codePoint);
            return true;
        });
        if (!buffer.isEmpty()) append(decoded, buffer.toString(), active[0]);
        return decoded;
    }

    private static void append(MutableComponent line, String text, Style style) {
        line.append(Component.literal(text).setStyle(style));
    }
}
