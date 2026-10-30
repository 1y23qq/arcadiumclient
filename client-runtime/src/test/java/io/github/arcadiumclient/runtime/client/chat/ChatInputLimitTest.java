package io.github.arcadiumclient.runtime.client.chat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

class ChatInputLimitTest {
    @AfterEach void restoreEmoji() { EmojiText.setEnabled(true); }

    @Test void regularAsciiAndCjkUseTheActualProtocolCharacterLimit() {
        assertEquals(100, ChatInputLimit.text("a".repeat(101), false).length());
        assertEquals("あ".repeat(100), ChatInputLimit.text("あ".repeat(101), false));
        assertEquals("", ChatInputLimit.insertion("x".repeat(100), 100, 100, "あ", false));
    }

    @Test void emojiExpansionIsBudgetedBeforeItReachesTheServer() {
        char glyph = EmojiText.entries().getFirst().glyph();
        String base = "a".repeat(90);
        assertEquals(String.valueOf(glyph), ChatInputLimit.insertion(base, 90, 90, "" + glyph, false));
        assertEquals("", ChatInputLimit.insertion(base + "x", 91, 91, "" + glyph, false));
        assertEquals("😀", ChatInputLimit.insertion(base, 90, 90, "😀x", false));
        assertTrue(ChatInputLimit.fits(base + "😀", false));
    }

    @Test void replacementsAccountForBothSidesAndPreserveWholeCodepoints() {
        assertEquals("😀", ChatInputLimit.insertion("a".repeat(100), 90, 100, "😀x", false));
        assertEquals("a", ChatInputLimit.text("a\ud83d", false));
        assertFalse(ChatInputLimit.fits("a\ud83d", false));
        assertEquals(0, ChatInputLimit.boundary("😀x", 1, 2));
        assertEquals(2, ChatInputLimit.boundary("😀x", 1, 0));
    }

    @Test void localCommandBypassAlsoHonorsUtf8EncodingBoundaries() {
        assertEquals(100, ChatInputLimit.text("/" + "a".repeat(101), false).length());
        assertEquals(32767, ChatInputLimit.text("/" + "a".repeat(40000), true).length());
        String japanese = ChatInputLimit.text("/" + "あ".repeat(20000), true);
        assertEquals(10923, japanese.length());
        assertTrue(ChatInputLimit.fits(japanese, true));
        assertFalse(ChatInputLimit.fits(japanese + "あ", true));
    }

    @Test void disabledEmojiLeavesNormalSupplementaryTextWithinUtf16Budget() {
        EmojiText.setEnabled(false);
        assertEquals("a".repeat(98) + "😀", ChatInputLimit.text("a".repeat(98) + "😀x", false));
        assertEquals("a".repeat(99), ChatInputLimit.text("a".repeat(99) + "😀", false));
    }
}
