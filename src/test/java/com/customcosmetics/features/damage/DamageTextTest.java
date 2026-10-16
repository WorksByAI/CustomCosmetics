package com.customcosmetics.features.damage;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DamageTextTest {
    @Test void parsesCriticalNormalAndSpecialDamageWithoutLosingPrecision(){
        var hit=DamageText.parse("✧1,234,567✧");assertNotNull(hit);assertTrue(hit.critical());assertEquals(new BigDecimal("1234567"),hit.amount());
        assertEquals(new BigDecimal("1250000"),DamageText.parse("1.25M⚔").amount().setScale(0));
        assertEquals("⚔",DamageText.parse("1.25M⚔").suffix());assertFalse(DamageText.parse("36").critical());
        assertEquals(new BigDecimal("1200.5"),DamageText.parse("1,200.5").amount());
    }
    @Test void rejectsHealthBarsNamesInvalidGroupingAndHostileValues(){
        for(String text:List.of("ToryHi","Lv100 12345","123❤","+123","123+","1,23","1,,234","NaN","Infinity","1e20","-1","0","12345/20000","✧9999999999999999999999999999999✧"," ","123\n","1.2.3"))assertNull(DamageText.parse(text),text);
        assertNull(DamageText.parse(null));assertNull(DamageText.parse("1".repeat(1000)));
    }
    @Test void formatsCompactUnitsAndCarriesRoundedBoundaries(){
        assertEquals("1.2M",DamageText.number(new BigDecimal("1234567"),true,"comma"));
        assertEquals("1M",DamageText.number(new BigDecimal("999999"),true,"none"));
        for(int unit=1;unit<=5;unit++)assertEquals("1"+"KMBTQ".charAt(unit-1),DamageText.number(BigDecimal.ONE.scaleByPowerOfTen(unit*3),true,"none"));
        assertEquals("1,234,567.25",DamageText.number(new BigDecimal("1234567.25"),false,"comma"));
        assertEquals("1_234",DamageText.number(new BigDecimal("1234"),false,"underscore"));
        assertEquals("1 234",DamageText.number(new BigDecimal("1234"),false,"space"));
        assertEquals("1234",DamageText.number(new BigDecimal("1234"),false,"none"));
    }
    @Test void gradientsRespectUnicodeSymbolsAndNormalColor(){
        var hit=DamageText.parse("✧123✧");var glyphs=DamageText.glyphs(hit,false,"none","🔥",false,0xFF0000,0x0000FF,0xFFFFFF);
        assertEquals(5,glyphs.size());assertEquals("🔥",glyphs.getFirst().text());assertEquals(0xFF0000,glyphs.getFirst().color());assertEquals(0x0000FF,glyphs.getLast().color());
        assertEquals(0x123456,DamageText.glyphs(DamageText.parse("123"),false,"none","✧",false,0,0,0x123456).getFirst().color());
        assertEquals(0,DamageText.glyphs(DamageText.parse("123"),false,"none","✧",true,0,0xFFFFFF,0x123456).getFirst().color());
        assertEquals("",DamageText.symbol(""));assertEquals("✧",DamageText.symbol("§"));assertEquals("✧",DamageText.symbol("\n"));assertEquals("🔥",DamageText.symbol("🔥x"));
        assertEquals(0xFF0000,DamageText.blend(0xFF0000,0,Double.NaN));
    }
    @Test void allAnimationFramesAreFiniteBoundedAndExpire(){
        for(String style:DamageAnimation.STYLES)for(boolean crit:new boolean[]{true,false})for(int i=0;i<=100;i++){
            var frame=DamageAnimation.frame(style,i/100.0,crit,.3,-.2,.5);
            assertTrue(Double.isFinite(frame.x())&&Double.isFinite(frame.y())&&Double.isFinite(frame.z()));assertTrue(frame.alpha()>=0&&frame.alpha()<=1);assertTrue(frame.scale()>0&&frame.scale()<=1.6);
            assertEquals(0,DamageAnimation.frame(style,1,crit,0,0,0).alpha());
        }
        assertEquals("default",DamageAnimation.normalize(null));assertEquals("arc",DamageAnimation.normalize("ARC"));assertEquals("default",DamageAnimation.normalize("broken"));
        assertEquals(1,DamageAnimation.bounded(Double.NaN,1,.25,3));assertEquals(.25,DamageAnimation.bounded(-3,1,.25,3));
    }
}
