// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.emoji;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;

class EmojiTextTest {
    @AfterEach void enabled() { EmojiText.setEnabled(true); EmojiText.setHideInScoreboard(true); EmojiText.endScoreboard(false); }

    @Test void scoreboardScopeKeepsTextAndRestoresNestedChatRendering() {
        String text = "Score :smile: 😀";
        assertNotEquals(text, EmojiText.display(text));
        boolean previous = EmojiText.beginScoreboard();
        try {
            assertEquals(text, EmojiText.display(text));
            boolean nested = EmojiText.beginScoreboard();
            try { assertFalse(EmojiText.enabled()); } finally { EmojiText.endScoreboard(nested); }
            assertFalse(EmojiText.enabled());
            assertTrue(java.util.concurrent.CompletableFuture.supplyAsync(EmojiText::enabled).join());
        } finally { EmojiText.endScoreboard(previous); }
        assertNotEquals(text, EmojiText.display(text));
    }

    @Test void scoreboardOverrideDoesNotEnableGloballyDisabledEmoji() {
        EmojiText.setHideInScoreboard(false);
        boolean previous = EmojiText.beginScoreboard();
        try { assertNotEquals(":smile:", EmojiText.display(":smile:")); } finally { EmojiText.endScoreboard(previous); }
        EmojiText.setEnabled(false);
        previous = EmojiText.beginScoreboard();
        try { assertEquals(":smile:", EmojiText.display(":smile:")); } finally { EmojiText.endScoreboard(previous); }
        assertFalse(EmojiText.enabled());
    }

    @Test void replacesSupportedRunsWithoutChangingFormattingOrUnknownShortcodes() {
        String raw = "§aHi :smile: :unknown: 😀 ❤️ テスト";
        String shown = EmojiText.display(raw);
        assertTrue(shown.startsWith("§aHi " + EmojiText.entries().get(1).glyph()));
        assertTrue(shown.contains(":unknown:"));
        assertFalse(shown.contains("\uFE0F"));
        assertTrue(shown.endsWith(" テスト"));
        assertEquals(3, shown.chars().filter(EmojiText::isGlyph).count());
        assertSame(shown, EmojiText.display(shown));
    }

    @Test void aliasesAndUnicodeHaveTheSameSingleGlyph() {
        assertEquals(EmojiText.display(":thumbsup:"), EmojiText.display(":+1:"));
        assertEquals(EmojiText.display(":red_heart:"), EmojiText.display("❤️"));
        assertEquals(1, EmojiText.display("😀").length());
        assertEquals(1, EmojiText.display(":thumbsup:").length());
        assertFalse(EmojiText.isGlyph(EmojiText.FIRST_GLYPH - 1));
        assertFalse(EmojiText.isGlyph(EmojiText.FIRST_GLYPH + 64));
    }

    @Test void sendsAsciiShortcodesAndKeepsCommandsAndUnrelatedUnicodeIntact() {
        assertEquals("gg :grinning: :heart: テスト", EmojiText.toWire("gg 😀 ❤️ テスト"));
        assertEquals(":smile:", EmojiText.toWire(EmojiText.display(":smile:")));
        String command = "/msg Test 😀 :smile:";
        assertSame(command, EmojiText.toWire(command));
        String normal = "/play bedwars_eight_one";
        assertSame(normal, EmojiText.toWire(normal));
    }

    @Test void inputTrimsMapDisplayGlyphsBackToTheirWholeSourceTokens() {
        String raw = "abc :smile: 😀 z", encoded = EmojiText.display(raw);
        assertEquals("abc :smile:", EmojiText.sourceTrim(raw, encoded, encoded.substring(0, 5), false));
        assertEquals("😀 z", EmojiText.sourceTrim(raw, encoded, encoded.substring(6), true));
        assertEquals(raw, EmojiText.sourceTrim(raw, encoded, encoded, false));
        assertEquals("", EmojiText.sourceTrim(raw, encoded, "", true));
        assertEquals("", EmojiText.sourceTrim(raw, encoded, "", false));
    }

    @Test void disablingTheFeatureLeavesInputAndPrivateUsePackGlyphsUntouched() {
        EmojiText.setEnabled(false);
        String text = "😀 :smile: §a";
        assertSame(text, EmojiText.display(text));
        assertSame(text, EmojiText.toWire(text));
        assertSame("❤", EmojiText.display("❤"));
    }

    @Test void everyCatalogEntryHasABundledTransparentAtlasCell() throws Exception {
        try (var input = EmojiText.class.getResourceAsStream(EmojiText.ATLAS)) {
            assertNotNull(input);
            var atlas = ImageIO.read(input);
            assertEquals(512, atlas.getWidth());
            assertEquals(512, atlas.getHeight());
            assertEquals(64, EmojiText.entries().size());
            for (int index = 0; index < 64; index++) {
                boolean visible = false;
                for (int y = index / 8 * 64; y < index / 8 * 64 + 64; y++)
                    for (int x = index % 8 * 64; x < index % 8 * 64 + 64; x++)
                        visible |= atlas.getRGB(x, y) >>> 24 != 0;
                assertTrue(visible, EmojiText.entries().get(index).name());
            }
        }
    }
}
