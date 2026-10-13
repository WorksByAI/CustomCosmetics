package com.customcosmetics.features.nickname;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NicknameGradientTest {
    @Test void customSweepColorsChangeTheHighlightWhileLeavingTheBaseAndDefaultAppearanceUntouched() {
        var original = new NicknameGradient("shimmer",0x24CFDF,0xEA78C5,1);
        var explicit = new NicknameGradient("shimmer",0x24CFDF,0xEA78C5,1,0xFFCF64,0xFFF0BB);
        var blue = new NicknameGradient("shimmer",0x24CFDF,0xEA78C5,1,0x0055FF,0x66DDFF);
        for (long time=0; time<3400; time+=53) for (int x=0;x<=20;x++)
            assertEquals(original.color(x/20.0,.5,time),explicit.color(x/20.0,.5,time));
        long center = Math.round((.5+.9)/3.5*NicknameGradient.PERIOD_MS);
        int highlight = blue.color(.5,.5,center);
        assertNotEquals(original.color(.5,.5,center),highlight);
        assertTrue((highlight&255)>240); assertTrue(((highlight>>16)&255)<130);
        var otherEdge = new NicknameGradient("shimmer",0x24CFDF,0xEA78C5,1,0xFF0000,0x66DDFF);
        assertNotEquals(blue.color(.6,.5,center),otherEdge.color(.6,.5,center));
        assertEquals(original.color(.5,.5,0),blue.color(.5,.5,0));
    }
    @Test void smoothBaseKeepsEndpointsAndTraversesBlueAndPurpleRatherThanGray() {
        var gradient=new NicknameGradient("smooth",NicknameGradient.PRESET_START,NicknameGradient.PRESET_END,1);
        assertEquals(NicknameGradient.PRESET_START,gradient.color(0,0,0));
        assertEquals(NicknameGradient.PRESET_END,gradient.color(1,0,0));
        int middle=gradient.color(0.5,0.5,0);
        assertTrue((middle & 255)>200);
        assertTrue(((middle>>8)&255)<160);
        assertNotEquals(gradient.color(0.25,0,0),gradient.color(0.3,0,0));
    }
    @Test void highlightTravelsLeftToRightThenLeavesTheOriginalColorsUntouched() {
        var gradient=new NicknameGradient("shimmer",NicknameGradient.PRESET_START,NicknameGradient.PRESET_END,1);
        var base=new NicknameGradient("smooth",gradient.start(),gradient.end(),1);
        long left=Math.round((0.2+0.9)/3.5*NicknameGradient.PERIOD_MS);
        long right=Math.round((0.8+0.9)/3.5*NicknameGradient.PERIOD_MS);
        int a=gradient.color(0.2,0.5,left),b=gradient.color(0.8,0.5,right);
        assertTrue(((a>>16)&255)>230 && ((a>>8)&255)>220);
        assertTrue(((b>>16)&255)>230 && ((b>>8)&255)>220);
        assertEquals(base.color(0.8,0.5,left),gradient.color(0.8,0.5,left));
        assertEquals(base.color(0.2,0.5,right),gradient.color(0.2,0.5,right));
        assertEquals(base.color(0.5,0.5,3000),gradient.color(0.5,0.5,3000));
    }
    @Test void animationIsPeriodicAndZeroSpeedLeavesAStaticGradient() {
        var gradient=new NicknameGradient("shimmer",0x24CFDF,0xEA78C5,1);
        assertEquals(gradient.color(0.4,0.3,1350),gradient.color(0.4,0.3,1350+NicknameGradient.PERIOD_MS));
        var paused=new NicknameGradient("shimmer",gradient.start(),gradient.end(),0);
        assertEquals(paused.color(0.4,0.3,0),paused.color(0.4,0.3,Long.MAX_VALUE));
        assertEquals(gradient.color(0.4,0.3,1350),new NicknameGradient("shimmer",gradient.start(),gradient.end(),2).color(0.4,0.3,675));
    }
    @Test void grayscaleAndMalformedSettingsHaveStableFiniteFallbacks() {
        assertEquals(0x808080,new NicknameGradient("smooth",0,0xFFFFFF,1).color(0.5,0,0));
        assertEquals("character",new NicknameGradient(null,0,0,Double.NaN).style());
        assertEquals(1,new NicknameGradient("shimmer",0,0,Double.POSITIVE_INFINITY).speed());
        assertEquals(4,new NicknameGradient("shimmer",0,0,99).speed());
        assertEquals(0,new NicknameGradient("shimmer",0,0,-2).speed());
    }
}
