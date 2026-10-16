// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
/** Player-relative blocks: positive X right, Y up, Z backward. */
public record AccessoryOffset(double x,double y,double z) {
    public static final AccessoryOffset ZERO=new AccessoryOffset(0,0,0);
    public AccessoryOffset {x=axis(x);y=axis(y);z=axis(z);}
    private static double axis(double v){return Double.isFinite(v)?Math.clamp(v,-1,1):0;}
    public AccessoryOffset x(double v){return new AccessoryOffset(v,y,z);}
    public AccessoryOffset y(double v){return new AccessoryOffset(x,v,z);}
    public AccessoryOffset z(double v){return new AccessoryOffset(x,y,v);}
}
