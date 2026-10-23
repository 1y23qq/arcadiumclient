// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.function.LongSupplier;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BedwarsData {
    public static final class Policy {
        public final boolean keyless;
        public final String hypixelKey;

        public Policy(boolean keyless, boolean useHypixelKey, String key) {
            this.keyless = keyless;
            hypixelKey = useHypixelKey && key != null ? key.trim() : "";
        }

        public boolean same(Policy other) { return other != null && keyless == other.keyless && hypixelKey.equals(other.hypixelKey); }
    }

    private static final Pattern NADESHIKO = Pattern.compile("playerData\\s*=\\s*JSON\\.parse\\(decodeURIComponent\\(\"(.*?)\"\\)\\)", Pattern.DOTALL);
    private static final String[] PROVIDERS = {
            "https://api.bordic.xyz/v3/cache/hypixel?uuid=",
            "https://nadeshiko.io/player/",
            "https://api.abyssoverlay.com/player?uuid="
    };
    private final OverlayHttp http;
    private final Map<UUID, Cached> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<UUID, CompletableFuture<BedwarsStats>> pending = new HashMap<>();
    private final Set<UUID> matchPlayers = new HashSet<>();
    private final LongSupplier clock;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong retryRevision = new AtomicLong();
    private final AtomicLongArray providerRetryAt = new AtomicLongArray(PROVIDERS.length);
    private volatile String rejectedHypixelKey = "";
    private volatile int preferredProvider;
    private String matchServer;

    public BedwarsData(OverlayHttp http) { this(http, System::currentTimeMillis); }
    public BedwarsData(OverlayHttp http, LongSupplier clock) { this.http = http; this.clock = clock; }

    public synchronized void beginMatch(String gameServer) {
        if (gameServer == null || gameServer.isEmpty()) { releaseMatch(); return; }
        if (!gameServer.equals(matchServer)) matchPlayers.clear();
        matchServer = gameServer;
    }

    public synchronized void retainForMatch(Collection<UUID> players) {
        if (matchServer == null) return;
        for (UUID player : players) {
            if (matchPlayers.size() >= 256) break;
            if (player != null) matchPlayers.add(player);
        }
    }
    public synchronized void releaseMatch() { matchPlayers.clear(); matchServer = null; }
    public synchronized boolean hasMatch() { return matchServer != null; }
    public synchronized int pendingCount() { return pending.size(); }

    public synchronized void retryUnavailable() {
        retryRevision.incrementAndGet();
        cache.entrySet().removeIf(entry -> entry.getValue().value.isUnavailable());
        rejectedHypixelKey = "";
        http.resetHypixelBackoff();
    }

    public synchronized BedwarsStats present(UUID id) {
        Cached cached = cache.get(id);
        return cached != null && (cached.expires > clock.getAsLong() || !cached.value.isUnavailable() && matchPlayers.contains(id)) ? cached.value : null;
    }

    public synchronized CompletableFuture<BedwarsStats> get(UUID id, String name, Policy policy) {
        BedwarsStats cached = present(id);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        if (pending.containsKey(id)) return pending.get(id);
        if (id == null || (!policy.keyless && policy.hypixelKey.isEmpty())) return CompletableFuture.completedFuture(BedwarsStats.unavailable(id, name));
        long ticket = generation.get();
        long retryTicket = retryRevision.get();
        CompletableFuture<BedwarsStats> shared = new CompletableFuture<>();
        pending.put(id, shared);
        BooleanSupplier active = () -> generation.get() == ticket && !shared.isCancelled();
        CompletableFuture<BedwarsStats> work = http.submit(active, () -> fetch(id, name, policy, active));
        work.whenComplete((value, error) -> {
            synchronized (BedwarsData.this) {
                if (generation.get() != ticket || shared.isCancelled()) {
                    pending.remove(id, shared);
                    shared.cancel(false);
                    return;
                }
                BedwarsStats result = error == null && value != null ? value : BedwarsStats.unavailable(id, name);
                if (cache.size() >= 2048) {
                    UUID oldest = cache.keySet().stream().filter(key -> !matchPlayers.contains(key)).findFirst().orElse(null);
                    if (oldest != null) cache.remove(oldest);
                }
                Cached previous = cache.get(id);
                if (result.isUnavailable() && previous != null && !previous.value.isUnavailable()) result = previous.value;
                if (!result.isUnavailable() || retryRevision.get() == retryTicket)
                    cache.put(id, new Cached(result, clock.getAsLong() + (result.isUnavailable() ? 10000 : 600000)));
                pending.remove(id, shared);
                shared.complete(result);
            }
        });
        shared.whenComplete((value, error) -> { if (shared.isCancelled()) work.cancel(true); });
        return shared;
    }

    public synchronized void clear() {
        generation.incrementAndGet();
        var cancelled = new ArrayList<>(pending.values());
        pending.clear();
        cache.clear();
        releaseMatch();
        rejectedHypixelKey = "";
        cancelled.forEach(future -> future.cancel(true));
    }

    public boolean invalidKey() { return !rejectedHypixelKey.isEmpty(); }
    public boolean invalidKey(String key) { return key != null && !key.isEmpty() && key.equals(rejectedHypixelKey); }

    private BedwarsStats fetch(UUID id, String name, Policy policy, BooleanSupplier active) {
        if (id.version() == 1) return BedwarsStats.nicked(id, name);
        String compact = id.toString().replace("-", "");
        if (!policy.hypixelKey.isEmpty() && !invalidKey(policy.hypixelKey)) {
            OverlayHttp.Response response = http.requestOnce("https://api.hypixel.net/v2/player?uuid=" + compact,
                    OverlayHttp.Credential.HYPIXEL, policy.hypixelKey, active);
            if (response.status == 401 || response.status == 403) rejectedHypixelKey = policy.hypixelKey;
            if (response.status == 200) {
                JsonObject root = object(response.body);
                if (root != null && bool(root, "success")) {
                    if (root.has("player") && root.get("player").isJsonNull()) return BedwarsStats.nicked(id, name);
                    JsonObject player = child(root, "player");
                    if (player != null && matchesUuid(player, compact)) {
                        BedwarsStats stats = parsePlayer(id, name, player);
                        if (!stats.isUnavailable()) return stats;
                    }
                }
            }
        }
        if (policy.keyless) {
            int firstProvider = preferredProvider;
            for (int offset = 0; offset < PROVIDERS.length; offset++) {
                int provider = (firstProvider + offset) % PROVIDERS.length;
                if (clock.getAsLong() < providerRetryAt.get(provider)) continue;
                String url = PROVIDERS[provider] + compact + (provider == 1 ? "/network" : "");
                OverlayHttp.Response response = http.requestOnce(url, OverlayHttp.Credential.NONE, "", active);
                if (response.status != 200) {
                    if (response.status != 404) providerRetryAt.set(provider, clock.getAsLong() + 5000);
                    continue;
                }
                JsonObject player = parseKeyless(provider, response.body);
                if (player == null || !matchesUuid(player, compact)) {
                    if (player == null) providerRetryAt.set(provider, clock.getAsLong() + 5000);
                    continue;
                }
                BedwarsStats stats = parsePlayer(id, name, player);
                if (!stats.isUnavailable()) { preferredProvider = provider; return stats; }
            }
        }
        return BedwarsStats.unavailable(id, name);
    }

    public static JsonObject parseKeyless(int provider, String body) {
        try {
            if (provider == 1) {
                Matcher matcher = NADESHIKO.matcher(body);
                return matcher.find() ? object(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8.name())) : null;
            }
            JsonObject root = object(body);
            if (root == null || !bool(root, "success")) return null;
            return child(root, "player");
        } catch (Exception ignored) { return null; }
    }

    private static boolean matchesUuid(JsonObject player, String compact) {
        String uuid = string(player, "uuid");
        return uuid != null && uuid.replace("-", "").equalsIgnoreCase(compact);
    }

    public static BedwarsStats parsePlayer(UUID id, String fallback, JsonObject player) {
        String name = string(player, "displayname");
        if (!PlayerNameUtil.valid(name)) name = fallback;
        JsonObject bw = child(child(player, "stats"), "Bedwars");
        int star = integer(child(player, "achievements"), "bedwars_level", -1);
        int experience = integer(bw, "Experience", integer(bw, "Experience_bedwars", -1));
        int kills = integer(bw, "final_kills_bedwars", -1), deaths = integer(bw, "final_deaths_bedwars", -1);
        int networkLevel = networkLevel(player);
        if (star < 0 && experience < 0 && kills < 0 && deaths < 0 && networkLevel < 0) return BedwarsStats.unavailable(id, name);
        if (star < 0) star = experienceToStar(Math.max(0, experience));
        kills = Math.max(0, kills); deaths = Math.max(0, deaths);
        return new BedwarsStats(id, name, Math.max(0, star), deaths == 0 ? kills : (double) kills / deaths, kills, deaths, false, false,
                string(player, "packageRank"), string(player, "newPackageRank"), string(player, "monthlyPackageRank"),
                string(player, "rankPlusColor"), string(player, "monthlyRankColor"), string(player, "prefix"), networkLevel);
    }

    public static int networkLevel(JsonObject player) {
        try {
            double experience = Double.parseDouble(string(player, "networkExp"));
            if (!Double.isFinite(experience) || experience < 0) return -1;
            return (int) Math.max(1, Math.floor((Math.sqrt(2 * experience + 30625) - 125) / 50));
        } catch (RuntimeException invalid) { return -1; }
    }

    public static int experienceToStar(long xp) {
        if (xp <= 0) return 0;
        long prestige = xp / 487000;
        long remaining = xp % 487000;
        int level = (int) Math.min(1000000, prestige * 100);
        int[] early = {500, 1000, 2000, 3500};
        for (int cost : early) {
            if (remaining < cost) return level;
            remaining -= cost;
            level++;
        }
        return level + (int) (remaining / 5000);
    }

    public static JsonObject object(String body) {
        try { JsonElement value = new JsonParser().parse(body); return value.isJsonObject() ? value.getAsJsonObject() : null; }
        catch (RuntimeException ignored) { return null; }
    }
    public static JsonObject child(JsonObject root, String key) {
        if (root == null) return null;
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
    public static String string(JsonObject root, String key) {
        if (root == null) return null;
        JsonElement value = root.get(key);
        try { return value != null && value.isJsonPrimitive() ? value.getAsString() : null; }
        catch (RuntimeException ignored) { return null; }
    }
    public static int integer(JsonObject root, String key, int fallback) {
        try { return Integer.parseInt(string(root, key)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    public static boolean bool(JsonObject root, String key) { return "true".equalsIgnoreCase(string(root, key)); }

    private static final class Cached {
        final BedwarsStats value;
        final long expires;
        Cached(BedwarsStats value, long expires) { this.value = value; this.expires = expires; }
    }
}
