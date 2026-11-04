// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import io.github.arcadiumclient.shared.network.ReconnectCountdown;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectCountdownTest {
    @Test void countsWallTimeAndRetainsItsDeadlineAcrossLayoutReinitialization() {
        AtomicLong clock = new AtomicLong(100);
        ReconnectCountdown countdown = new ReconnectCountdown(clock::get);
        countdown.start();
        assertEquals("Reconnect (3)", countdown.label());
        clock.addAndGet(1_000_000_000L); countdown.start();
        assertEquals(2, countdown.seconds()); assertFalse(countdown.ready());
        clock.addAndGet(1_000_000_000L); assertEquals(1, countdown.seconds());
        clock.addAndGet(999_999_999L); assertFalse(countdown.ready());
        clock.incrementAndGet(); assertTrue(countdown.ready()); assertEquals(0, countdown.seconds());
    }
    @Test void immediateAndAutomaticAttemptsAreSingleUseAndLeavingCancels() {
        AtomicLong clock = new AtomicLong();
        ReconnectCountdown countdown = new ReconnectCountdown(clock::get);
        countdown.start(); assertTrue(countdown.take()); assertFalse(countdown.take());
        clock.addAndGet(8_000_000_000L); assertFalse(countdown.ready());
        countdown.start(); assertFalse(countdown.ready()); assertFalse(countdown.take());
        ReconnectCountdown next = new ReconnectCountdown(clock::get);
        next.start(); next.cancel(); clock.addAndGet(4_000_000_000L); next.start();
        assertFalse(next.ready()); assertEquals("Reconnect", next.label());
        assertTrue(next.take()); assertFalse(next.take());
        ReconnectCountdown third = new ReconnectCountdown(clock::get); third.start();
        clock.addAndGet(5_000_000_000L); assertTrue(third.ready());
        assertTrue(third.take()); assertFalse(third.ready());
    }
    @Test void nanoTimeWrapDoesNotChangeTheThreeSecondDelay() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 1_000_000_000L);
        ReconnectCountdown countdown = new ReconnectCountdown(clock::get); countdown.start();
        clock.addAndGet(2_000_000_000L); assertEquals(1, countdown.seconds());
        clock.addAndGet(1_000_000_000L); assertTrue(countdown.ready());
    }
}
