package com.customcosmetics.features.damage;

import com.customcosmetics.config.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageSplashColorTest {
    private final String oldMode = ConfigManager.damageSplashColorMode;
    private final double oldSpeed = ConfigManager.damageSplashRainbowSpeed;
    private final boolean oldNormal = ConfigManager.damageSplashStyleNormal;
    @AfterEach void restore() {
        ConfigManager.damageSplashColorMode = oldMode;
        ConfigManager.damageSplashRainbowSpeed = oldSpeed;
        ConfigManager.damageSplashStyleNormal = oldNormal;
    }

    private static List<Style> styles(FormattedCharSequence sequence) {
        var out = new ArrayList<Style>();
        sequence.accept((index, style, code) -> { out.add(style); return true; });
        return out;
    }

    @Test void worldColorsAnimateWithTheSamePaletteAsPreviewAndReuseStyleObjects() {
        ConfigManager.damageSplashColorMode = "rainbow";
        ConfigManager.damageSplashRainbowSpeed = 1;
        // Each component starts its glyph index at zero; coloring must advance across spans.
        var text = Component.empty();
        for (char digit : "123456".toCharArray()) text.append(Component.literal(String.valueOf(digit)).withStyle(style -> style.withColor(0x123456).withBold(true)));
        var visual = text.getVisualOrderText();
        var first = styles(DamageSplash.coloredSequence(visual, 6, true, 0));
        var later = styles(DamageSplash.coloredSequence(visual, 6, true, 1));
        assertEquals(6, first.size());
        assertNotEquals(first, later);
        for (int i = 0; i < first.size(); i++) {
            assertTrue(first.get(i).isBold());
            assertEquals(DamageColors.rgb(DamageColors.index(i, 6, 0)), first.get(i).getColor().getValue());
        }
        var repeated = styles(DamageSplash.coloredSequence(visual, 6, true, 0));
        for (int i = 0; i < first.size(); i++) assertSame(first.get(i), repeated.get(i));
        var shortCircuit = DamageSplash.coloredSequence(visual, 6, true, 0);
        assertEquals(visual.accept((index, style, code) -> false), shortCircuit.accept((index, style, code) -> false));
        assertEquals(first, styles(shortCircuit));
    }

    @Test void gradientAndUnstyledNormalLabelsKeepTheirOriginalSequence() {
        var visual = Component.literal("1,234").withStyle(style -> style.withColor(0x123456)).getVisualOrderText();
        ConfigManager.damageSplashColorMode = "gradient";
        assertSame(visual, DamageSplash.coloredSequence(visual, 5, true, 1));
        ConfigManager.damageSplashColorMode = "rainbow";
        ConfigManager.damageSplashStyleNormal = false;
        assertSame(visual, DamageSplash.coloredSequence(visual, 5, false, 1));
        ConfigManager.damageSplashStyleNormal = true;
        assertNotEquals(styles(visual), styles(DamageSplash.coloredSequence(visual, 5, false, 1)));
        ConfigManager.damageSplashRainbowSpeed = 0;
        assertEquals(styles(DamageSplash.coloredSequence(visual, 5, false, 0)), styles(DamageSplash.coloredSequence(visual, 5, false, 99)));
    }
}
