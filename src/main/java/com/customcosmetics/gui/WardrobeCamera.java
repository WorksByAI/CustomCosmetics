// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Local preview controls; they never change the equipped outfit or its sync state. */
public record WardrobeCamera(float yaw,float pitch,float zoom) {
    public static final WardrobeCamera DEFAULT=new WardrobeCamera(22,-8,1);
    public WardrobeCamera {yaw=Float.isFinite(yaw)?(float)Math.IEEEremainder(yaw,360):22;pitch=Float.isFinite(pitch)?Math.clamp(pitch,-35,35):-8;zoom=Float.isFinite(zoom)?Math.clamp(zoom,.65f,1.8f):1;}
    public WardrobeCamera drag(double x,double y){return new WardrobeCamera(yaw+(float)x*.7f,pitch+(float)y*.4f,zoom);}
    public WardrobeCamera zoom(double wheel){return new WardrobeCamera(yaw,pitch,zoom*(float)Math.pow(1.12,wheel));}
}
