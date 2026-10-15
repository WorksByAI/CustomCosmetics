// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyNametagScaleTest {
    @Test void fixedScreenSizeCancelsDistanceIncludingTheFarMarkerAnchorClamp() {
        double near=PartyNametagScale.scale("fixed",1,8,8)/8;
        double far=PartyNametagScale.scale("fixed",1,10000,128)/128;
        assertEquals(near,far,1e-8);
        assertEquals(2*far,PartyNametagScale.scale("fixed",2,10000,128)/128,1e-8);
    }

    @Test void distanceScalingShrinksNaturallyWithoutAHiddenMinimumSize() {
        double near=PartyNametagScale.scale("distance",1,8,8)/8;
        double far=PartyNametagScale.scale("distance",1,8000,128)/128;
        assertEquals(near/1000,far,1e-10);
        assertEquals(.025,PartyNametagScale.scale("distance",1,8,8),1e-8);
    }

    @Test void badConfigurationHasSafeDefaultsAndCannotIntroduceNaNOrInfiniteTransforms() {
        assertEquals("fixed",PartyNametagScale.mode("unexpected"));
        assertEquals("fixed",PartyNametagScale.mode(null));
        assertEquals(1,PartyNametagScale.size(Double.NaN));
        assertEquals(.5,PartyNametagScale.size(-5));assertEquals(3,PartyNametagScale.size(10));
        assertTrue(Float.isFinite(PartyNametagScale.scale("fixed",1,0,0)));
    }
}
