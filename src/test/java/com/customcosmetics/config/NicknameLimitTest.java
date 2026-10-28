package com.customcosmetics.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NicknameLimitTest {
    @Test void rawFormattingAndChineseCharactersCountTowardThirty() {
        String value = "&d" + "昵称".repeat(30);
        assertEquals(value.substring(0, 30), NicknameLimit.limit(value));
        assertEquals("", NicknameLimit.limit(null));
        assertEquals("&dCherry", NicknameLimit.limit("&dCherry"));
    }
    @Test void theLimitDoesNotSplitAnEmoji() {
        assertEquals("x".repeat(29), NicknameLimit.limit("x".repeat(29) + "😀"));
        assertEquals("x".repeat(28) + "😀", NicknameLimit.limit("x".repeat(28) + "😀more"));
    }
}
