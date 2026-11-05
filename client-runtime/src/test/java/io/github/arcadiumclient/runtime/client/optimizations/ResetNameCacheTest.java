// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResetNameCacheTest {
    @Test void preservesExactResetConcatenationAndReusesUnchangedContent() {
        ResetNameCache cache = new ResetNameCache(16, 1024);
        for (String input : new String[]{null, "", "Alice", "§b[MVP+] Alice", "日本語", "A\ud83d\ude00", "x\ud800", "§r"}) {
            assertEquals(input + "§r", cache.format(input));
            String value = cache.format(input);
            assertSame(value, cache.format(input == null ? null : new String(input)));
            assertTrue(cache.hit());
        }
        assertNotEquals(cache.format("§cAlice"), cache.format("§aAlice"));
    }

    @Test void boundsEntriesAndAllRetainedCharacters() {
        ResetNameCache cache = new ResetNameCache(2, 16);
        String first = cache.format("A"); cache.format("B"); assertSame(first, cache.format("A")); cache.format("C");
        assertEquals(2, cache.size()); assertTrue(cache.characters() <= 16);
        cache.format("B"); assertFalse(cache.hit());
        cache.format("x".repeat(100)); assertEquals(2, cache.size()); assertTrue(cache.characters() <= 16);
        cache.clear(); assertEquals(0, cache.size()); assertEquals(0, cache.characters());
    }

    @Test void tracksDynamicNamesAndFormattingWithoutEntityState() {
        ResetNameCache cache = new ResetNameCache(8, 128); Random random = new Random(72072);
        for (int index = 0; index < 3000; index++) {
            int length = random.nextInt(45); StringBuilder input = new StringBuilder();
            for (int character = 0; character < length; character++) input.append((char)random.nextInt(65536));
            String name = input.toString(); assertEquals(name + "§r", cache.format(name));
            assertTrue(cache.size() <= 8); assertTrue(cache.characters() <= 128);
        }
    }
}
