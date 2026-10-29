// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BedwarsKeylessLatencyTest {
    private static final UUID ID = player(0);
    private static final BedwarsData.Policy KEYLESS = new BedwarsData.Policy(true, false, "stored-secret");

    private static UUID player(int index) { return new UUID(0x1234567812344234L, 0x9234123456789ab0L + index); }

    private static String compact(URI uri) {
        return uri.getQuery() == null ? uri.getPath().split("/")[2] : uri.getQuery().substring("uuid=".length());
    }

    private static String profile(URI uri) {
        return "{\"success\":true,\"player\":{\"uuid\":\"" + compact(uri)
                + "\",\"displayname\":\"Player\",\"achievements\":{\"bedwars_level\":245},"
                + "\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":30,\"final_deaths_bedwars\":4}}}}";
    }

    private static String page(URI uri) {
        String player = BedwarsData.child(BedwarsData.object(profile(uri)), "player").toString();
        return "playerData = JSON.parse(decodeURIComponent(\"" + URLEncoder.encode(player, StandardCharsets.UTF_8) + "\"))";
    }

    private static void await(CountDownLatch release) throws IOException {
        try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test request not released"); }
        catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw new IOException(cancelled); }
    }

    @Test public void rosterStartsEightRequestsWithoutUsingTheCallingThread() throws Exception {
        Thread caller = Thread.currentThread();
        CountDownLatch entered = new CountDownLatch(8), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            assertNotSame(caller, Thread.currentThread());
            assertEquals("api.bordic.xyz", uri.getHost());
            assertFalse(headers.containsKey("API-Key"));
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return new OverlayHttp.Response(200, profile(uri));
        })) {
            BedwarsData data = new BedwarsData(http);
            List<CompletableFuture<BedwarsStats>> players = new ArrayList<>();
            for (int i = 0; i < 8; i++) players.add(data.get(player(i), "Player", KEYLESS));
            assertTrue(entered.await(500, TimeUnit.MILLISECONDS));
            release.countDown();
            for (CompletableFuture<BedwarsStats> player : players) assertEquals(245, player.get(2, TimeUnit.SECONDS).getStar());
            assertEquals(8, calls.get());
        } finally { release.countDown(); }
    }

    @Test public void hedgedStatsCompleteWhileThePrimaryProviderIsStillStalled() throws Exception {
        CountDownLatch primaryEntered = new CountDownLatch(1), release = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            assertFalse(headers.containsKey("API-Key"));
            assertFalse(uri.toString().contains("stored-secret"));
            calls.incrementAndGet();
            if (uri.getHost().equals("api.bordic.xyz")) {
                primaryEntered.countDown();
                try { await(release); }
                catch (IOException cancelled) { interrupted.countDown(); throw cancelled; }
                return new OverlayHttp.Response(200, profile(uri));
            }
            assertEquals("nadeshiko.io", uri.getHost());
            return new OverlayHttp.Response(200, page(uri));
        })) {
            BedwarsData data = new BedwarsData(http);
            CompletableFuture<BedwarsStats> first = data.get(ID, "Player", KEYLESS);
            assertTrue(primaryEntered.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) assertSame(first, data.get(ID, "Player", KEYLESS));
            BedwarsStats stats = first.get(2, TimeUnit.SECONDS);
            assertEquals(245, stats.getStar());
            assertEquals(1, release.getCount());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertSame(stats, data.present(ID));
            assertEquals(245, data.get(player(1), "Player", KEYLESS).get(2, TimeUnit.SECONDS).getStar());
            assertEquals(3, calls.get());
        } finally { release.countDown(); }
    }

    @Test public void reservedFallbackWorkersCanResolveAFullRosterBehindStalledPrimaries() throws Exception {
        CountDownLatch primaryEntered = new CountDownLatch(8), release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger(), maximum = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                if (uri.getHost().equals("api.bordic.xyz")) {
                    primaryEntered.countDown();
                    await(release);
                    return new OverlayHttp.Response(200, profile(uri));
                }
                assertEquals("nadeshiko.io", uri.getHost());
                return new OverlayHttp.Response(200, page(uri));
            } finally { running.decrementAndGet(); }
        })) {
            BedwarsData data = new BedwarsData(http);
            List<CompletableFuture<BedwarsStats>> players = new ArrayList<>();
            for (int i = 0; i < 24; i++) players.add(data.get(player(i), "Player", KEYLESS));
            assertTrue(primaryEntered.await(500, TimeUnit.MILLISECONDS));
            CompletableFuture.allOf(players.toArray(new CompletableFuture<?>[0])).get(3, TimeUnit.SECONDS);
            for (CompletableFuture<BedwarsStats> player : players) assertEquals(245, player.get().getStar());
            assertEquals(1, release.getCount());
            assertEquals(0, data.pendingCount());
            assertTrue(maximum.get() <= 10);
        } finally { release.countDown(); }
    }

    @Test public void thePrimaryCanWinAfterFallbackStartsAndCancelTheSlowerAttempt() throws Exception {
        CountDownLatch primaryRelease = new CountDownLatch(1), fallbackEntered = new CountDownLatch(1);
        CountDownLatch fallbackRelease = new CountDownLatch(1), fallbackStopped = new CountDownLatch(1);
        AtomicInteger fallbackCalls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            if (uri.getHost().equals("api.bordic.xyz")) {
                await(primaryRelease);
                return new OverlayHttp.Response(200, profile(uri));
            }
            assertEquals("nadeshiko.io", uri.getHost());
            fallbackCalls.incrementAndGet();
            fallbackEntered.countDown();
            try { await(fallbackRelease); }
            catch (IOException cancelled) { fallbackStopped.countDown(); throw cancelled; }
            return new OverlayHttp.Response(200, page(uri));
        })) {
            BedwarsData data = new BedwarsData(http);
            CompletableFuture<BedwarsStats> first = data.get(ID, "Player", KEYLESS);
            assertTrue(fallbackEntered.await(2, TimeUnit.SECONDS));
            primaryRelease.countDown();
            assertEquals(245, first.get(2, TimeUnit.SECONDS).getStar());
            assertTrue(fallbackStopped.await(2, TimeUnit.SECONDS));
            assertEquals(245, data.get(player(1), "Player", KEYLESS).get(2, TimeUnit.SECONDS).getStar());
            assertEquals(1, fallbackCalls.get());
        } finally { primaryRelease.countDown(); fallbackRelease.countDown(); }
    }

    @Test public void invalidHedgedProfileFallsThroughWithoutAcceptingAnotherPlayersStats() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger(), maximum = new AtomicInteger(), abyss = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                if (uri.getHost().equals("api.bordic.xyz")) { await(release); return new OverlayHttp.Response(503, ""); }
                if (uri.getHost().equals("nadeshiko.io")) return new OverlayHttp.Response(200, page(uri).replace(compact(uri), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
                abyss.incrementAndGet();
                return new OverlayHttp.Response(200, profile(uri));
            } finally { running.decrementAndGet(); }
        })) {
            BedwarsStats stats = new BedwarsData(http).get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS);
            assertEquals(ID, stats.getUuid());
            assertEquals(245, stats.getStar());
            assertEquals(1, abyss.get());
            assertTrue(maximum.get() <= 2);
        } finally { release.countDown(); }
    }

    @Test public void cancellationRemovesTheDelayedFallbackAndDoesNotBackOffAHealthyHost() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), stopped = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            assertEquals("api.bordic.xyz", uri.getHost());
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try { await(release); }
                catch (IOException cancelled) { stopped.countDown(); throw cancelled; }
            }
            return new OverlayHttp.Response(200, profile(uri));
        })) {
            BedwarsData data = new BedwarsData(http);
            CompletableFuture<BedwarsStats> first = data.get(ID, "Player", KEYLESS);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(first.cancel(false));
            assertTrue(stopped.await(2, TimeUnit.SECONDS));
            assertEquals(245, data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS).getStar());
            http.defer(() -> { }, 700).get(2, TimeUnit.SECONDS);
            assertEquals(2, calls.get());
        } finally { release.countDown(); }
    }

    @Test public void clearingTheWorldCancelsBothProvidersAndRejectsLateResults() throws Exception {
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1), stopped = new CountDownLatch(2);
        AtomicBoolean accept = new AtomicBoolean();
        AtomicInteger abyss = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            if (uri.getHost().equals("api.abyssoverlay.com")) abyss.incrementAndGet();
            if (!accept.get()) {
                entered.countDown();
                try { await(release); }
                catch (IOException cancelled) { stopped.countDown(); throw cancelled; }
            }
            return new OverlayHttp.Response(200, uri.getHost().equals("nadeshiko.io") ? page(uri) : profile(uri));
        })) {
            BedwarsData data = new BedwarsData(http);
            CompletableFuture<BedwarsStats> first = data.get(ID, "Player", KEYLESS);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            data.clear();
            assertTrue(first.isCancelled());
            assertTrue(stopped.await(2, TimeUnit.SECONDS));
            assertNull(data.present(ID));
            assertEquals(0, abyss.get());
            accept.set(true);
            assertEquals(245, data.get(ID, "Player", KEYLESS).get(2, TimeUnit.SECONDS).getStar());
        } finally { release.countDown(); }
    }

    @Test public void knownNicknamesNeverStartProviderRequests() throws Exception {
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> { fail("Nickname requested"); return null; })) {
            UUID nickname = UUID.fromString("12345678-1234-1234-9234-123456789abc");
            assertTrue(new BedwarsData(http).get(nickname, "Nick", KEYLESS).get(2, TimeUnit.SECONDS).isNicked());
        }
    }

    @Test public void closingTheHttpServiceCancelsRunningQueuedAndDelayedWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(8), release = new CountDownLatch(1);
        List<CompletableFuture<Integer>> work = new ArrayList<>();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> new OverlayHttp.Response(200, ""))) {
            for (int i = 0; i < 12; i++) work.add(http.submit(() -> true, () -> {
                entered.countDown();
                try { await(release); } catch (IOException cancelled) { return 0; }
                return 1;
            }));
            CompletableFuture<Void> delayed = http.defer(() -> fail("Delayed work started after close"), 10000);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            http.close();
            for (CompletableFuture<Integer> future : work) assertTrue(future.isCancelled());
            assertTrue(delayed.isCancelled());
        } finally { release.countDown(); }
    }

    @Test public void fallbackAndDelayQueuesHaveSeparateFixedBounds() throws Exception {
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger(), maximum = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> new OverlayHttp.Response(200, ""))) {
            List<CompletableFuture<Integer>> work = new ArrayList<>();
            for (int i = 0; i < 80; i++) work.add(http.submitFallback(() -> true, () -> {
                maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
                entered.countDown();
                try { await(release); } catch (IOException cancelled) { return 0; }
                finally { running.decrementAndGet(); }
                return 1;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(work.stream().filter(CompletableFuture::isCompletedExceptionally).count() >= 14);
            List<CompletableFuture<Void>> timers = new ArrayList<>();
            for (int i = 0; i < 150; i++) timers.add(http.defer(() -> fail("Delayed work started"), 10000));
            assertEquals(22, timers.stream().filter(CompletableFuture::isCompletedExceptionally).count());
            timers.forEach(timer -> timer.cancel(false));
            release.countDown();
            for (CompletableFuture<Integer> future : work) future.handle((value, error) -> 0).get(2, TimeUnit.SECONDS);
            assertTrue(maximum.get() <= 2);
        } finally { release.countDown(); }
    }
}
