// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

public final class HypixelHeightLimit {
    private static final URI DATA = URI.create("https://data-v2.polyfrost.org/hlm/trans-rights-are-human-rights.json");
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static volatile Map<String, Integer> limits = Map.of();
    private static volatile String map;
    private static volatile boolean bedwars;
    private static boolean initialized, loading;
    private static long nextLoad;
    private HypixelHeightLimit() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        var api = HypixelModAPI.getInstance();
        api.createHandler(ClientboundLocationPacket.class, packet -> Minecraft.getInstance().execute(() -> location(packet)));
        api.subscribeToEventPacket(ClientboundLocationPacket.class);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { bedwars = false; map = null; });
    }

    public static void location(ClientboundLocationPacket packet) {
        bedwars = packet.getServerType().map(type -> type.getName().equals("Bed Wars")).orElse(false);
        map = bedwars ? packet.getMap().map(HeightLimitMaps::key).orElse(null) : null;
    }

    public static boolean inBedwars() { return bedwars; }

    public static Integer maximum() {
        if (!bedwars) return null;
        requestData();
        return map == null ? null : limits.get(map);
    }

    private static synchronized void requestData() {
        long now = System.nanoTime();
        if (loading || now < nextLoad) return;
        loading = true;
        nextLoad = now + Duration.ofMinutes(15).toNanos();
        Path cache = FabricLoader.getInstance().getGameDir().resolve(".arcadium/cache/height-limits.json");
        CompletableFuture.runAsync(() -> {
            try {
                if (limits.isEmpty()) {
                    try {
                        if (Files.isRegularFile(cache) && Files.size(cache) <= MAX_BYTES)
                            limits = HeightLimitMaps.parse(Files.readString(cache));
                    } catch (Exception invalidCache) {
                        LoggerFactory.getLogger("Arcadium/Height Limit").debug("Ignoring unreadable height-limit cache", invalidCache);
                    }
                }
                try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                    var request = HttpRequest.newBuilder(DATA).timeout(Duration.ofSeconds(10)).header("User-Agent", "Arcadium/0.5.0").GET().build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    try (var body = response.body()) {
                        if (response.statusCode() != 200) throw new java.io.IOException("Height-limit response " + response.statusCode());
                        byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                        if (bytes.length > MAX_BYTES) throw new java.io.IOException("Height-limit response is too large.");
                        var parsed = HeightLimitMaps.parse(new String(bytes, StandardCharsets.UTF_8));
                        limits = parsed;
                        Files.createDirectories(cache.getParent());
                        Files.write(cache, bytes);
                    }
                }
            } catch (Exception failure) {
                LoggerFactory.getLogger("Arcadium/Height Limit").debug("Kept cached height limits after a refresh failure", failure);
            } finally {
                synchronized (HypixelHeightLimit.class) { loading = false; }
            }
        });
    }
}
