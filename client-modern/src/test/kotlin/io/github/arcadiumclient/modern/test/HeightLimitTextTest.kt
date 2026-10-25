// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.modern.client.features.HeightLimitText

class HeightLimitTextTest {
    @Test
    fun compactSpacingIsOptionalWithoutAddingLeadingOrTrailingSpaces() {
        assertEquals("#limit(#distance)", HeightLimitText.template(1, true, false, true, false))
        assertEquals("#limit (#distance)", HeightLimitText.template(1, true, false, true, true))
        assertEquals("(#distance)", HeightLimitText.template(1, false, false, true, true))
        assertEquals("#limit", HeightLimitText.template(1, true, false, false, true))
        assertEquals(HeightLimitText.template(0, true, false, true), HeightLimitText.template(0, true, false, true, true))
    }

    @Test
    fun compactMatchesTheReferenceAndSupportsAllValueCombinations() {
        val expected = listOf("", "256", "67", "67 / 256", "(189)", "256(189)", "67(189)", "67 / 256(189)")
        for (flags in 0..7) {
            assertEquals(expected[flags], HeightLimitText.format(1, 67, 256,
                flags and 1 != 0, flags and 2 != 0, flags and 4 != 0))
        }
    }

    @Test
    fun classicAndUnknownLimitsRemainReadable() {
        assertEquals("64 / 113", HeightLimitText.format(0, 64, 113, false, false, false))
        assertEquals("64 / ?", HeightLimitText.format(0, 64, null, true, false, true))
        assertEquals("?(?)", HeightLimitText.format(1, 64, null, true, false, true))
    }

    @Test
    fun distanceShowsAtLimitAboveLimitAndNegativeWorldHeights() {
        assertEquals("113(0)", HeightLimitText.format(1, 113, 113, true, false, true))
        assertEquals("113(0)", HeightLimitText.format(1, 117, 113, true, false, true))
        assertEquals("256(257)", HeightLimitText.format(1, -1, 256, true, false, true))
    }

    @Test
    fun customTemplatesKeepLiteralTextAndRepeatedTokens() {
        val parts = HeightLimitText.parts("Y #current / #limit (#distance) #distance", 109, 113)
        assertEquals("Y 109 / 113 (4) 4", parts.joinToString("") { it.text() })
        assertEquals(listOf("4", "4"), parts.filter { it.kind() == HeightLimitText.Kind.DISTANCE }.map { it.text() })
        assertEquals(listOf("(", ")"), parts.filter { it.kind() == HeightLimitText.Kind.PARENTHESES }.map { it.text() })
        assertEquals("above=0", HeightLimitText.parts("above=#distance", 200, 113).joinToString("") { it.text() })
        assertEquals("?", HeightLimitText.parts("#distance", 20, null).single().text())
        assertEquals(4294967295L, HeightLimitText.distance(Int.MIN_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun distanceColorsMatchFormattingCodeBoundaries() {
        for (distance in listOf(16L, 100L)) assertEquals(0, HeightLimitText.distanceRange(distance, 15, 5))
        for (distance in 6L..15L) assertEquals(1, HeightLimitText.distanceRange(distance, 15, 5))
        for (distance in 0L..5L) assertEquals(2, HeightLimitText.distanceRange(distance, 15, 5))
        assertEquals(2, HeightLimitText.distanceRange(8, 4, 10))
        assertEquals(0, HeightLimitText.distanceRange(11, 4, 10))
    }
}
