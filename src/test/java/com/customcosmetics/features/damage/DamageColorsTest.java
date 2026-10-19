package com.customcosmetics.features.damage;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageColorsTest {
    @Test void rainbowCoversMultipleColorsAndCyclesAtTheConfiguredSpeed() {
        assertEquals(0xFF2626, DamageColors.rgb(0));
        assertNotEquals(DamageColors.rgb(0), DamageColors.rgb(85));
        assertNotEquals(DamageColors.rgb(85), DamageColors.rgb(170));
        assertEquals(0, DamageColors.phase(0, 1));
        assertEquals(64, DamageColors.phase(1, 1));
        assertEquals(128, DamageColors.phase(1, 2));
        assertEquals(0, DamageColors.phase(4, 1));
        assertEquals(0, DamageColors.phase(1, 4));
        for (double time : new double[]{-100, 0, .5, 1e9}) assertEquals(0, DamageColors.phase(time, 0));
    }

    @Test void invalidSettingsHaveSafeFallbacksAndAllIndicesWrap() {
        assertEquals("gradient", DamageColors.normalize(null));
        assertEquals("gradient", DamageColors.normalize("invalid"));
        assertEquals("rainbow", DamageColors.normalize("RAINBOW"));
        assertEquals(1, DamageColors.speed(Double.NaN));
        assertEquals(0, DamageColors.speed(-1));
        assertEquals(4, DamageColors.speed(100));
        assertEquals(0, DamageColors.phase(Double.POSITIVE_INFINITY, 1));
        for (double time : new double[]{-3, .1, 5, 1e300}) for (int i = 0; i < 48; i++) {
            int index = DamageColors.index(i, 48, DamageColors.phase(time, 1));
            assertTrue(index >= 0 && index < 256);
            assertEquals(0, DamageColors.rgb(index) & 0xFF000000);
        }
        assertEquals(DamageColors.rgb(0), DamageColors.rgb(256));
    }

    @Test void rainbowRespectsUnicodeFormattingAndTheNormalDamageSwitch() {
        var hit = DamageText.parse("✧1234567✧⚔");
        var colored = DamageText.glyphs(hit, true, "comma", "🔥", false, 0, 0, 0x123456, "rainbow", 0, 1);
        assertEquals("🔥1.2M🔥⚔", colored.stream().map(DamageText.Glyph::text).reduce("", String::concat));
        assertEquals(7, colored.size());
        assertTrue(colored.stream().map(DamageText.Glyph::color).distinct().count() > 3);
        assertNotEquals(colored, DamageText.glyphs(hit, true, "comma", "🔥", false, 0, 0, 0x123456, "rainbow", 1, 1));
        var normal = DamageText.parse("1234");
        var original = DamageText.glyphs(normal, false, "underscore", "✧", false, 0, 0, 0x123456, "rainbow", 1, 1);
        assertTrue(original.stream().allMatch(glyph -> glyph.color() == 0x123456));
        var styled = DamageText.glyphs(normal, false, "underscore", "✧", true, 0, 0, 0x123456, "rainbow", 1, 1);
        assertTrue(styled.stream().map(DamageText.Glyph::color).distinct().count() > 1);
        assertEquals("1_234", styled.stream().map(DamageText.Glyph::text).reduce("", String::concat));
        for (String mode : List.of("gradient", "invalid"))
            assertEquals(DamageText.glyphs(hit, false, "comma", "✧", true, 0x123456, 0x654321, 0),
                DamageText.glyphs(hit, false, "comma", "✧", true, 0x123456, 0x654321, 0, mode, 1, 4));
    }
}
