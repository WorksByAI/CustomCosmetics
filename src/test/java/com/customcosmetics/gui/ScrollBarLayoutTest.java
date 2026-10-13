package com.customcosmetics.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollBarLayoutTest {
    @Test void dragMapsTopMiddleAndBottomToTheScrollableRange() {
        var bar=ScrollBarLayout.of(100,200,5,15,10);
        int travel=200-bar.thumbLength();
        assertEquals(0,bar.offsetAtPointer(100,0));
        assertEquals(5,bar.offsetAtPointer(100+travel/2.0,0));
        assertEquals(10,bar.offsetAtPointer(100+travel,0));
    }

    @Test void draggingFromInsideThumbPreservesTheGrabPoint() {
        var bar=ScrollBarLayout.of(40,180,6,18,12);
        int original=bar.thumbStart(4);
        assertEquals(4,bar.offsetAtPointer(original+9,9));
    }

    @Test void clickingTrackClampsAtEitherEndAndShortTracksStayUsable() {
        var bar=ScrollBarLayout.of(20,30,1,100,99);
        assertEquals(0,bar.offsetAtPointer(-500,bar.thumbLength()/2));
        assertEquals(99,bar.offsetAtPointer(500,bar.thumbLength()/2));
        var shortBar=ScrollBarLayout.of(10,8,1,4,3);
        assertEquals(8,shortBar.thumbLength());
        assertEquals(0,shortBar.offsetAtPointer(100,0));
    }
}
