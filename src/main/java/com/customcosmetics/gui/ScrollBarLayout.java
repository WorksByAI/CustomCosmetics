// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Shared geometry and pointer mapping for the custom screen scrollbars. */
final class ScrollBarLayout {
    private final int trackStart;
    private final int trackLength;
    private final int thumbLength;
    private final int maxOffset;

    private ScrollBarLayout(int trackStart,int trackLength,int thumbLength,int maxOffset) {
        this.trackStart=trackStart;
        this.trackLength=trackLength;
        this.thumbLength=thumbLength;
        this.maxOffset=maxOffset;
    }

    static ScrollBarLayout of(int trackStart,int trackLength,int visible,int total,int maxOffset) {
        int length=Math.max(0,trackLength);
        int thumb=total<=0?length:(int)Math.round(length*Math.min(1,Math.max(0,visible)/(double)total));
        thumb=Math.min(length,Math.max(Math.min(12,length),thumb));
        return new ScrollBarLayout(trackStart,length,thumb,Math.max(0,maxOffset));
    }

    int thumbLength(){return thumbLength;}
    int thumbStart(int offset) {
        int travel=Math.max(0,trackLength-thumbLength);
        if(travel==0||maxOffset==0)return trackStart;
        return trackStart+(int)Math.round(travel*Math.clamp(offset,0,maxOffset)/(double)maxOffset);
    }

    int offsetAtPointer(double pointer,int grabOffset) {
        int travel=Math.max(0,trackLength-thumbLength);
        if(travel==0||maxOffset==0)return 0;
        double thumbTop=Math.clamp(pointer-trackStart-grabOffset,0,travel);
        return (int)Math.round(thumbTop*maxOffset/travel);
    }
}
