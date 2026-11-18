// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkyBlockLevelTest {
    @Test void tierBoundariesMatchHypixel() {
        assertEquals(0xAAAAAA, SkyBlockLevel.tierColor(39));
        assertEquals(0xFFFFFF, SkyBlockLevel.tierColor(40));
        assertEquals(0xFF5555, SkyBlockLevel.tierColor(479));
        assertEquals(0xAA0000, SkyBlockLevel.tierColor(480));
        assertEquals(0xAA0000, SkyBlockLevel.tierColor(600));
    }

    @Test void numbersKeepTheirSpacingAndEachRepeatedDigitCanHaveItsOwnColor() {
        var text = Component.literal("⛅ [").withColor(0x555555)
            .append(Component.literal("40").withColor(0xFFFFFF).withStyle(net.minecraft.ChatFormatting.BOLD))
            .append(Component.literal("] [MVP+] Alice: 40").withColor(0x55FFFF));
        var display = new SkyBlockLevel(555, List.of(0xFF0000, 0x00FF00, 0x0000FF));
        var replaced = SkyBlockLevelAppearance.replaceDigits(text, 3, 5, display);
        assertEquals("⛅ [555] [MVP+] Alice: 40", replaced.getString());
        var digitStyles = new ArrayList<Style>();
        RankText.sequence(replaced).accept((i, style, cp) -> { if (cp == '5') digitStyles.add(style); return true; });
        assertEquals(display.colors(), digitStyles.stream().map(s -> s.getColor().getValue()).toList());
        assertTrue(digitStyles.stream().allMatch(Style::isBold));
    }

    @Test void malformedRemoteSettingsAreIgnored() {
        for (String unsafe : List.of("{\"value\":-1,\"colors\":[12]}", "{\"value\":55,\"colors\":[12]}",
                "{\"value\":7,\"colors\":[1.1]}", "{\"value\":0,\"colors\":[16777216]}", "{\"value\":\"9\",\"colors\":[3]}")) {
            assertNull(SkyBlockLevel.decode(JsonParser.parseString(unsafe)));
        }
    }

    @Test void onlyLevelPrefixesForKnownPlayersAreRewritten() throws ReflectiveOperationException {
        var field = SkyBlockLevelAppearance.class.getDeclaredField("known");
        field.setAccessible(true);
        var original = field.get(null);
        field.set(null, Map.of(
            "alice", new SkyBlockLevel(555, List.of(1, 2, 3)),
            "toryhi", new SkyBlockLevel(1314, List.of(1, 2, 3, 4))));
        try {
            assertEquals("[12:34] [555] [MVP++] Alice ✦: [40] Alice",
                SkyBlockLevelAppearance.chat(Component.literal("[12:34] [40] [MVP++] Alice ✦: [40] Alice")).getString());
            assertEquals("[21:57] [1314] ᛝ [Cute] [MVP++] ToryHi: 1",
                SkyBlockLevelAppearance.chat(Component.literal("[21:57] [523] ᛝ [Cute] [MVP++] ToryHi: 1")).getString());
            assertEquals("[1314] ✬ [MVP++] ToryHi: [523] ᛝ [Cute] [MVP++] ToryHi",
                SkyBlockLevelAppearance.chat(Component.literal("[523] ✬ §6[MVP§a++§6] ToryHi§f: [523] ᛝ [Cute] [MVP++] ToryHi")).getString());
            assertEquals("[555♲] Alice", SkyBlockLevelAppearance.nametag(Component.literal("[40♲] Alice")).getString());
            assertEquals("555 [MVP+] Alice", SkyBlockLevelAppearance.tab(Component.literal("40 [MVP+] Alice"), "Alice").getString());
            for (var unrelated : List.of("[40] Bob: [40] Alice", "[Lv40] Alice: Hi", "[40] Alice Mention: Hi", "[40] Alicia: Hi")) {
                var input = Component.literal(unrelated);
                assertSame(input, SkyBlockLevelAppearance.chat(input));
            }
        } finally {
            field.set(null, original);
        }
    }
}
