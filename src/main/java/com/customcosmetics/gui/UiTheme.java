// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;

/** Preferences are local; refreshing the palette happens only when a screen is built or edited. */
public final class UiTheme {
    private static String mode;
    private static int accent;
    private static UiPalette palette;
    private UiTheme(){}
    public static void refresh(){
        String next="light".equals(ConfigManager.uiThemeMode)?"light":"dark";
        int color=ConfigManager.uiAccentColor&0xFFFFFF;
        if(palette!=null&&next.equals(mode)&&color==accent)return;
        mode=next;accent=color;palette=UiPalette.create(mode,accent);
        PinkUi.PANEL=palette.panel();PinkUi.SIDEBAR=palette.sidebar();PinkUi.CARD=palette.surface();PinkUi.BORDER=palette.border();
        PinkUi.PINK=palette.accent();PinkUi.PINK_DARK=palette.accentSoft();PinkUi.TEXT=palette.text();
        PinkUi.MUTED=palette.muted();PinkUi.DIM=palette.dim();PinkUi.INK=palette.onAccent();PinkUi.HOVER=palette.hover();
    }
    public static void accent(int rgb){ConfigManager.uiAccentColor=rgb&0xFFFFFF;refresh();ConfigManager.requestSave();}
    public static void mode(String value){ConfigManager.uiThemeMode="light".equals(value)?"light":"dark";refresh();ConfigManager.requestSave();}
}
