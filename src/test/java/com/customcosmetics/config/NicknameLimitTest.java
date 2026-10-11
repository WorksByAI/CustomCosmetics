package com.customcosmetics.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NicknameLimitTest {
    @Test void rawFormattingAndChineseCharactersCountTowardForty() {
        String value = "&d" + "昵称".repeat(30);
        assertEquals(value.substring(0, 40), NicknameLimit.limit(value));
        assertEquals("", NicknameLimit.limit(null));
        assertEquals("&dCherry", NicknameLimit.limit("&dCherry"));
    }
    @Test void theLimitDoesNotSplitAnEmoji() {
        assertEquals("x".repeat(39), NicknameLimit.limit("x".repeat(39) + "😀"));
        assertEquals("x".repeat(38) + "😀", NicknameLimit.limit("x".repeat(38) + "😀more"));
    }
}
