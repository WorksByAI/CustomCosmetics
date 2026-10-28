package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiBlurPolicyTest {
    @Test void zeroVanillaSliderStillHasAnEffectiveModBlur(){assertEquals(5,UiBlurPolicy.effective(0,true,true,true));assertEquals(8,UiBlurPolicy.effective(8,true,true,true));}
    @Test void otherScreensDisabledToggleAndTitleMenuRetainTheOriginalValue(){assertEquals(0,UiBlurPolicy.effective(0,false,true,true));assertEquals(0,UiBlurPolicy.effective(0,true,false,true));assertEquals(0,UiBlurPolicy.effective(0,true,true,false));assertEquals(7,UiBlurPolicy.effective(7,false,true,true));}
}
