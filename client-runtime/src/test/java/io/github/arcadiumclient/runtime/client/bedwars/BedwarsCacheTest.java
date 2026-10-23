// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BedwarsCacheTest {
    private static final UUID ID = UUID.fromString("12345678-1234-4234-9234-123456789abc");
    private static final BedwarsData.Policy KEYLESS = new BedwarsData.Policy(true, false, "");

    private static String profile() {
        return "{\"success\":true,\"player\":{\"uuid\":\"" + ID.toString().replace("-", "")
                + "\",\"displayname\":\"Player\",\"networkExp\":3430000,\"achievements\":{\"bedwars_level\":245},"
                + "\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":30,\"final_deaths_bedwars\":4}}}}";
    }

    @Test public void successfulStatsStayPinnedForTheSameGameAcrossRepeatedLocationUpdates() throws Exception {
        AtomicLong now = new AtomicLong(100000);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            return new OverlayHttp.Response(200, profile());
        }, now::get)) {
            BedwarsData data = new BedwarsData(http, now::get);
            data.beginMatch("mini123");
            data.retainForMatch(List.of(ID));
            BedwarsStats first = data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS);
            now.addAndGet(1800000);
            data.beginMatch("mini123");
            for (int i = 0; i < 100; i++) assertSame(first, data.get(ID, "Player", KEYLESS).get());
            assertEquals(1, calls.get());
            data.beginMatch("mini456");
            assertNull(data.present(ID));
            assertNotSame(first, data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        }
    }

    @Test public void leavingTheGameReleasesExpiredStatsWithoutClearingFreshSuccesses() throws Exception {
        AtomicLong now = new AtomicLong(100000);
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> new OverlayHttp.Response(200, profile()), now::get)) {
            BedwarsData data = new BedwarsData(http, now::get);
            data.beginMatch("mini123");
            data.retainForMatch(List.of(ID));
            BedwarsStats first = data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS);
            data.releaseMatch();
            assertSame(first, data.present(ID));
            now.addAndGet(600001);
            assertNull(data.present(ID));
        }
    }

    @Test public void transientOutageRecoversWithoutCachingFailureForTheMatch() throws Exception {
        AtomicLong now = new AtomicLong(100000);
        AtomicBoolean unavailable = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            return unavailable.get() ? new OverlayHttp.Response(503, "down") : new OverlayHttp.Response(200, profile());
        }, now::get)) {
            BedwarsData data = new BedwarsData(http, now::get);
            data.beginMatch("mini123");
            data.retainForMatch(List.of(ID));
            assertTrue(data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS).isUnavailable());
            assertEquals(3, calls.get());
            assertTrue(data.get(ID, "Player", KEYLESS).get().isUnavailable());
            assertEquals(3, calls.get());
            unavailable.set(false);
            now.addAndGet(10001);
            assertEquals(245, data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS).getStar());
            assertEquals(4, calls.get());
        }
    }

    @Test public void providerTimeoutFallsBackOnceBeforeRetryingTheFailedHost() throws Exception {
        AtomicInteger bordic = new AtomicInteger();
        AtomicInteger nadeshiko = new AtomicInteger();
        String player = BedwarsData.child(BedwarsData.object(profile()), "player").toString();
        String page = "playerData = JSON.parse(decodeURIComponent(\"" + URLEncoder.encode(player, StandardCharsets.UTF_8) + "\"))";
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            if (uri.getHost().equals("api.bordic.xyz")) { bordic.incrementAndGet(); throw new IOException("Timeout"); }
            nadeshiko.incrementAndGet();
            assertFalse(headers.containsKey("API-Key"));
            return new OverlayHttp.Response(200, page);
        })) {
            BedwarsStats stats = new BedwarsData(http).get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS);
            assertEquals(245, stats.getStar());
            assertEquals(1, bordic.get());
            assertEquals(1, nadeshiko.get());
        }
    }

    @Test public void cosmeticOrCredentialChangesDoNotDiscardSuccessOrSharedPendingWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            entered.countDown();
            try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            return new OverlayHttp.Response(200, profile());
        })) {
            BedwarsData data = new BedwarsData(http);
            var first = data.get(ID, "Player", KEYLESS);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            data.retryUnavailable();
            assertSame(first, data.get(ID, "Player", new BedwarsData.Policy(true, true, "another-key")));
            release.countDown();
            BedwarsStats stats = first.get(2, TimeUnit.SECONDS);
            data.retryUnavailable();
            assertSame(stats, data.present(ID));
            assertEquals(1, calls.get());
        }
    }

    @Test public void failedRefreshKeepsTheLastSuccessfulStats() throws Exception {
        AtomicLong now = new AtomicLong(100000);
        AtomicBoolean fail = new AtomicBoolean();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> new OverlayHttp.Response(fail.get() ? 503 : 200, profile()), now::get)) {
            BedwarsData data = new BedwarsData(http, now::get);
            BedwarsStats first = data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS);
            now.addAndGet(600001);
            fail.set(true);
            assertSame(first, data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS));
            assertSame(first, data.present(ID));
        }
    }

    @Test public void cancelledRequestDoesNotPoisonTheCacheOrContinueProviderFallback() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                entered.countDown();
                try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
            return new OverlayHttp.Response(200, profile());
        })) {
            BedwarsData data = new BedwarsData(http);
            var first = data.get(ID, "Player", KEYLESS);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(first.cancel(false));
            assertNull(data.present(ID));
            var second = data.get(ID, "Player", KEYLESS);
            release.countDown();
            assertEquals(245, second.get(2, TimeUnit.SECONDS).getStar());
            assertEquals(2, calls.get());
        }
    }

    @Test public void oldCredentialFailureCannotRestoreANegativeEntryAfterSettingsChange() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            if ("old-key".equals(headers.get("API-Key"))) {
                entered.countDown();
                try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
                return new OverlayHttp.Response(403, "{}");
            }
            return new OverlayHttp.Response(200, profile());
        })) {
            BedwarsData data = new BedwarsData(http);
            var first = data.get(ID, "Player", new BedwarsData.Policy(false, true, "old-key"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            data.retryUnavailable();
            release.countDown();
            assertTrue(first.get(2, TimeUnit.SECONDS).isUnavailable());
            assertNull(data.present(ID));
            assertFalse(data.invalidKey("new-key"));
            assertEquals(245, data.get(ID, "Player", new BedwarsData.Policy(false, true, "new-key")).get(2, TimeUnit.SECONDS).getStar());
        }
    }

    @Test public void rateLimitCooldownExpiresUsingTheInjectedClock() {
        AtomicLong now = new AtomicLong(100000);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            return calls.incrementAndGet() == 1 ? new OverlayHttp.Response(429, "{}", 90, -1, 0) : new OverlayHttp.Response(200, "{}");
        }, now::get)) {
            assertEquals(429, http.requestOnce("https://api.bordic.xyz/test", OverlayHttp.Credential.NONE, "", () -> true).status);
            now.addAndGet(89999);
            assertEquals(429, http.requestOnce("https://api.bordic.xyz/test", OverlayHttp.Credential.NONE, "", () -> true).status);
            assertEquals(1, calls.get());
            now.addAndGet(2);
            assertEquals(200, http.requestOnce("https://api.bordic.xyz/test", OverlayHttp.Credential.NONE, "", () -> true).status);
            assertEquals(2, calls.get());
        }
    }

    @Test public void networkExperienceUsesTheHypixelLevelCurveAndDoesNotInventUnknownLevels() {
        assertEquals(1, BedwarsData.networkLevel(BedwarsData.object("{\"networkExp\":0}")));
        assertEquals(2, BedwarsData.networkLevel(BedwarsData.object("{\"networkExp\":10000}")));
        assertEquals(3, BedwarsData.networkLevel(BedwarsData.object("{\"networkExp\":22500}")));
        assertEquals(50, BedwarsData.networkLevel(BedwarsData.object("{\"networkExp\":3430000}")));
        assertEquals(-1, BedwarsData.networkLevel(BedwarsData.object("{}")));
        assertEquals(-1, BedwarsData.networkLevel(BedwarsData.object("{\"networkExp\":-1}")));
        assertEquals(-1, BedwarsData.networkLevel(BedwarsData.object("{\"networkExp\":\"NaN\"}")));
    }

    @Test public void emptyProviderProfilesDoNotBecomePinnedZeroStatistics() throws Exception {
        String empty = "{\"success\":true,\"player\":{\"uuid\":\"" + ID.toString().replace("-", "")
                + "\",\"displayname\":\"Player\",\"networkExp\":null,\"achievements\":{\"bedwars_level\":null},\"stats\":{\"Bedwars\":{}}}}";
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            return new OverlayHttp.Response(200, empty);
        })) {
            BedwarsData data = new BedwarsData(http);
            data.beginMatch("mini123");
            data.retainForMatch(List.of(ID));
            assertTrue(data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS).isUnavailable());
            assertEquals(3, calls.get());
        }
        assertEquals(0, BedwarsData.parsePlayer(ID, "Player", BedwarsData.object("{\"displayname\":\"Player\",\"networkExp\":0}")).getStar());
    }
}
