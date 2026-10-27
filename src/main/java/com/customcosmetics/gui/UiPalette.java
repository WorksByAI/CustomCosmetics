// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Immutable colors calculated when preferences change, never per widget/frame. */
public record UiPalette(int panel,int sidebar,int surface,int border,int accent,int accentSoft,
                        int text,int muted,int dim,int onAccent,int hover) {
    public static UiPalette create(String mode,int rgb){
        int accent=0xFF000000|(rgb&0xFFFFFF);
        int ink=contrastInk(rgb);
        return "light".equals(mode)
            ?new UiPalette(0xFFFAFBFC,0xFFF0F2F4,0xFFFFFFFF,0xFFDADFE5,accent,0x24000000|(rgb&0xFFFFFF),0xFF242B33,0xFF65717D,0xFF89949F,ink,0xFFE9EDF1)
            :new UiPalette(0xFF1A1E23,0xFF14181C,0xFF22272D,0xFF333A42,accent,0x24000000|(rgb&0xFFFFFF),0xFFE8ECF0,0xFFA2ACB6,0xFF798592,ink,0xFF303840);
    }
    private static int contrastInk(int rgb){
        double luminance=.2126*linear(rgb>>16&255)+.7152*linear(rgb>>8&255)+.0722*linear(rgb&255);
        return (luminance+.05)/.05 >= 1.05/(luminance+.05)?0xFF101619:0xFFFFFFFF;
    }
    private static double linear(int color){double value=color/255d;return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);}
}
