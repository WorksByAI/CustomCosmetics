// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

/** Immutable, client-rendered gradient; elapsed time never requires a remote update. */
public record NicknameGradient(String style, int start, int end, double speed, int shimmerColor, int shimmerHighlightColor, double shimmerSize, double shimmerInterval) {
    public static final int PRESET_START = 0x24CFDF;
    public static final int PRESET_END = 0xEA78C5;
    public static final int DEFAULT_SHIMMER = 0xFFCF64;
    public static final double DEFAULT_SIZE = 0.16;
    public static final double DEFAULT_INTERVAL = 2;
    public static final int DEFAULT_HIGHLIGHT = 0xFFF0BB;
    public NicknameGradient(String style, int start, int end, double speed) {
        this(style, start, end, speed, DEFAULT_SHIMMER, DEFAULT_HIGHLIGHT, DEFAULT_SIZE, DEFAULT_INTERVAL);
    }
    public NicknameGradient(String style, int start, int end, double speed, int shimmerColor, int shimmerHighlightColor) {
        this(style,start,end,speed,shimmerColor,shimmerHighlightColor,DEFAULT_SIZE,DEFAULT_INTERVAL);
    }
    public NicknameGradient(String style,int start,int end,double speed,int shimmerColor,int shimmerHighlightColor,double shimmerSize) {
        this(style,start,end,speed,shimmerColor,shimmerHighlightColor,shimmerSize,DEFAULT_INTERVAL);
    }
    public static final long PERIOD_MS = 3400;

    public NicknameGradient {
        style = normalizeStyle(style);
        shimmerSize = normalizeSize(shimmerSize);
        shimmerInterval = normalizeInterval(shimmerInterval);
        start &= 0xFFFFFF;
        end &= 0xFFFFFF;
        shimmerColor &= 0xFFFFFF; shimmerHighlightColor &= 0xFFFFFF;
        speed = Double.isFinite(speed) ? Math.clamp(speed, 0, 4) : 1;
    }

    public static double normalizeInterval(double seconds) { return Double.isFinite(seconds) ? Math.clamp(seconds,0,30) : DEFAULT_INTERVAL; }
    public long motionMillis() { return speed == 0 ? 0 : Math.max(1,Math.round((1+2*shimmerSize+0.13)/(3.5/3.4)*1000/speed)); }
    public long periodMillis() { return motionMillis()+Math.round(shimmerInterval*1000); }
    public static double normalizeSize(double size) { return Double.isFinite(size) ? Math.clamp(size, 0.02, 1) : DEFAULT_SIZE; }
    public static String normalizeStyle(String style) {
        return "smooth".equals(style) || "shimmer".equals(style) ? style : "character";
    }

    public boolean smooth() { return !style.equals("character"); }

    public int color(double position, double height, long timeMs) {
        double x = Math.clamp(Double.isFinite(position) ? position : 0, 0, 1);
        int base = hueBlend(start, end, x);
        if (!style.equals("shimmer") || speed == 0) return base;
        // A short, diagonal colored sweep followed by a quiet gap, as in the reference.
        long moving = motionMillis();
        long elapsed = Math.floorMod(timeMs, periodMillis());
        if (elapsed >= moving) return base;
        double sweep = -shimmerSize - 0.065 + elapsed / (double)moving * (1 + 2*shimmerSize + 0.13);
        double distance = Math.abs(x + (Math.clamp(Double.isFinite(height) ? height : 0.5, 0, 1) - 0.5) * 0.13 - sweep);
        double intensity = Math.max(0, 1 - distance / shimmerSize);
        intensity = intensity * intensity * (3 - 2 * intensity);
        int shine = rgbBlend(shimmerColor, shimmerHighlightColor, intensity);
        return rgbBlend(base, shine, intensity * 0.96);
    }

    private static int hueBlend(int from, int to, double t) {
        float[] a = hsv(from), b = hsv(to);
        if (a[1] < 0.001) a[0] = b[0];
        if (b[1] < 0.001) b[0] = a[0];
        double delta = b[0] - a[0];
        if (delta > 0.5) delta -= 1;
        if (delta < -0.5) delta += 1;
        double hue = a[0] + delta * t;
        hue -= Math.floor(hue);
        double s = a[1] + (b[1] - a[1]) * t;
        double v = a[2] + (b[2] - a[2]) * t;
        double h = hue * 6, f = h - Math.floor(h), p = v * (1 - s), q = v * (1 - f * s), r = v * (1 - (1 - f) * s);
        return switch ((int) h % 6) {
            case 0 -> rgb(v, r, p); case 1 -> rgb(q, v, p); case 2 -> rgb(p, v, r);
            case 3 -> rgb(p, q, v); case 4 -> rgb(r, p, v); default -> rgb(v, p, q);
        };
    }

    private static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = d == 0 ? 0 : max == r ? (g - b) / d : max == g ? 2 + (b - r) / d : 4 + (r - g) / d;
        h = (h / 6 + 1) % 1;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private static int rgb(double r, double g, double b) {
        return ((int)Math.round(r * 255) << 16) | ((int)Math.round(g * 255) << 8) | (int)Math.round(b * 255);
    }

    private static int rgbBlend(int a, int b, double t) {
        int r = (int)Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int)Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | blue;
    }
}
