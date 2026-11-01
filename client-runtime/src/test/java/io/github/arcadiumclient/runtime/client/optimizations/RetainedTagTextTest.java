// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import static io.github.arcadiumclient.runtime.client.optimizations.RetainedTagText.Eligibility.*;
import static org.junit.jupiter.api.Assertions.*;

class RetainedTagTextTest {
    @AfterEach void restoreEmojiMode() { EmojiText.setEnabled(true); EmojiText.setHideInScoreboard(true); EmojiText.endScoreboard(false); }

    @Test void nativeStarsArrowsCjkAndOrdinaryColonsRemainNative() {
        for (String text : new String[]{"§6[250✫] §bNPC ➜ §fStats: 3.75", "玩家 日本語 한국어", "◇ ❖ ⦿ → ←", "Lobby: NPC", ":not_an_emoji:"})
            assertEquals(NATIVE, RetainedTagText.classify(text), text);
    }

    @Test void exactEmojiShortcodesUnicodeSequencesAndEncodedAtlasGlyphsFallBack() {
        for (String text : new String[]{":smile:", ":+1:", ":red_heart:", "😀", "❤️", "⭐️", "Hello " + (char) EmojiText.FIRST_GLYPH})
            assertEquals(EMOJI, RetainedTagText.classify(text), text);
        for (var entry : EmojiText.entries()) {
            assertEquals(EMOJI, RetainedTagText.classify(entry.shortcode()));
            assertEquals(EMOJI, RetainedTagText.classify(String.valueOf(entry.glyph())));
        }
    }

    @Test void bmpSymbolsWithoutTheRegisteredVariationSelectorStayNativeGlyphs() {
        assertEquals(NATIVE, RetainedTagText.classify("⭐ ❤ ✌"));
        assertEquals(EMOJI, RetainedTagText.classify("⭐️"));
        assertEquals(EMOJI, RetainedTagText.classify("✌️"));
    }

    @Test void obfuscationIsRejectedRegardlessOfFormattingCodeCase() {
        assertEquals(OBFUSCATED, RetainedTagText.classify("§kSecret§r"));
        assertEquals(OBFUSCATED, RetainedTagText.classify("§KSecret§r"));
        assertEquals(NATIVE, RetainedTagText.classify("§lBold §nUnderlined §rPlain"));
    }

    @Test void animatedPrefixMarkersCannotEnterNativeGlyphPlans() {
        assertEquals(ANIMATED, RetainedTagText.classify("\u2063Arcadium\u2064"));
        assertEquals(ANIMATED, RetainedTagText.classify("Start\u2063"));
        assertEquals(ANIMATED, RetainedTagText.classify("End\u2064"));
    }

    @Test void disabledAndSuppressedEmojiCannotSeedStaleSpecialTextPlans() {
        EmojiText.setEnabled(false);
        assertEquals(EMOJI_DISABLED, RetainedTagText.classify(":smile:"));
        assertEquals(EMOJI_DISABLED, RetainedTagText.classify("NPC: ✫"));
        assertEquals(NATIVE, RetainedTagText.classify("Plain NPC"));
        EmojiText.setEnabled(true);
        assertEquals(EMOJI, RetainedTagText.classify(":smile:"));
        assertEquals(NATIVE, RetainedTagText.classify("NPC: ✫"));
        boolean previous = EmojiText.beginScoreboard();
        try { assertEquals(EMOJI_DISABLED, RetainedTagText.classify("NPC: ✫")); }
        finally { EmojiText.endScoreboard(previous); }
        assertEquals(NATIVE, RetainedTagText.classify("NPC: ✫"));
    }

    @Test void malformedUtf16AndOversizedMetadataStayOnTheOriginalPath() {
        assertEquals(UNSUPPORTED, RetainedTagText.classify("\uD83D"));
        assertEquals(UNSUPPORTED, RetainedTagText.classify("\uDE00"));
        assertEquals(UNSUPPORTED, RetainedTagText.classify("a".repeat(2049)));
    }
}
