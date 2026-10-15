// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

/** A local visibility filter; never changes the shared party roster or network subscriptions. */
public final class PartyDisplayRange {
    public static final double MAX=30000000;
    private PartyDisplayRange(){}
    public static double normalize(double distance){return Double.isFinite(distance)?Math.clamp(distance,0,MAX):0;}
    public static boolean visible(double limit,double distanceSquared){
        double bounded=normalize(limit);
        return Double.isFinite(distanceSquared)&&distanceSquared>=0&&(bounded==0||distanceSquared<=bounded*bounded);
    }
}
