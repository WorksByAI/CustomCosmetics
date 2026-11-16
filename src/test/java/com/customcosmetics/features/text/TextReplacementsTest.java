// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.text;

import com.customcosmetics.features.nickname.RankText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextReplacementsTest {
    @Test void replacesLiteralLongestMatchesOnceAndCanHideText() {
        var rules = new TextReplacements.Rules(Map.of(
            "HP", "Health", "Health", "Life", "HP+1", "Heart", "[hide]", "", "💎", "Gem"));
        assertEquals("Heart Health Life hp  Gem", rules.replace("HP+1 HP Health hp [hide] 💎"));
        assertSame("unchanged", rules.replace("unchanged"));
    }

    @Test void preservesSurroundingStylesAndInheritsTheFirstMatchedGlyphStyle() {
        var hovered = Style.EMPTY.withBold(true).withHoverEvent(new HoverEvent.ShowText(Component.literal("details")));
        var italic = Style.EMPTY.withItalic(true);
        var input = FormattedCharSequence.composite(
            FormattedCharSequence.forward("🙂", italic),
            FormattedCharSequence.forward("Sk", hovered),
            FormattedCharSequence.forward("y!", italic));
        var rules = new TextReplacements.Rules(Map.of("Sky", "Cloud", "Cloud", "Other"));
        var result = rules.replace(input);
        var rendered = new StringBuilder();
        result.accept((index, style, codepoint) -> {
            assertEquals(index > 1 && index < 7 ? hovered : italic, style);
            rendered.appendCodePoint(codepoint);
            return true;
        });
        assertEquals("🙂Cloud!", rendered.toString());
        assertEquals("🙂Sky!", RankText.component(input).getString());
    }

    @Test void validatesStoredRulesAndBoundsExpansion() {
        assertEquals(Map.of("keep", ""), TextReplacements.normalize(Map.of("keep", "", " ", "x", "same", "same", "new\nline", "x")));
        var rules = new TextReplacements.Rules(Map.of("a", "b".repeat(256)));
        String excessive = "a".repeat(512);
        assertSame(excessive, rules.replace(excessive));
    }

    @Test void hidesWholeAccountNamesWithoutChangingSourceStyles() {
        var aliases = new TextReplacements.Rules(Map.of("alice", "Guest-1", "bob", "Mage"), true);
        assertEquals("[MVP+] Guest-1: Mage, not Alice123 or x_Bob.",
            aliases.replace("[MVP+] ALICE: Bob, not Alice123 or x_Bob."));
        var colored = Style.EMPTY.withColor(0xFFFFFF);
        var input = FormattedCharSequence.composite(FormattedCharSequence.forward("A", colored),
            FormattedCharSequence.forward("lice!", Style.EMPTY));
        var output = aliases.replace(input);
        output.accept((index, style, codepoint) -> {
            assertEquals(index < "Guest-1".length() ? colored : Style.EMPTY, style);
            return true;
        });
        assertEquals("Guest-1!", RankText.component(output).getString());
        assertEquals("Alice!", RankText.component(input).getString());
    }

    @Test void appliesOnlyThePersonalAliasColorsAndKeepsTheOriginalNameHover() {
        var aliases = new TextReplacements.Rules(Map.of("alice", "&6Time&#FF66BBDeo&r!", "bob", "&6Guest"), true, Set.of("alice"));
        var hover = new HoverEvent.ShowText(Component.literal("player info"));
        var style = Style.EMPTY.withColor(0xFFFFFF).withHoverEvent(hover);
        var output = aliases.replace(FormattedCharSequence.composite(
            FormattedCharSequence.forward("Alice", style), FormattedCharSequence.forward(" Bob", Style.EMPTY)));
        assertEquals("TimeDeo! &6Guest", RankText.component(output).getString());
        assertEquals("TimeDeo! &6Guest", aliases.replace("Alice Bob"));
        output.accept((index, rendered, codepoint) -> {
            if (index < 8) {
                assertEquals(hover, rendered.getHoverEvent());
                assertEquals(index < 4 ? 0xFFAA00 : index < 7 ? 0xFF66BB : 0xFFFFFF, rendered.getColor().getValue());
            } else assertEquals(Style.EMPTY, rendered);
            return true;
        });
    }
}
