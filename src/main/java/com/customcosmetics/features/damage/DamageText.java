/* Adapted from Baity FancyDmgSplashSettings, Copyright (C) 2025 raueyhs.
 * Modifications Copyright (C) 2026 CustomCosmetics contributors.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package com.customcosmetics.features.damage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Strictly parses floating combat labels, never player names or health bars. */
public final class DamageText {
    private static final Pattern NUMBER = Pattern.compile("([✧✯]?)((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)([kKmMbBtTqQ]?)([✧✯]?)([⚔☄♞✷ﬗ✯]*)");
    private static final BigDecimal MAX_DAMAGE=new BigDecimal("1E18");
    private static final String UNITS = " KMBTQ";
    public record Hit(BigDecimal amount, boolean critical, String suffix) {}
    public record Glyph(String text, int color) {}
    private DamageText() {}

    public static Hit parse(String text) {
        if (text == null || text.length() > 48) return null;
        var match = NUMBER.matcher(text);
        if (!match.matches()) return null;
        // Fishing countdowns use bare decimals too. Only accept fractional values
        // when a combat marker or a damage unit distinguishes them from timers.
        if (match.group(2).contains(".") && match.group(1).isEmpty() && match.group(3).isEmpty()
                && match.group(4).isEmpty() && match.group(5).isEmpty()) return null;
        try {
            var amount = new BigDecimal(match.group(2).replace(",", ""));
            int unit = match.group(3).isEmpty() ? 0 : UNITS.indexOf(match.group(3).toUpperCase(Locale.ROOT));
            amount = amount.scaleByPowerOfTen(unit * 3);
            if (amount.signum() <= 0 || amount.compareTo(MAX_DAMAGE) > 0) return null;
            return new Hit(amount, !match.group(1).isEmpty() || !match.group(4).isEmpty(), match.group(5));
        } catch (NumberFormatException error) { return null; }
    }

    public static String number(BigDecimal amount, boolean compact, String separator) {
        if (compact) {
            int unit = 0;
            while (amount.compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < 5) {
                amount = amount.movePointLeft(3); unit++;
            }
            amount = amount.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
            if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < 5) { amount = amount.movePointLeft(3).stripTrailingZeros(); unit++; }
            return amount.toPlainString() + (unit == 0 ? "" : UNITS.charAt(unit));
        }
        String[] parts = amount.stripTrailingZeros().toPlainString().split("\\.", 2);
        String group = switch (separator == null ? "" : separator) { case "comma" -> ","; case "space" -> " "; case "underscore" -> "_"; default -> ""; };
        String integer = parts[0].replaceAll("(?<=\\d)(?=(\\d{3})+$)", java.util.regex.Matcher.quoteReplacement(group));
        return integer + (parts.length == 2 ? "." + parts[1] : "");
    }

    public static String symbol(String value) {
        if (value == null) return "✧";
        if (value.isEmpty()) return "";
        int code = value.codePointAt(0);
        return Character.isISOControl(code) || code == '§' || Character.isSurrogate((char) code) && code <= 0xFFFF
            ? "✧" : new String(Character.toChars(code));
    }

    public static List<Glyph> glyphs(Hit hit, boolean compact, String separator, String symbol, boolean styleNormal, int start, int end, int originalColor) {
        return glyphs(hit, compact, separator, symbol, styleNormal, start, end, originalColor, "gradient", 0, 1);
    }

    public static List<Glyph> glyphs(Hit hit, boolean compact, String separator, String symbol, boolean styleNormal,
                                   int start, int end, int originalColor, String colorMode, double seconds, double speed) {
        boolean styled = hit.critical() || styleNormal;
        boolean rainbow = "rainbow".equals(DamageColors.normalize(colorMode));
        int phase = DamageColors.phase(seconds, speed);
        String numeric = number(hit.amount(), compact, separator);
        String text = (hit.critical() ? symbol(symbol) : "") + numeric + (hit.critical() ? symbol(symbol) : "") + hit.suffix();
        int[] codes = text.codePoints().toArray();
        var result = new java.util.ArrayList<Glyph>(codes.length);
        for (int i = 0; i < codes.length; i++) result.add(new Glyph(new String(Character.toChars(codes[i])), !styled ? originalColor & 0xFFFFFF
            : rainbow ? DamageColors.rgb(DamageColors.index(i, codes.length, phase)) : blend(start, end, codes.length == 1 ? 0 : (double) i / (codes.length - 1))));
        return List.copyOf(result);
    }

    public static int blend(int start, int end, double progress) {
        double t = Math.clamp(Double.isFinite(progress) ? progress : 0, 0, 1);
        int out = 0;
        for (int shift : new int[]{16, 8, 0}) out |= (int) Math.round(((start >> shift) & 255) * (1-t) + ((end >> shift) & 255) * t) << shift;
        return out;
    }
}
