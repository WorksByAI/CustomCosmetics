package com.customcosmetics.features.nickname;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GradientCompatibilityTest {
    @Test void allColorsMatchReleased184AcrossSettingsTimeChangesAndCacheEvictions()throws Exception{
        assertEquals("8b741691db921ecad437674bae8b006ca724019b9fc2142bd234d7e096ab8585",GradientGoldenWorkload.fingerprint());
    }
}
