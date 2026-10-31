package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class Keyless061Qa {
    private static int ticks, startedTick;
    private static long started;
    private static boolean done;
    private static HttpServer server;
    private static ExecutorService executor;
    private static OverlayHttp http;
    private static final CountDownLatch release = new CountDownLatch(1);
    private static final ArrayList<CompletableFuture<BedwarsStats>> roster = new ArrayList<>();
    private static final java.util.concurrent.ConcurrentLinkedQueue<String> errors = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public static boolean enabled() { return System.getProperty("arcadium.keyless061.output") != null; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    public static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        OneConfigConfig.limitUnfocusedFps = false;
        try {
            require(++ticks < 1000, "Native keyless QA timed out");
            if (started == 0 && ticks > 60) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline account");
                ClientPreferences.setSetupComplete(true);
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                executor = Executors.newFixedThreadPool(64); server.setExecutor(executor);
                server.createContext("/", response -> {
                    try {
                        byte[] bytes;
                        if (response.getRequestURI().getPath().equals("/slow")) {
                            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                            bytes = new byte[0]; response.sendResponseHeaders(503, -1);
                        } else {
                            String id = response.getRequestURI().getRawQuery().substring(5);
                            bytes = ("{\"success\":true,\"player\":{\"uuid\":\"" + id + "\",\"displayname\":\"Player\",\"achievements\":{\"bedwars_level\":245},\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":30,\"final_deaths_bedwars\":4}}}}").getBytes(StandardCharsets.UTF_8);
                            response.sendResponseHeaders(200, bytes.length); response.getResponseBody().write(bytes);
                        }
                    } catch (Exception ignored) { } finally { response.close(); }
                }); server.start();
                http = new OverlayHttp((uri, headers, body) -> {
                    require(!headers.containsKey("API-Key"), "A credential reached keyless transport");
                    String path = uri.getHost().equals("api.abyssoverlay.com") ? "/fast" : "/slow";
                    String id = uri.getQuery() == null ? uri.getPath().split("/")[2] : uri.getQuery().substring(5);
                    try { return OverlayHttp.exchange(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path + "?uuid=" + id), headers, body); }
                    catch (Exception error) { errors.add(path + ": " + error); throw error; }
                });
                var data = new BedwarsData(http); started = System.nanoTime(); startedTick = ticks;
                for (int i = 0; i < 24; i++) roster.add(data.get(new UUID(0x1234567812344234L, 0x9234123456789000L + i), "Player", new BedwarsData.Policy(true, false, "stored-secret")));
            } else if (started != 0 && roster.stream().allMatch(CompletableFuture::isDone)) {
                for (var request : roster) require(request.join().getStar() == 245, "Stats were not obtained");
                require(ticks - startedTick >= 5, "Networking blocked the game tick thread");
                require(release.getCount() == 1, "The first two providers finished before fallback worked");
                finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }
    private static void finish(Throwable failure) {
        done = true;
        var result = new java.util.LinkedHashMap<String, Object>(); result.put("passed", failure == null);
        result.put("elapsedMillis", started == 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        result.put("gameTicksDuringLookup", ticks - startedTick); result.put("players", roster.size());
        result.put("requestErrors", errors);
        if (failure != null) { result.put("error", failure.toString()); failure.printStackTrace(); }
        release.countDown(); if (http != null) http.close(); if (server != null) server.stop(0); if (executor != null) executor.shutdownNow();
        try { Files.writeString(Path.of(System.getProperty("arcadium.keyless061.output")), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
