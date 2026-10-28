// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Fit visual proportions, not the unchanged collision box, to a portrait preview. */
public final class PreviewModelFit {
    private PreviewModelFit(){}
    public static double scale(int width,int height,double modelWidth,double modelHeight){
        return Math.max(.01,Math.min(Math.max(1,width-12)/(Math.max(.01,modelWidth)*1.15),Math.max(1,height-8)/(Math.max(.01,modelHeight)*1.12)));
    }
}
