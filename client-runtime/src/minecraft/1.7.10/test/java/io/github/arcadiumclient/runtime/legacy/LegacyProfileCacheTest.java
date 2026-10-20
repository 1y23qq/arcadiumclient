// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

final class LegacyProfileCacheTest {
    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test void rawNamesCannotInjectPathsOrFormatting() {
        assertEquals("notch", LegacyProfileCache.canonical("Notch"));
        for (String name : List.of("../notch", "Bad Name", "§aNotch", "12345678901234567", "name?x=1", "nötch", ""))
            assertNull(LegacyProfileCache.canonical(name));
        assertNull(LegacyProfileCache.canonical(null));
    }

    @Test void officialResponseMustMatchNameAndVersionFourIdentity() {
        String body = "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}";
        assertEquals(NOTCH, LegacyProfileCache.parseProfile(body, "notch"));
        assertNull(LegacyProfileCache.parseProfile(body, "AnotherPlayer"));
        assertNull(LegacyProfileCache.parseProfile(body.replace("4726", "3726"), "Notch"));
        assertNull(LegacyProfileCache.parseProfile("{}", "Notch"));
        assertNull(LegacyProfileCache.parseProfile("{\"id\":3,\"name\":false}", "false"));
    }

    @Test void successfulAndNegativeResultsAreCachedUntilExpiry() throws Exception {
        AtomicLong time = new AtomicLong(); AtomicInteger calls = new AtomicInteger();
        try (LegacyProfileCache cache = new LegacyProfileCache(name -> {
            calls.incrementAndGet(); return new LegacyProfileCache.Result(name.equals("notch") ? NOTCH : null, 100, 0);
        }, time::get, 8, 8, 0)) {
            cache.watch(List.of("Notch", "notch", "Missing"));
            await(() -> cache.queuedCount() == 0 && cache.cachedCount() == 2);
            assertEquals(2, calls.get()); assertEquals(NOTCH, cache.cached("NOTCH")); assertNull(cache.cached("Missing"));
            time.set(99); cache.watch(List.of("Notch", "Missing"));
            assertEquals(2, calls.get());
            time.set(101); cache.watch(List.of("Notch", "Missing"));
            await(() -> calls.get() == 4 && cache.queuedCount() == 0);
            assertEquals(NOTCH, cache.cached("Notch"));
        }
    }

    @Test void queueCacheAndConcurrencyStayBounded() throws Exception {
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (LegacyProfileCache cache = new LegacyProfileCache(name -> {
            int running = active.incrementAndGet(); peak.accumulateAndGet(running, Math::max); calls.incrementAndGet(); entered.countDown();
            try { assertTrue(release.await(2, TimeUnit.SECONDS)); return new LegacyProfileCache.Result(NOTCH, 1000, 0); }
            finally { active.decrementAndGet(); }
        }, System::currentTimeMillis, 4, 8, 0)) {
            List<String> names = new ArrayList<>(); for (int i = 0; i < 80; i++) names.add("player_" + i);
            cache.watch(names); assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(cache.queuedCount() <= 8); release.countDown();
            await(() -> cache.queuedCount() == 0);
            assertEquals(1, peak.get()); assertEquals(8, calls.get()); assertTrue(cache.cachedCount() <= 4);
        } finally { release.countDown(); }
    }

    @Test void serverChangesDiscardUnneededQueuedNames() throws Exception {
        List<String> queried = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (LegacyProfileCache cache = new LegacyProfileCache(name -> {
            queried.add(name); entered.countDown();
            if (name.equals("first")) assertTrue(release.await(2, TimeUnit.SECONDS));
            return new LegacyProfileCache.Result(NOTCH, 1000, 0);
        }, System::currentTimeMillis, 8, 8, 0)) {
            cache.watch(List.of("first", "oldqueued")); assertTrue(entered.await(2, TimeUnit.SECONDS));
            cache.watch(List.of("newserver")); release.countDown();
            await(() -> queried.size() == 2 && cache.queuedCount() == 0);
            assertEquals(List.of("first", "newserver"), queried);
            cache.pause(); assertEquals(0, cache.queuedCount());
        } finally { release.countDown(); }
    }

    @Test void offlineIdsAndFailedLookupsNeverBecomePositiveIdentities() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (LegacyProfileCache cache = new LegacyProfileCache(name -> {
            calls.incrementAndGet();
            if (name.equals("failure")) throw new java.io.IOException("fixture");
            return new LegacyProfileCache.Result(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 1000, 0);
        }, System::currentTimeMillis, 8, 8, 0)) {
            cache.watch(List.of("offline", "failure")); await(() -> calls.get() == 2 && cache.queuedCount() == 0);
            assertNull(cache.cached("offline")); assertNull(cache.cached("failure"));
            cache.watch(List.of("offline", "failure")); assertEquals(2, calls.get());
        }
    }

    private static void await(BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(ready.getAsBoolean(), "The bounded lookup worker did not finish");
    }
}
