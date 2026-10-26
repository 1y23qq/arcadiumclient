package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.features.BackgroundFramePacer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackgroundFramePacerTest {
    @Test fun limitsOnlyTheNextFrameAndDiscardsOverdueWaits() {
        val pacer = BackgroundFramePacer()
        assertEquals(0L, pacer.remaining(1_000_000_000L, 5))
        pacer.mark(1_000_000_000L)
        assertEquals(200_000_000L, pacer.remaining(1_000_000_000L, 5))
        assertEquals(100_000_000L, pacer.remaining(1_100_000_000L, 5))
        assertEquals(0L, pacer.remaining(2_000_000_000L, 5))
        assertEquals(0L, pacer.remaining(900_000_000L, 5))
        pacer.reset()
        assertEquals(0L, pacer.remaining(1_100_000_000L, 5))
    }

    @Test fun newCapsApplyImmediatelyWithoutAccumulatingSleep() {
        val pacer = BackgroundFramePacer()
        pacer.mark(0)
        assertEquals(200_000_000L, pacer.remaining(0, -1))
        assertEquals(5_000_000L, pacer.remaining(0, 10000))
        assertEquals(0L, pacer.remaining(10_000_000, 200))
    }
}
