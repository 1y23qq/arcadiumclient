// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class BedwarsOverlayDataTest {
    private static final UUID ID = UUID.fromString("12345678-1234-4234-9234-123456789abc");
    private static final String COMPACT = ID.toString().replace("-", "");

    private static String profile(String name) {
        return "{\"success\":true,\"player\":{\"uuid\":\"" + COMPACT + "\",\"displayname\":\"" + name
                + "\",\"achievements\":{\"bedwars_level\":245},\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":30,\"final_deaths_bedwars\":4}}}}";
    }

    @Test public void keylessNeverReceivesStoredHypixelKey() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            assertEquals("api.bordic.xyz", uri.getHost());
            assertFalse(uri.toString().contains("test-secret"));
            assertFalse(headers.containsKey("API-Key"));
            assertFalse(headers.containsKey("seraph-api-key"));
            return new OverlayHttp.Response(200, profile("Player"));
        })) {
            BedwarsData data = new BedwarsData(http);
            BedwarsStats stats = data.get(ID, "Player", new BedwarsData.Policy(true, false, "test-secret")).get(2, TimeUnit.SECONDS);
            assertEquals(245, stats.getStar());
            assertEquals(7.5, stats.getFkdr());
            assertSame(stats, data.get(ID, "Player", new BedwarsData.Policy(true, false, "test-secret")).get());
            assertEquals(1, calls.get());
        }
    }

    @Test public void enabledKeyUsesOnlyHypixelHeader() throws Exception {
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            assertEquals("api.hypixel.net", uri.getHost());
            assertEquals("test-secret", headers.get("API-Key"));
            assertFalse(uri.toString().contains("test-secret"));
            return new OverlayHttp.Response(200, profile("Player"));
        })) {
            assertFalse(new BedwarsData(http).get(ID, "Player", new BedwarsData.Policy(false, true, "test-secret")).get().isUnavailable());
            assertThrows(IllegalArgumentException.class, () -> http.request("https://api.bordic.xyz/", OverlayHttp.Credential.HYPIXEL, "test-secret", null, () -> true));
        }
    }

    @Test public void fallbackDropsCredentialsAndReportsInvalidKey() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            if (uri.getHost().equals("api.hypixel.net")) {
                assertEquals("test-secret", headers.get("API-Key"));
                return new OverlayHttp.Response(403, "{}");
            }
            assertFalse(headers.containsKey("API-Key"));
            return new OverlayHttp.Response(200, profile("Player"));
        })) {
            BedwarsData data = new BedwarsData(http);
            assertFalse(data.get(ID, "Player", new BedwarsData.Policy(true, true, "test-secret")).get().isUnavailable());
            assertTrue(data.invalidKey());
            assertEquals(2, calls.get());
        }
    }

    @Test public void concurrentRequestsCoalesceBeforeTransportStarts() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet(); entered.countDown();
            try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            return new OverlayHttp.Response(200, profile("Player"));
        })) {
            BedwarsData data = new BedwarsData(http);
            BedwarsData.Policy policy = new BedwarsData.Policy(true, false, "");
            CompletableFuture<BedwarsStats> first = data.get(ID, "Player", policy);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) assertSame(first, data.get(ID, "Player", policy));
            release.countDown();
            assertEquals("Player", first.get(2, TimeUnit.SECONDS).getName());
            assertEquals(1, calls.get());
        }
    }

    @Test public void worldGenerationRejectsLateResults() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            return new OverlayHttp.Response(200, profile(call == 1 ? "OldPlayer" : "NewPlayer"));
        })) {
            BedwarsData data = new BedwarsData(http);
            BedwarsData.Policy policy = new BedwarsData.Policy(true, false, "");
            CompletableFuture<BedwarsStats> old = data.get(ID, "OldPlayer", policy);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            data.clear();
            assertTrue(old.isCancelled());
            assertEquals("NewPlayer", data.get(ID, "NewPlayer", policy).get(2, TimeUnit.SECONDS).getName());
            release.countDown();
            assertEquals("NewPlayer", data.present(ID).getName());
        }
    }

    @Test public void throttlingHonorsRetryAfterWithoutSendingMoreRequests() {
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            return new OverlayHttp.Response(429, "{}", 90, -1, 0);
        })) {
            assertEquals(429, http.request("https://api.bordic.xyz/test", OverlayHttp.Credential.NONE, "", null, () -> true).status);
            assertEquals(429, http.request("https://api.bordic.xyz/test", OverlayHttp.Credential.NONE, "", null, () -> true).status);
            assertEquals(1, calls.get());
        }
    }

    @Test public void failedProvidersRemainUnavailable() throws Exception {
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> new OverlayHttp.Response(503, "service unavailable"))) {
            BedwarsStats stats = new BedwarsData(http).get(ID, "Player", new BedwarsData.Policy(true, false, "")).get(2, TimeUnit.SECONDS);
            assertTrue(stats.isUnavailable());
            assertFalse(stats.isNicked());
        }
    }

    @Test public void wrongUuidIsRejectedAndNadeshikoPageParses() throws Exception {
        String player = BedwarsData.object(profile("Player")).getAsJsonObject("player").toString();
        String page = "playerData = JSON.parse(decodeURIComponent(\"" + URLEncoder.encode(player, StandardCharsets.UTF_8.name()) + "\"))";
        AtomicInteger calls = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> {
            calls.incrementAndGet();
            return new OverlayHttp.Response(200, uri.getHost().equals("nadeshiko.io") ? page : profile("Wrong").replace(COMPACT, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        })) {
            assertEquals("Player", new BedwarsData(http).get(ID, "Player", new BedwarsData.Policy(true, false, "")).get().getName());
            assertEquals(2, calls.get());
        }
    }

    @Test public void workersAndQueueStayBounded() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> new OverlayHttp.Response(200, "{}"))) {
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 150; i++) futures.add(http.submit(() -> true, () -> {
                int active = running.incrementAndGet(); maximum.accumulateAndGet(active, Math::max);
                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                running.decrementAndGet(); return 1;
            }));
            assertTrue(futures.stream().filter(CompletableFuture::isCompletedExceptionally).count() >= 14);
            release.countDown();
            for (CompletableFuture<Integer> future : futures) future.handle((value, error) -> 0).get(2, TimeUnit.SECONDS);
            assertTrue(maximum.get() <= 8);
        }
    }

    @Test public void thresholdFormattingUsesRealTeamColorAndPrestige() {
        BedwarsStats stats = BedwarsData.parsePlayer(ID, "Player", BedwarsData.object(profile("Player")).getAsJsonObject("player"));
        String line = BedwarsTabLineFormatter.format(stats, "Player", "§c");
        assertTrue(line.contains("§6[245"));
        assertTrue(line.contains("§cPlayer"));
        assertTrue(line.contains("7.50"));
        assertEquals("§c", BedwarsTabLineFormatter.resolveTabNameColor("§a[VIP] §cPlayer", "Player", "§9"));
        assertTrue(BedwarsStarFormatter.formatBracketed(1000).contains("§c1§60§e0§a0"));
    }

    @Test public void experienceMatchesBedwarsPrestigeCurve() {
        assertEquals(0, BedwarsData.experienceToStar(499));
        assertEquals(1, BedwarsData.experienceToStar(500));
        assertEquals(2, BedwarsData.experienceToStar(1500));
        assertEquals(3, BedwarsData.experienceToStar(3500));
        assertEquals(4, BedwarsData.experienceToStar(7000));
        assertEquals(100, BedwarsData.experienceToStar(487000));
        assertEquals(101, BedwarsData.experienceToStar(487500));
    }

    @Test public void mapBlacklistSupportsWholeNamesAndWildcards() {
        assertTrue(PlayerNameUtil.mapMatches("lighthouse, air*\n*stone", "Lighthouse"));
        assertTrue(PlayerNameUtil.mapMatches("lighthouse, air*\n*stone", "Airshow"));
        assertTrue(PlayerNameUtil.mapMatches("lighthouse, air*\n*stone", "Sandstone"));
        assertFalse(PlayerNameUtil.mapMatches("Lighthouse", "Lighthouse 2"));
        assertFalse(PlayerNameUtil.mapMatches("[a-z]+", "Stone"));
    }

    @Test public void queueRequiresRealBedwarsLocation() {
        assertTrue(HypixelLocraw.parse("{\"server\":\"mini1\",\"gametype\":\"BEDWARS\",\"mode\":\"bedwars_eight_one\",\"map\":\"Airshow\"}").isBedwarsGameEligible());
        assertFalse(HypixelLocraw.parse("{\"server\":\"lobby1\",\"gametype\":\"BEDWARS\",\"lobbyname\":\"bedwarslobby1\"}").isBedwarsGameEligible());
        assertNull(HypixelLocraw.parse("not JSON"));
    }

    @Test public void nickRulesExcludeCurrentNicknameAndMatchCustomRules() {
        List<String> empty = Collections.emptyList();
        assertFalse(NickRules.matches("Abcd", "abcd", true, true, true, true, empty, empty, empty, empty));
        assertTrue(NickRules.matches("abCdef", "", false, false, false, true, empty, Arrays.asList("abc"), empty, empty));
        assertTrue(NickRules.matches("aaaNick", "", false, true, false, false, empty, empty, empty, empty));
        assertTrue(NickRules.matches("_Player", "", false, false, true, false, empty, empty, empty, empty));
    }

    private static final class Book {
        private final List<String> lines = Arrays.asList("a random name for", "§7you:", "§aNick_123", "extra");
    }

    @Test public void nicknameBookReaderUsesAdjacentYouLine() {
        NickBookReader reader = new NickBookReader();
        Book book = new Book();
        assertEquals("Nick_123", reader.read(book, String.class, Object::toString));
        assertEquals("Nick_123", reader.read(book, String.class, Object::toString));
        assertEquals("", reader.read(new Object(), String.class, Object::toString));
    }

    @Test public void cosmeticSamplesExtractKillerAndCounters(@TempDir Path directory) {
        DenickEvidence evidence = new DenickEvidence();
        evidence.load(directory);
        evidence.observe("Victim was sent the wrong way by HiddenName. FINAL KILL!");
        JsonObject body = evidence.cosmeticBody("HiddenName");
        assertNotNull(body);
        assertEquals("killmessages_to_the_moon", body.get("killMessage").getAsString());
        evidence.observe("Victim was HiddenName's final #10,001.");
        assertEquals(10001, evidence.cosmeticBody("HiddenName").get("finals").getAsInt());
        assertEquals(Arrays.asList("Candidate"), DenickEvidence.cosmeticNames("{\"players\":[{\"name\":\"Candidate\"},{\"name\":\"Candidate\"}]}"));
        assertNull(evidence.candidate(ID));
    }

    @Test public void skinEvidenceRemainsUnconfirmedAndPersistsAsObservation(@TempDir Path directory) throws Exception {
        DenickEvidence evidence = new DenickEvidence();
        evidence.load(directory);
        String json = "{\"profileName\":\"RealPlayer\",\"profileId\":\"" + COMPACT + "\",\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + String.join("", Collections.nCopies(64, "0")) + "\"}}}";
        DenickEvidence.Candidate candidate = evidence.skin("NickPlayer", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(candidate);
        assertEquals("RealPlayer", candidate.name);
        evidence.remember(ID, candidate);
        evidence.save(directory, ID, "NickPlayer", candidate);
        String saved = new String(Files.readAllBytes(directory.resolve("denick-observations.json")), StandardCharsets.UTF_8);
        assertTrue(saved.contains("\"confirmed\": false"));
        evidence.clear();
        evidence.load(directory);
        assertNull(evidence.candidate(ID));
    }

    @Test public void prefixAnimatesTrueRgbAndLeavesMessageBodyUnmarked() {
        String message = ArcadiumPrefix.message("Body");
        assertEquals("Arcadium §7> §fBody", ArcadiumPrefix.plainMarkers(message));
        int first = ArcadiumPrefix.color(0, 0, true, 0xFF55CDB1, 0xFF6DB9E8);
        int second = ArcadiumPrefix.color(0, 1000000000L, true, 0xFF55CDB1, 0xFF6DB9E8);
        assertNotEquals(first, second);
        assertEquals(0xFF55CDB1, ArcadiumPrefix.color(5, 1000000000L, false, 0xFF55CDB1, 0xFF6DB9E8));
    }
}
