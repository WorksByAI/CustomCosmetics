// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyChatPrefixTest {
    @Test void configuredPrefixIsRemovedOnlyAtTheBeginningAndCanContainMoreThanOneCharacter() {
        assertTrue(PartyChatRules.intercept("!! hello",true,"!!"));
        assertEquals("hello",PartyChatRules.body("!! hello","!!"));
        assertFalse(PartyChatRules.intercept("hello !!",true,"!!"));
        assertFalse(PartyChatRules.intercept("`hello",true,"!!"));
        assertTrue(PartyChatRules.intercept("😀你好",true,"😀"));
        assertEquals("你好",PartyChatRules.body("😀你好","😀"));
    }

    @Test void emptyPrefixRoutesOrdinaryChatButNeverInterceptsSlashCommands() {
        assertTrue(PartyChatRules.intercept("hello everyone",true,""));
        assertEquals("hello everyone",PartyChatRules.body("hello everyone",""));
        assertFalse(PartyChatRules.intercept("/p list",true,""));
        assertFalse(PartyChatRules.intercept("/customcosmetics party",true,""));
        assertFalse(PartyChatRules.intercept("/pc original channel",true,"/"));
        assertFalse(PartyChatRules.intercept("hello everyone",false,""));
    }

    @Test void missingOrInvalidPrefixesUseTheDefaultAndAnExplicitEmptyStringRemainsEmpty() {
        assertEquals("`",PartyChatRules.prefix(null));
        assertEquals("`",PartyChatRules.prefix("x\ny"));
        assertEquals("`",PartyChatRules.prefix("x".repeat(17)));
        assertEquals("`",PartyChatRules.prefix("§a"));
        assertEquals("",PartyChatRules.prefix(""));
        assertEquals("😀".repeat(16),PartyChatRules.prefix("😀".repeat(16)));
    }
}
