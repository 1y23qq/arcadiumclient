// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GammaTransitionGuardTest {
    @Test void aCanceledTimerTaskCannotOverwriteTheRestoredNativeValue() throws Exception {
        var value = new AtomicInteger(3);
        var configuredToggle = new AtomicInteger(950);
        long old = GammaTransitionGuard.token();
        GammaTransitionGuard.replace(() -> { }, () -> value.set(75));
        GammaTransitionGuard.run(old, () -> { value.set(19); configuredToggle.set(1800); });
        assertEquals(75, value.get());
        assertEquals(950, configuredToggle.get());
        GammaTransitionGuard.run(GammaTransitionGuard.token(), () -> value.set(95));
        assertEquals(95, value.get());
    }
    @Test void restoringWaitsForTheOldInFlightTaskThenRejectsItsQueuedWrites() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var replacing = new CountDownLatch(1);
        var value = new AtomicInteger();
        long token = GammaTransitionGuard.token();
        try {
            var old = executor.submit(() -> GammaTransitionGuard.run(token, () -> {
                entered.countDown();
                try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                value.set(19);
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var restore = executor.submit(() -> {
                replacing.countDown();
                try { GammaTransitionGuard.replace(() -> { }, () -> value.set(75)); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            assertTrue(replacing.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> restore.get(50, TimeUnit.MILLISECONDS));
            release.countDown(); old.get(2, TimeUnit.SECONDS); restore.get(2, TimeUnit.SECONDS);
            GammaTransitionGuard.run(token, () -> value.set(7));
            assertEquals(75, value.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
