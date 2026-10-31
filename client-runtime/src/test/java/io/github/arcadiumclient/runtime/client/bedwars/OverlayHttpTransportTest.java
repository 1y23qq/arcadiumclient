// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.bedwars;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayHttpTransportTest {
    @Test void responseBodyDeadlineIncludesContinuousSlowDrips() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/drip", response -> {
            try {
                response.sendResponseHeaders(200, 0);
                for (int i = 0; i < 30; i++) {
                    response.getResponseBody().write('a'); response.getResponseBody().flush();
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
                }
            } catch (IOException ignored) { } finally { response.close(); }
        });
        server.start();
        try {
            long before = System.nanoTime();
            assertThrows(IOException.class, () -> OverlayHttp.exchange(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/drip"), Map.of(), null, 400));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1800);
        } finally { server.stop(0); }
    }

    @Test void cancellingRealRequestsReleasesEveryProviderWorkerBeforeTheServerResponds() throws Exception {
        CountDownLatch entered = new CountDownLatch(8), release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newFixedThreadPool(16);
        server.setExecutor(executor);
        server.createContext("/", response -> {
            try {
                if (response.getRequestURI().getPath().equals("/blocked")) {
                    entered.countDown();
                    try { release.await(8, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                }
                response.sendResponseHeaders(200, 2); response.getResponseBody().write(new byte[]{'o', 'k'});
            } catch (IOException ignored) { } finally { response.close(); }
        });
        server.start();
        try (OverlayHttp http = new OverlayHttp((uri, headers, body) -> OverlayHttp.exchange(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + uri.getPath()), headers, body))) {
            var requests = new ArrayList<CompletableFuture<OverlayHttp.Response>>();
            for (int i = 0; i < 8; i++) requests.add(http.submitKeyless(0, () -> true,
                    () -> http.requestKeyless("https://api.bordic.xyz/blocked", () -> true)));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            requests.forEach(future -> future.cancel(true));
            var fast = new ArrayList<CompletableFuture<OverlayHttp.Response>>();
            for (int i = 0; i < 8; i++) fast.add(http.submitKeyless(0, () -> true,
                    () -> http.requestKeyless("https://api.bordic.xyz/fast", () -> true)));
            CompletableFuture.allOf(fast.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
            for (var future : fast) assertEquals(200, future.get().status);
            assertEquals(1, release.getCount());
        } finally { release.countDown(); server.stop(0); executor.shutdownNow(); }
    }

    @Test void oversizedResponsesAreRejectedAndRedirectsStayDisabled() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger followed = new AtomicInteger();
        server.createContext("/large", response -> {
            try {
                byte[] bytes = new byte[4 * 1024 * 1024 + 64];
                response.sendResponseHeaders(200, bytes.length); response.getResponseBody().write(bytes);
            } catch (IOException ignored) { } finally { response.close(); }
        });
        server.createContext("/redirect", response -> {
            response.getResponseHeaders().add("Location", "/destination"); response.sendResponseHeaders(302, -1); response.close();
        });
        server.createContext("/destination", response -> { followed.incrementAndGet(); response.sendResponseHeaders(200, -1); response.close(); });
        server.createContext("/limited", response -> {
            response.getResponseHeaders().add("Retry-After", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(45)));
            response.sendResponseHeaders(429, -1); response.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            assertThrows(IOException.class, () -> OverlayHttp.exchange(URI.create(base + "/large"), Map.of(), null));
            assertEquals(302, OverlayHttp.exchange(URI.create(base + "/redirect"), Map.of("API-Key", "synthetic"), null).status);
            assertEquals(0, followed.get());
            long retry = OverlayHttp.exchange(URI.create(base + "/limited"), Map.of(), null).retryAfterSeconds;
            assertTrue(retry >= 43 && retry <= 46);
        } finally { server.stop(0); }
    }
}
