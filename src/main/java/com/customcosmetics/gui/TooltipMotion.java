// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Render-owned motion, sampled only while a tooltip is actually drawn. */
public final class TooltipMotion {
    private Object owner;
    private long last;
    private double x,y;
    private boolean initialized;
    public void update(Object nextOwner,double targetX,double targetY,boolean animate,long now){
        if(!initialized||owner!=nextOwner||!animate||now-last>100_000_000L||now<last){x=targetX;y=targetY;initialized=true;}
        else if(x!=targetX||y!=targetY){
            double blend=-Math.expm1(-Math.max(0,now-last)/30_000_000.0);
            x+=(targetX-x)*blend;y+=(targetY-y)*blend;
            if(Math.abs(targetX-x)<.001)x=targetX;if(Math.abs(targetY-y)<.001)y=targetY;
        }
        owner=nextOwner;last=now;
    }
    public double x(double pixels){return Math.round(x*pixels)/pixels;}
    public double y(double pixels){return Math.round(y*pixels)/pixels;}
}
