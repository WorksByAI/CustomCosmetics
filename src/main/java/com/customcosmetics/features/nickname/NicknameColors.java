// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.ConfigManager;

/** A local color preset. Nickname text, bold and unrelated settings are deliberately excluded. */
public record NicknameColors(int start, int end, String style, double shimmerSpeed,
        int shimmerColor, int shimmerHighlightColor, boolean chromaEnabled,
        double chromaSpeed, double chromaSize, double chromaAmount, double chromaLightness, double shimmerSize, double shimmerInterval) {
    public NicknameColors(int start,int end,String style,double shimmerSpeed,int shimmerColor,int shimmerHighlightColor,boolean chromaEnabled,double chromaSpeed,double chromaSize,double chromaAmount,double chromaLightness) {
        this(start,end,style,shimmerSpeed,shimmerColor,shimmerHighlightColor,chromaEnabled,chromaSpeed,chromaSize,chromaAmount,chromaLightness,NicknameGradient.DEFAULT_SIZE,NicknameGradient.DEFAULT_INTERVAL);
    }
    public NicknameColors {
        shimmerSize = NicknameGradient.normalizeSize(shimmerSize);
        shimmerInterval = NicknameGradient.normalizeInterval(shimmerInterval);
        start &= 0xFFFFFF; end &= 0xFFFFFF;
        shimmerColor &= 0xFFFFFF; shimmerHighlightColor &= 0xFFFFFF;
        style = NicknameGradient.normalizeStyle(style);
        shimmerSpeed = bounded(shimmerSpeed, 0, 4, 1);
        chromaSpeed = bounded(chromaSpeed, 0.1, 8, 1);
        chromaSize = bounded(chromaSize, 0.5, 10, 3.1);
        chromaAmount = bounded(chromaAmount, 0, 0.4, 0.2);
        chromaLightness = bounded(chromaLightness, 0.2, 1, 0.8);
    }
    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    public static NicknameColors capture() {
        return new NicknameColors(ConfigManager.nickTweaksGradientStartColor, ConfigManager.nickTweaksGradientEndColor,
            ConfigManager.nickTweaksGradientStyle, ConfigManager.nickTweaksShimmerSpeed,
            ConfigManager.nickTweaksShimmerColor, ConfigManager.nickTweaksShimmerHighlightColor,
            ConfigManager.nickTweaksChromaEnabled, ConfigManager.nickTweaksChromaSpeed,
            ConfigManager.nickTweaksChromaSize, ConfigManager.nickTweaksChromaChroma, ConfigManager.nickTweaksChromaLightness, ConfigManager.nickTweaksShimmerSize,ConfigManager.nickTweaksShimmerInterval);
    }
    public void apply() {
        ConfigManager.nickTweaksEnabled = true;
        ConfigManager.nickTweaksCustomNickColorEnabled = true;
        ConfigManager.nickTweaksGradientStartColor = start;
        ConfigManager.nickTweaksGradientEndColor = end;
        ConfigManager.nickTweaksGradientStyle = style;
        ConfigManager.nickTweaksShimmerSpeed = shimmerSpeed;
        ConfigManager.nickTweaksShimmerSize = shimmerSize;
        ConfigManager.nickTweaksShimmerInterval = shimmerInterval;
        ConfigManager.nickTweaksShimmerColor = shimmerColor;
        ConfigManager.nickTweaksShimmerHighlightColor = shimmerHighlightColor;
        ConfigManager.nickTweaksChromaEnabled = chromaEnabled;
        ConfigManager.nickTweaksChromaSpeed = chromaSpeed;
        ConfigManager.nickTweaksChromaSize = chromaSize;
        ConfigManager.nickTweaksChromaChroma = chromaAmount;
        ConfigManager.nickTweaksChromaLightness = chromaLightness;
    }
}
