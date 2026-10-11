package com.customcosmetics.features.blocking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockingStyleTest {
    @Test void styleNamesAndConfigurationValuesRemainDistinct() {
        assertEquals("Default",BlockingStyle.DEFAULT.label); assertEquals("default",BlockingStyle.DEFAULT.value());
        assertEquals("Jigsaw",BlockingStyle.JIGSAW.label); assertEquals("jigsaw",BlockingStyle.JIGSAW.value());
        assertEquals(BlockingStyle.JIGSAW,BlockingStyle.parse(" JIGSAW "));
    }
    @Test void missingOrUnknownValuesKeepTheOriginalStyle() {
        for (String value : new String[]{null,"","unknown","jigsaw-v2"}) assertEquals(BlockingStyle.DEFAULT,BlockingStyle.parse(value));
    }
}
