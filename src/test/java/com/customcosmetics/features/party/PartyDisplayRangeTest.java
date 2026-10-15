// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PartyDisplayRangeTest {
    @Test void zeroKeepsUnlimitedVisibilityAndTheDistanceBoundaryIsInclusive(){
        assertTrue(PartyDisplayRange.visible(0,1e15));assertTrue(PartyDisplayRange.visible(128,128*128));
        assertFalse(PartyDisplayRange.visible(128,128*128+.01));assertTrue(PartyDisplayRange.visible(1,0));
    }
    @Test void malformedLimitsCannotPoisonRenderingAndInvalidCoordinatesStayHidden(){
        assertEquals(0,PartyDisplayRange.normalize(Double.NaN));assertEquals(0,PartyDisplayRange.normalize(-1));assertEquals(30000000,PartyDisplayRange.normalize(1e12));
        assertFalse(PartyDisplayRange.visible(0,Double.NaN));assertFalse(PartyDisplayRange.visible(0,Double.POSITIVE_INFINITY));assertFalse(PartyDisplayRange.visible(0,-1));
    }
}
