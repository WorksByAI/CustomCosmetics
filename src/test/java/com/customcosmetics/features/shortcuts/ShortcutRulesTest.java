// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.shortcuts;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static com.customcosmetics.features.shortcuts.ShortcutRules.*;

class ShortcutRulesTest {
    private Entry alias(String name, String command) { return new Entry(name, Kind.ALIAS, name, command, true); }
    private Entry argument(String name, String replacement) { return new Entry(name, Kind.ARGUMENT, name, replacement, true); }

    @Test void aliasesAppendTrailingArgumentsAndMatchWholeCommandNames() {
        var rules = new ShortcutRules(List.of(alias("home", "/warp home"), alias("pa", "/party accept")));
        assertEquals("warp home", rules.expand("home"));
        assertEquals("party accept Alice extra", rules.expand("pa Alice extra"));
        assertEquals("homeward", rules.expand("homeward"));
        assertEquals("party home", rules.expand("party home"));
    }
    @Test void positionalAndRemainderArgumentsAreLiteralText() {
        var rules = new ShortcutRules(List.of(alias("tell", "/msg $2 $1 $*")));
        assertEquals("msg B A A B", rules.expand("tell A B"));
        assertEquals("msg Bob $path $path Bob", rules.expand("tell $path Bob"));
        assertThrows(IllegalArgumentException.class, () -> rules.expand("tell Alice"));
    }
    @Test void argumentTokensReplaceExactlyOnceIncludingSlashCommandToken() {
        var rules = new ShortcutRules(List.of(argument("/v", "/visit"), argument("@friend", "Alice"),
            argument("Alice", "Bob")));
        assertEquals("visit Alice", rules.expand("v @friend"));
        assertEquals("visit Bob", rules.expand("v Alice"));
        assertEquals("msg user@friend @friend!", rules.expand("msg user@friend @friend!"));
        assertEquals("visit  Alice", rules.expand("v  @friend"));
    }
    @Test void aliasExpansionPrecedesArgumentReplacement() {
        var rules = new ShortcutRules(List.of(alias("go", "/v"), argument("/v", "/visit"), argument("@friend", "Alice")));
        assertEquals("visit Alice", rules.expand("go @friend"));
    }
    @Test void detectsDirectIndirectAndDisabledAliasCycles() {
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("a", "/a")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("a", "/b"), alias("b", "/a")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("a", "/b"),
            new Entry("b", Kind.ALIAS, "b", "/a", false)), Set.of()));
        var untrusted = new ShortcutRules(List.of(alias("a", "/b"), alias("b", "/a")));
        assertThrows(IllegalArgumentException.class, () -> untrusted.expand("a"));
    }
    @Test void protectsOwnCommandsButAllowsServerCommandOverrides() {
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("a", "/warp home"),
            new Entry("different", Kind.ALIAS, "a", "/hub", false)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("ccos", "/hub")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("customcosmetics", "/hub")), Set.of()));
        assertDoesNotThrow(() -> validate(List.of(alias("tp", "/hub")), Set.of("tp")));
        assertDoesNotThrow(() -> validate(List.of(argument("/v", "/visit")), Set.of("v")));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(argument("/ccos", "/hub")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(argument("/customcosmetics", "/hub")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("v", "/hub"), argument("/v", "/visit")), Set.of()));
        assertDoesNotThrow(() -> validate(List.of(new Entry("a", Kind.ALIAS, "tp", "/hub", false)), Set.of("tp")));
        assertDoesNotThrow(() -> validate(List.of(alias("base", "/tp 1 2 3")), Set.of("tp")));
    }
    @Test void disabledAndEmptyRulesPreserveInputInstance() {
        String input = new String("anything test");
        assertSame(input, new ShortcutRules(List.of()).expand(input));
        assertSame(input, new ShortcutRules(List.of(new Entry("a", Kind.ALIAS, "anything", "/hub", false))).expand(input));
    }
    @Test void rejectsControlCharactersDynamicCommandNamesAndOversizedExpansion() {
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("a", "/hub\nwarp home")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(alias("a", "/$1")), Set.of()));
        var rules = new ShortcutRules(List.of(alias("a", "/msg " + "x".repeat(250))));
        assertThrows(IllegalArgumentException.class, () -> rules.expand("a overflow"));
    }
    @Test void chainedAliasesWorkWithoutReinterpretingArgumentTokens() {
        var rules = new ShortcutRules(List.of(alias("a", "/b"), alias("b", "/warp")));
        assertEquals("warp home", rules.expand("a home"));
    }
    @Test void keyChordConflictsAreIndependentOfRecordedOrder() {
        var first = new Entry("1", Kind.KEY, "key.keyboard.left.control + key.keyboard.k", "/hub", true);
        var duplicate = new Entry("2", Kind.KEY, "key.keyboard.k + key.keyboard.left.control", "/warp home", false);
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(first, duplicate), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(List.of(new Entry("1", Kind.KEY, "key.keyboard.k + key.keyboard.k", "/hub", true)), Set.of()));
        assertDoesNotThrow(() -> validate(List.of(new Entry("scan", Kind.KEY, "scancode.123", "/hub", true)), Set.of()));
    }
}
