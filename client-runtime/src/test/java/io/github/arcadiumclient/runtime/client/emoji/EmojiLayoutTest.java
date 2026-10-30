package io.github.arcadiumclient.runtime.client.emoji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EmojiLayoutTest {
    @Test void formattingDoesNotShrinkTheImageAdvance() {
        assertEquals(9, EmojiLayout.advance(""));
        assertEquals(9, EmojiLayout.advance("\u00a7a\u00a7o\u00a7n"));
        assertEquals(10, EmojiLayout.advance("\u00a7a\u00a7l\u00a7o"));
    }

    @Test void resetAndColorsClearBoldBeforeTheNextImage() {
        assertEquals(9, EmojiLayout.advance("\u00a7l\u00a7r"));
        assertEquals(9, EmojiLayout.advance("\u00a7l\u00a7c\u00a7o"));
        assertEquals(10, EmojiLayout.advance("\u00a7l\u00a7c\u00a7L"));
        assertEquals(10, EmojiLayout.advance("\u00a7l\u00a7l"));
    }
}
