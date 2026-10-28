package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TooltipMotionTest {
    @Test void followsMotionAtIntermediatePhysicalPixelsAndSettles(){
        var motion=new TooltipMotion();var owner=new Object();long time=1;
        motion.update(owner,10,20,true,time);motion.update(owner,12,20,true,time+=16_000_000);
        assertTrue(motion.x(2)>10&&motion.x(2)<12);assertEquals(Math.rint(motion.x(2)*2),motion.x(2)*2);
        for(int i=0;i<30;i++)motion.update(owner,12,20,true,time+=16_000_000);
        assertEquals(12,motion.x(2));assertEquals(20,motion.y(2));
    }
    @Test void newTargetsGapsAndReducedMotionNeverFlyAcrossTheScreen(){
        var motion=new TooltipMotion();var owner=new Object();motion.update(owner,10,20,true,1);
        motion.update(new Object(),200,300,true,16_000_001);assertEquals(200,motion.x(3));
        motion.update(owner,30,40,false,32_000_001);assertEquals(30,motion.x(3));
        motion.update(owner,60,70,true,200_000_001);assertEquals(60,motion.x(3));
    }
}
