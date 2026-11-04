// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextWidthCacheTest {
    @Test void scopesWidthsByFontUnicodeAndRevisionAndKeepsZeroWidths() {
        TextWidthCache cache = new TextWidthCache(); Object vanilla = new Object(), other = new Object();
        assertEquals(TextWidthCache.MISSING, cache.get("§lLabel", vanilla, false, 1));
        cache.put("§lLabel", 37, vanilla, false, 1);
        assertEquals(37, cache.get("§lLabel", vanilla, false, 1));
        assertEquals(TextWidthCache.MISSING, cache.get("§lLabel", other, false, 1));
        cache.put("§lLabel", 19, other, false, 1);
        assertEquals(TextWidthCache.MISSING, cache.get("§lLabel", other, true, 1));
        cache.put("§lLabel", 14, other, true, 1);
        assertEquals(TextWidthCache.MISSING, cache.get("§lLabel", other, true, 2));
        cache.put("", 0, vanilla, false, 2);
        assertEquals(0, cache.get("", vanilla, false, 2));
        assertEquals(2, cache.hits());
    }

    @Test void boundsCachedStringsAndDoesNotRetainOversizedInputs() {
        TextWidthCache cache = new TextWidthCache(); Object source = new Object();
        for (int i = 0; i < 2100; i++) cache.put("label-" + i, i, source, false, 1);
        assertEquals(TextWidthCache.MISSING, cache.get("label-0", source, false, 1));
        assertEquals(2099, cache.get("label-2099", source, false, 1));
        String huge = "x".repeat(8192); cache.put(huge, 8, source, false, 1);
        assertEquals(TextWidthCache.MISSING, cache.get(huge, source, false, 1));
        cache.clear(); assertEquals(TextWidthCache.MISSING, cache.get("label-2099", source, false, 1));
    }

    @Test void nestedImmediateScopesFlushOnceAndRestoreTheirCaller() {
        int[] flushed = {0};
        GameFontMetricCounters font = new GameFontMetricCounters() {
            public long arcadium$metricHits() { return 0; }
            public long arcadium$metricMisses() { return 0; }
            public void arcadium$flushTextBatches() { flushed[0]++; }
        };
        boolean first = TextOptimizationOptions.beginImmediateText(font);
        try {
            assertFalse(first); assertTrue(TextOptimizationOptions.immediateText());
            boolean nested = TextOptimizationOptions.beginImmediateText(font);
            try { assertTrue(nested); assertEquals(1, flushed[0]); }
            finally { TextOptimizationOptions.endImmediateText(nested); }
            assertTrue(TextOptimizationOptions.immediateText());
        } finally { TextOptimizationOptions.endImmediateText(first); }
        assertFalse(TextOptimizationOptions.immediateText()); assertEquals(1, flushed[0]);
    }
}
