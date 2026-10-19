package com.customcosmetics.features.nickname;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationClockTest {
    @Test void highGameFrameRatesDoNotAdvanceAnimationBeyondDisplayRate(){
        long start=System.nanoTime()+2_000_000_000L;
        AnimationClock.update(start,1000,60);assertEquals(1000,AnimationClock.millis());
        for(int i=1;i<=16;i++){AnimationClock.update(start+i*1_000_000L,1000+i,60);assertEquals(1000,AnimationClock.millis());}
        AnimationClock.update(start+17_000_000L,1017,60);assertEquals(1017,AnimationClock.millis());
        long next=start+100_000_000L;AnimationClock.update(next,2000,144);
        for(int i=1;i<=6;i++){AnimationClock.update(next+i*1_000_000L,2000+i,144);assertEquals(2000,AnimationClock.millis());}
        AnimationClock.update(next+7_000_000L,2007,144);assertEquals(2007,AnimationClock.millis());
    }
}
