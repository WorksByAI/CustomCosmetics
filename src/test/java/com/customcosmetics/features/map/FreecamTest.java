package com.customcosmetics.features.map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreecamTest {
    @Test void movementFollowsViewAndKeepsDiagonalSpeedBounded(){
        assertEquals(new Vec3(0,0,.6),Freecam.step(0,0,1,0,0,false,12));
        var west=Freecam.step(90,0,1,0,0,false,12);assertEquals(-.6,west.x,1e-9);assertEquals(0,west.z,1e-9);
        var up=Freecam.step(0,-90,1,0,0,false,12);assertEquals(.6,up.y,1e-9);
        assertEquals(.6,Freecam.step(0,0,1,1,1,false,12).length(),1e-9);
        assertEquals(1.8,Freecam.step(0,0,1,1,1,true,12).length(),1e-9);
        assertEquals(Vec3.ZERO,Freecam.step(10,50,0,0,0,false,12));
    }
    @Test void invalidSavedSpeedsCannotCreateNonFiniteOrUnboundedCameraPositions(){
        assertEquals(12,Freecam.validSpeed(Double.NaN));assertEquals(12,Freecam.validSpeed(Double.POSITIVE_INFINITY));
        assertEquals(1,Freecam.validSpeed(-50));assertEquals(40,Freecam.validSpeed(1000));
        assertEquals(6,Freecam.step(0,0,1,0,0,true,Double.MAX_VALUE).length(),1e-9);
    }
}
