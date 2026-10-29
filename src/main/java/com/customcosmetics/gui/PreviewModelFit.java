// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Fit visual proportions, not the unchanged collision box, to a portrait preview. */
public final class PreviewModelFit {
    private PreviewModelFit(){}
    public static double projectedWidth(double width,double depth,double yaw){
        double angle=Math.toRadians(yaw);return Math.abs(Math.cos(angle))*width+Math.abs(Math.sin(angle))*depth;
    }
    public static double projectedHeight(double height,double width,double depth,double yaw,double pitch){
        double angle=Math.toRadians(pitch);return Math.abs(Math.cos(angle))*height+Math.abs(Math.sin(angle))*projectedWidth(depth,width,yaw);
    }
    public static double scale(int width,int height,double modelWidth,double modelHeight){
        return Math.max(.01,Math.min(Math.max(1,width-12)/(Math.max(.01,modelWidth)*1.15),Math.max(1,height-8)/(Math.max(.01,modelHeight)*1.12)));
    }
}
