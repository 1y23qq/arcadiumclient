// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
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
    private final Map<UUID, Cached> cache = new HashMap<>();
    private final Map<UUID, CompletableFuture<BedwarsStats>> pending = new HashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean invalidKey;
    private volatile int preferredProvider;

    public BedwarsData(OverlayHttp http) { this.http = http; }

    public synchronized BedwarsStats present(UUID id) {
        Cached cached = cache.get(id);
        return cached != null && cached.expires > System.currentTimeMillis() ? cached.value : null;
    }

    public synchronized CompletableFuture<BedwarsStats> get(UUID id, String name, Policy policy) {
        BedwarsStats cached = present(id);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        if (pending.containsKey(id)) return pending.get(id);
        if (id == null || (!policy.keyless && policy.hypixelKey.isEmpty())) return CompletableFuture.completedFuture(BedwarsStats.unavailable(id, name));
        long ticket = generation.get();
        CompletableFuture<BedwarsStats> shared = new CompletableFuture<>();
        pending.put(id, shared);
        CompletableFuture<BedwarsStats> work = http.submit(() -> generation.get() == ticket,
                () -> fetch(id, name, policy, () -> generation.get() == ticket));
        work.whenComplete((value, error) -> {
            synchronized (BedwarsData.this) {
                if (generation.get() != ticket) { shared.cancel(false); return; }
                BedwarsStats result = error == null && value != null ? value : BedwarsStats.unavailable(id, name);
                if (cache.size() >= 512) cache.clear();
                cache.put(id, new Cached(result, System.currentTimeMillis() + (result.isUnavailable() ? 15000 : 120000)));
                pending.remove(id, shared);
                shared.complete(result);
            }
        });
        shared.whenComplete((value, error) -> { if (shared.isCancelled()) work.cancel(true); });
        return shared;
    }

    public synchronized void clear() {
        generation.incrementAndGet();
        pending.values().forEach(future -> future.cancel(true));
        pending.clear();
        cache.clear();
        invalidKey = false;
    }

    public boolean invalidKey() { return invalidKey; }

    private BedwarsStats fetch(UUID id, String name, Policy policy, BooleanSupplier active) {
        if (id.version() == 1) return BedwarsStats.nicked(id, name);
        String compact = id.toString().replace("-", "");
        if (!policy.hypixelKey.isEmpty()) {
            OverlayHttp.Response response = http.request("https://api.hypixel.net/v2/player?uuid=" + compact,
                    OverlayHttp.Credential.HYPIXEL, policy.hypixelKey, null, active);
            if (response.status == 401 || response.status == 403) invalidKey = true;
            if (response.status == 200) {
                JsonObject root = object(response.body);
                if (root != null && bool(root, "success")) {
                    if (root.has("player") && root.get("player").isJsonNull()) return BedwarsStats.nicked(id, name);
                    JsonObject player = child(root, "player");
                    if (player != null && matchesUuid(player, compact)) return parsePlayer(id, name, player);
                }
            }
        }
        if (policy.keyless) {
            for (int offset = 0; offset < PROVIDERS.length; offset++) {
                int provider = (preferredProvider + offset) % PROVIDERS.length;
                String url = PROVIDERS[provider] + compact + (provider == 1 ? "/network" : "");
                OverlayHttp.Response response = http.request(url, OverlayHttp.Credential.NONE, "", null, active);
                if (response.status != 200) continue;
                JsonObject player = parseKeyless(provider, response.body);
                if (player == null || !matchesUuid(player, compact)) continue;
                preferredProvider = provider;
                return parsePlayer(id, name, player);
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
            return root != null && bool(root, "success") ? child(root, "player") : null;
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
        if (star < 0) star = experienceToStar(integer(bw, "Experience", integer(bw, "Experience_bedwars", 0)));
        int kills = Math.max(0, integer(bw, "final_kills_bedwars", 0));
        int deaths = Math.max(0, integer(bw, "final_deaths_bedwars", 0));
        return new BedwarsStats(id, name, Math.max(0, star), deaths == 0 ? kills : (double) kills / deaths, kills, deaths, false, false,
                string(player, "packageRank"), string(player, "newPackageRank"), string(player, "monthlyPackageRank"),
                string(player, "rankPlusColor"), string(player, "monthlyRankColor"), string(player, "prefix"));
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
