// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.test

import io.github.arcadiumclient.modern.client.features.HealthText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthTextTest {
    @Test
    fun healthCanBeDisplayedInHeartsOrHp() {
        assertEquals("8.5", HealthText.format(17f, true))
        assertEquals("17", HealthText.format(17f, false))
        assertEquals("10", HealthText.format(20f, true))
        assertEquals("20", HealthText.format(20f, false))
        assertEquals("15", HealthText.format(30f, true))
        assertEquals("0", HealthText.format(-1f, true))
        assertEquals("0", HealthText.format(Float.NaN, false))
        assertEquals("0", HealthText.format(Float.POSITIVE_INFINITY, false))
    }
}
