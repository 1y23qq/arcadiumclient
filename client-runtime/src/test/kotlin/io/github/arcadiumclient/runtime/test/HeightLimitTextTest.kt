// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.features.HeightLimitText

class HeightLimitTextTest {
    @Test
    fun compactMatchesTheReferenceAndSupportsAllValueCombinations() {
        val expected = listOf("", "256", "67", "67 / 256", "(189)", "256 (189)", "67 (189)", "67 / 256 (189)")
        for (flags in 0..7) {
            assertEquals(expected[flags], HeightLimitText.format(1, 67, 256,
                flags and 1 != 0, flags and 2 != 0, flags and 4 != 0))
        }
    }

    @Test
    fun classicAndUnknownLimitsRemainReadable() {
        assertEquals("64 / 113", HeightLimitText.format(0, 64, 113, false, false, false))
        assertEquals("64 / ?", HeightLimitText.format(0, 64, null, true, false, true))
        assertEquals("? (?)", HeightLimitText.format(1, 64, null, true, false, true))
    }

    @Test
    fun distanceShowsAtLimitAboveLimitAndNegativeWorldHeights() {
        assertEquals("113 (0)", HeightLimitText.format(1, 113, 113, true, false, true))
        assertEquals("113 (-4)", HeightLimitText.format(1, 117, 113, true, false, true))
        assertEquals("256 (257)", HeightLimitText.format(1, -1, 256, true, false, true))
    }
}
