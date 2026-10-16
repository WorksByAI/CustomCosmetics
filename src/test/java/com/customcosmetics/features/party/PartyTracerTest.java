// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyTracerTest {
    @Test void locationTracersHaveTheirOwnToggleAndColor(){
        boolean party=com.customcosmetics.config.ConfigManager.partyTracersEnabled,location=com.customcosmetics.config.ConfigManager.locationTracersEnabled;
        int pc=com.customcosmetics.config.ConfigManager.partyTracerColor,lc=com.customcosmetics.config.ConfigManager.locationTracerColor;
        try{
            com.customcosmetics.config.ConfigManager.partyTracersEnabled=false;com.customcosmetics.config.ConfigManager.locationTracersEnabled=true;
            com.customcosmetics.config.ConfigManager.partyTracerColor=0x112233;com.customcosmetics.config.ConfigManager.locationTracerColor=0xAABBCC;
            assertFalse(PartyTracer.enabled(true));assertTrue(PartyTracer.enabled(false));assertEquals(0xFF112233,PartyTracer.color(true));assertEquals(0xFFAABBCC,PartyTracer.color(false));
        }finally{com.customcosmetics.config.ConfigManager.partyTracersEnabled=party;com.customcosmetics.config.ConfigManager.locationTracersEnabled=location;com.customcosmetics.config.ConfigManager.partyTracerColor=pc;com.customcosmetics.config.ConfigManager.locationTracerColor=lc;}
    }
    private final Vec3 forward=new Vec3(0,0,-1),right=new Vec3(1,0,0);
    @Test void ribbonStartsAtCrosshairAndPointsAtTeammateCenter(){
        var target=new Vec3(4,2,-10);var points=PartyTracer.ribbon(forward,right,target,128);
        assertEquals(4,points.size());assertEquals(0,points.get(0).add(points.get(1)).scale(.5).distanceTo(forward.scale(.5)),1e-9);
        assertEquals(0,points.get(2).add(points.get(3)).scale(.5).distanceTo(target),1e-9);
        assertEquals(.0026,points.get(2).distanceTo(points.get(3))/target.length(),1e-9);
    }
    @Test void distantEndpointsStayInsideFarClipWithoutChangingDirection(){
        var target=new Vec3(10000,3000,-20000);var p=PartyTracer.ribbon(forward,right,target,128);
        var end=p.get(2).add(p.get(3)).scale(.5);assertEquals(128,end.length(),1e-8);
        assertEquals(0,end.normalize().distanceTo(target.normalize()),1e-9);
    }
    @Test void centeredBehindAndVerticalTargetsNeverCreateInvalidVertices(){
        for(var target:java.util.List.of(new Vec3(0,0,-10),new Vec3(0,0,10),new Vec3(0,10,0)))
            for(var p:PartyTracer.ribbon(forward,right,target,128))assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z));
        assertTrue(PartyTracer.ribbon(forward,right,Vec3.ZERO,128).isEmpty());
    }
}
