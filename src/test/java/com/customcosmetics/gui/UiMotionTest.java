// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiMotionTest {
    @Test void restingControlsDoNotPollTheClockAndFinishedTransitionsStop(){
        long[] now={0},calls={0};var motion=new UiMotion(0,()->{calls[0]++;return now[0];});
        for(int i=0;i<100_000;i++)assertEquals(0,motion.value(0,true));assertEquals(0,calls[0]);
        assertEquals(0,motion.value(1,true));now[0]=70_000_000;assertEquals(.875f,motion.value(1,true),.0001);
        now[0]=140_000_000;assertEquals(1,motion.value(1,true));long settled=calls[0];
        for(int i=0;i<100_000;i++)assertEquals(1,motion.value(1,true));assertEquals(settled,calls[0]);
    }
    @Test void reversalPreservesContinuityAndReducedMotionDoesNoTimingWork(){
        long[] now={0};var motion=new UiMotion(0,()->now[0]);motion.value(1,true);now[0]=40_000_000;
        float current=motion.value(1,true);assertEquals(current,motion.value(0,true));now[0]+=140_000_000;assertEquals(0,motion.value(0,true));
        var reduced=new UiMotion(0,()->{throw new AssertionError("Disabled motion read the clock");});assertEquals(1,reduced.value(1,false));
    }
    @Test void springOvershootsSettlesAndStopsTimingEvenAfterRapidReversal(){
        long[] now={0},calls={0};var motion=new UiMotion(0,()->{calls[0]++;return now[0];},UiMotion.Curve.SPRING);
        motion.value(1,true);now[0]=160_000_000;float peak=motion.value(1,true);
        assertTrue(peak>1&&peak<1.2,"Spring must rebound without an excessive jump");
        assertEquals(peak,motion.value(0,true));now[0]+=240_000_000;assertEquals(0,motion.value(0,true));
        long settled=calls[0];for(int i=0;i<100_000;i++)assertEquals(0,motion.value(0,true));assertEquals(settled,calls[0]);
        var disabled=new UiMotion(0,()->{throw new AssertionError("Disabled spring read the clock");},UiMotion.Curve.SPRING);
        assertEquals(1,disabled.value(1,false));
    }
    @Test void oneShotGlideIsFiniteAndNeverPollsAfterFinishing(){
        long[] now={0};int[] calls={0};var motion=new UiMotion(1,()->{calls[0]++;return now[0];},UiMotion.Curve.GLIDE);
        assertEquals(1,motion.value(1,true));assertEquals(0,calls[0]);motion.snap(0);assertEquals(0,motion.value(1,true));
        now[0]=360_000_000;assertEquals(1,motion.value(1,true));int settled=calls[0];
        for(int i=0;i<100_000;i++)assertEquals(1,motion.value(1,true));assertEquals(settled,calls[0]);
    }
    @Test void accentInkMaintainsReadableContrastAtBothExtremes(){
        assertEquals(0xFF101619,UiPalette.create("dark",0xffffff).onAccent());
        assertEquals(0xffffffff,UiPalette.create("light",0).onAccent());
        assertNotEquals(UiPalette.create("dark",0x6db29a).panel(),UiPalette.create("light",0x6db29a).panel());
    }
}
