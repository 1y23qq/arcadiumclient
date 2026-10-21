// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.features.UnfocusedFrameLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnfocusedFrameLimitTest {
    @Test
    fun onlyInactiveWindowsAreCappedAndTheGameLimitIsRespected() {
        for (cap in listOf(5, 60, 137, 200)) {
            assertEquals(cap, UnfocusedFrameLimit.apply(260, false, cap))
            assertEquals(260, UnfocusedFrameLimit.apply(260, true, cap))
        }
        assertEquals(30, UnfocusedFrameLimit.apply(30, false, 60))
        assertEquals(5, UnfocusedFrameLimit.apply(260, false, 0))
        assertEquals(200, UnfocusedFrameLimit.apply(260, false, 1000))
        assertEquals(60, UnfocusedFrameLimit.apply(0, false, 60))
    }
}
