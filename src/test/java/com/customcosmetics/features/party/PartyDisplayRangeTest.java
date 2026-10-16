// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PartyDisplayRangeTest {
    @Test void closeNametagsHideAtAndInsideTheBoundaryAndZeroDisablesOnlyTheMinimum(){
        assertFalse(PartyDisplayRange.nametagVisible(8,0));assertFalse(PartyDisplayRange.nametagVisible(8,64));
        assertTrue(PartyDisplayRange.nametagVisible(8,64.01));assertTrue(PartyDisplayRange.nametagVisible(0,0));
        assertTrue(PartyDisplayRange.nametagVisible(0,1e15));
        assertTrue(PartyDisplayRange.visible(8,64),"The existing maximum display distance has an independent inclusive boundary");
    }
    @Test void invalidNametagDistancesCannotEnterRendering(){
        assertFalse(PartyDisplayRange.nametagVisible(0,Double.NaN));assertFalse(PartyDisplayRange.nametagVisible(8,Double.POSITIVE_INFINITY));
        assertFalse(PartyDisplayRange.nametagVisible(0,-1));assertTrue(PartyDisplayRange.nametagVisible(-8,0));
    }
    @Test void zeroKeepsUnlimitedVisibilityAndTheDistanceBoundaryIsInclusive(){
        assertTrue(PartyDisplayRange.visible(0,1e15));assertTrue(PartyDisplayRange.visible(128,128*128));
        assertFalse(PartyDisplayRange.visible(128,128*128+.01));assertTrue(PartyDisplayRange.visible(1,0));
    }
    @Test void malformedLimitsCannotPoisonRenderingAndInvalidCoordinatesStayHidden(){
        assertEquals(0,PartyDisplayRange.normalize(Double.NaN));assertEquals(0,PartyDisplayRange.normalize(-1));assertEquals(30000000,PartyDisplayRange.normalize(1e12));
        assertFalse(PartyDisplayRange.visible(0,Double.NaN));assertFalse(PartyDisplayRange.visible(0,Double.POSITIVE_INFINITY));assertFalse(PartyDisplayRange.visible(0,-1));
    }
}
