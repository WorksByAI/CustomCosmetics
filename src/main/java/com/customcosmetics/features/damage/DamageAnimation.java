/* Adapted from Baity FancyDmgSplash, Copyright (C) 2025 raueyhs.
 * Modifications Copyright (C) 2026 CustomCosmetics contributors.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package com.customcosmetics.features.damage;

import java.util.List;
import java.util.Locale;

/** Baity's eight movement styles, shared by world rendering and the live GUI preview. */
public final class DamageAnimation {
    public static final List<String> STYLES = List.of("default", "genshin", "arc", "slam", "bounce", "shake", "fall", "spiral");
    public record Frame(double x, double y, double z, float alpha, float scale) {}
    private DamageAnimation() {}
    public static String normalize(String value) { return value != null && STYLES.contains(value.toLowerCase(Locale.ROOT)) ? value.toLowerCase(Locale.ROOT) : "default"; }
    public static double bounded(double value, double fallback, double min, double max) { return Math.clamp(Double.isFinite(value) ? value : fallback, min, max); }
    private static double ease(double t) { return 1 - (1-t)*(1-t); }
    private static float fade(double t, double start) { double p = Math.clamp((t-start)/(1-start)*2, 0, 1); return (float) (1-p*p); }
    private static float pop(double p, double start, double boost, double peak, double reduction) {
        return (float) (p < .3 ? start + p/.3*boost : p < .6 ? peak-(p-.3)/.3*reduction : 1);
    }
    public static Frame frame(String style, double progress, boolean critical, double driftX, double driftZ, double phase) {
        double t = bounded(progress, 0, 0, 1), x = 0, y = 0, z = 0;
        float alpha = 1, scale = 1;
        switch (normalize(style)) {
            case "genshin" -> {
                if (t < .4) scale = pop(t/.4, .5, 1, 1.5, .5);
                else { double p = (t-.4)/.6; y = ease(p)*.18; alpha = fade(p, .25); }
            }
            case "arc" -> { x = driftX*ease(t)*.8; z = driftZ*ease(t)*.8; y = Math.sin(t*Math.PI)*.25+t*.12; scale = t < .15 ? pop(t/.15, .5, 1, 1.5, .5) : 1; alpha = fade(t, .75); }
            case "slam" -> {
                if (!critical) y = ease(t)*.18;
                else if (t < .2) { double p = Math.pow(t/.2, 3); y = .35*(1-p); scale = (float) (1.2-p*.1); }
                else if (t < .35) { double p = (t-.2)/.15; y = -.02*(1-p); scale = (float) (1.1+Math.sin(p*Math.PI)*.25); }
                else y = ease((t-.35)/.65)*.08;
                alpha = fade(t, critical ? .7 : .6);
            }
            case "bounce" -> {
                if (t < .42) { double p = t/.42*3; int bounce = Math.min(2, (int) p); p -= bounce; double peak = .14*Math.pow(.55, bounce); y = peak*4*p*(1-p); scale = (float) (1+Math.sin(p*Math.PI)*.1*peak/.14); }
                else y = ease((t-.42)/.58)*.06;
                alpha = fade(t, .68);
            }
            case "shake" -> { if (critical) { double p = Math.min(1, t/.35), a = .045*(1-ease(p)), angle = phase+p*18*Math.PI*2; x = Math.sin(angle)*a; z = Math.cos(angle*1.31)*a*.5; } alpha = fade(t, .75); }
            case "fall" -> { y = -.38*t*t; alpha = fade(t, .55); }
            case "spiral" -> { double r = .18*(1-t*.35), a = phase+t*1.25*Math.PI*2; x = Math.cos(a)*r; z = Math.sin(a)*r; y = ease(t)*.22; scale = t < .12 ? pop(t/.12, .85, .3, 1.15, .15) : 1; alpha = fade(t, .72); }
            default -> { }
        }
        return new Frame(x, y, z, t >= 1 ? 0 : alpha, scale);
    }
}
