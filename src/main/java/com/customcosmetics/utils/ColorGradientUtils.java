/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
// Oklab conversion: Copyright (c) 2020 Björn Ottosson; MIT, see LICENSES/Oklab-MIT.txt.
package com.customcosmetics.utils;

public class ColorGradientUtils {
    
    public static int blendColors(int startColor, int endColor, float ratio) {
        float r1 = ((startColor >> 16) & 0xFF) / 255f;
        float g1 = ((startColor >> 8) & 0xFF) / 255f;
        float b1 = (startColor & 0xFF) / 255f;

        float r2 = ((endColor >> 16) & 0xFF) / 255f;
        float g2 = ((endColor >> 8) & 0xFF) / 255f;
        float b2 = (endColor & 0xFF) / 255f;

        ColorSpaceLab lab1 = convertToLabColorSpace(linearize(r1), linearize(g1), linearize(b1));
        ColorSpaceLab lab2 = convertToLabColorSpace(linearize(r2), linearize(g2), linearize(b2));

        float L = Math.fma(ratio, (lab2.l - lab1.l), lab1.l);
        float A = Math.fma(ratio, (lab2.a - lab1.a), lab1.a);
        float B = Math.fma(ratio, (lab2.b - lab1.b), lab1.b);

        ColorSpaceRGB rgb = convertToRGBColorSpace(L, A, B);
        int r = Math.clamp((int) (delinearize(rgb.r) * 255f), 0, 255);
        int g = Math.clamp((int) (delinearize(rgb.g) * 255f), 0, 255);
        int b = Math.clamp((int) (delinearize(rgb.b) * 255f), 0, 255);

        return (r << 16) | (g << 8) | b;
    }

    private static ColorSpaceLab convertToLabColorSpace(float r, float g, float b) {
        float l = Math.fma(0.4122214708f, r, Math.fma(0.5363325363f, g, 0.0514459929f * b));
        float m = Math.fma(0.2119034982f, r, Math.fma(0.6806995451f, g, 0.1073969566f * b));
        float s = Math.fma(0.0883024619f, r, Math.fma(0.2817188376f, g, 0.6299787005f * b));

        float l_ = (float) Math.cbrt(l);
        float m_ = (float) Math.cbrt(m);
        float s_ = (float) Math.cbrt(s);

        float L = Math.fma(0.2104542553f, l_, Math.fma(+0.7936177850f, m_, -0.0040720468f * s_));
        float A = Math.fma(1.9779984951f, l_, Math.fma(-2.4285922050f, m_, +0.4505937099f * s_));
        float B = Math.fma(0.0259040371f, l_, Math.fma(+0.7827717662f, m_, -0.8086757660f * s_));

        return new ColorSpaceLab(L, A, B);
    }

    private static ColorSpaceRGB convertToRGBColorSpace(float L, float A, float B) {
        float l_ = L + 0.3963377774f * A + 0.2158037573f * B;
        float m_ = L - 0.1055613458f * A - 0.0638541728f * B;
        float s_ = L - 0.0894841775f * A - 1.2914855480f * B;

        float l = l_ * l_ * l_;
        float m = m_ * m_ * m_;
        float s = s_ * s_ * s_;

        float r = Math.fma(+4.0767416621f, l, Math.fma(-3.3077115913f, m, +0.2309699292f * s));
        float g = Math.fma(-1.2684380046f, l, Math.fma(+2.6097574011f, m, -0.3413193965f * s));
        float b = Math.fma(-0.0041960863f, l, Math.fma(-0.7034186147f, m, +1.7076147010f * s));

        return new ColorSpaceRGB(r, g, b);
    }

    private static float linearize(float channel) {
        return channel <= 0.04045f ? channel / 12.92f : (float) Math.pow((channel + 0.055f) / 1.055f, 2.4f);
    }

    private static float delinearize(float channel) {
        return channel <= 0.0031308f ? channel * 12.92f : Math.fma(1.055f, (float) Math.pow(channel, 1.0f / 2.4f), -0.055f);
    }

    private record ColorSpaceLab(float l, float a, float b) {}
    private record ColorSpaceRGB(float r, float g, float b) {}
}

