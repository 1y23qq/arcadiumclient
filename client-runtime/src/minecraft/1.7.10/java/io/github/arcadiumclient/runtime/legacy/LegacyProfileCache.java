// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.runtime.ClientBuild;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class LegacyProfileCache implements AutoCloseable {
    interface Lookup { Result find(String name) throws Exception; }
    record Result(UUID uuid, long ttlMillis, long retryMillis) { }
    private record Entry(UUID uuid, long expiresAt) { }
    private final Object lock = new Object();
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(32, 0.75f, true);
    private final LinkedHashSet<String> pending = new LinkedHashSet<>();
    private final ScheduledExecutorService worker;
    private final Lookup lookup;
    private final LongSupplier now;
    private final int maximum, queueLimit;
    private final long intervalMillis;
    private String inFlight;
    private long nextAllowed;
    private boolean scheduled, closed;

    LegacyProfileCache() { this(LegacyProfileCache::lookupOfficial, () -> System.nanoTime() / 1_000_000L, 2048, 256, 1000); }

    LegacyProfileCache(Lookup lookup, LongSupplier now, int maximum, int queueLimit, long intervalMillis) {
        this.lookup = lookup; this.now = now; this.maximum = maximum; this.queueLimit = queueLimit; this.intervalMillis = intervalMillis;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "arcadium-profile-lookup"); thread.setDaemon(true); return thread;
        });
    }

    static String canonical(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) return null;
        for (int index = 0; index < name.length(); index++) {
            char ch = name.charAt(index);
            if (!(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '_')) return null;
        }
        return name.toLowerCase(Locale.ROOT);
    }

    UUID cached(String name) {
        String key = canonical(name);
        if (key == null) return null;
        synchronized (lock) {
            Entry entry = entries.get(key);
            if (entry == null || entry.expiresAt <= now.getAsLong()) return null;
            return entry.uuid;
        }
    }

    void watch(Collection<String> names) {
        LinkedHashSet<String> wanted = new LinkedHashSet<>();
        for (String name : names) {
            String key = canonical(name);
            if (key != null) wanted.add(key);
            if (wanted.size() >= queueLimit) break;
        }
        synchronized (lock) {
            if (closed) return;
            pending.retainAll(wanted);
            long time = now.getAsLong();
            for (String name : wanted) {
                Entry entry = entries.get(name);
                if ((entry == null || entry.expiresAt <= time) && !name.equals(inFlight)) pending.add(name);
                if (pending.size() >= queueLimit) break;
            }
            schedule();
        }
    }

    void pause() { synchronized (lock) { pending.clear(); } }

    private void schedule() {
        if (closed || scheduled || pending.isEmpty()) return;
        scheduled = true;
        worker.schedule(this::advance, Math.max(0, nextAllowed - now.getAsLong()), TimeUnit.MILLISECONDS);
    }

    private void advance() {
        String name;
        synchronized (lock) {
            if (closed || pending.isEmpty()) { scheduled = false; return; }
            Iterator<String> iterator = pending.iterator(); name = iterator.next(); iterator.remove(); inFlight = name;
        }
        Result result;
        try { result = lookup.find(name); }
        catch (Exception ignored) { result = new Result(null, 30_000, 15_000); }
        if (result == null) result = new Result(null, 30_000, 15_000);
        synchronized (lock) {
            inFlight = null; scheduled = false;
            if (closed) return;
            UUID uuid = result.uuid != null && result.uuid.version() == 4 ? result.uuid : null;
            long time = now.getAsLong();
            entries.put(name, new Entry(uuid, time + Math.max(1, Math.min(300_000, result.ttlMillis))));
            while (entries.size() > maximum) entries.remove(entries.keySet().iterator().next());
            nextAllowed = time + Math.max(intervalMillis, Math.min(300_000, result.retryMillis));
            schedule();
        }
    }

    int cachedCount() { synchronized (lock) { return entries.size(); } }
    int queuedCount() { synchronized (lock) { return pending.size() + (inFlight == null ? 0 : 1); } }

    @Override public void close() {
        synchronized (lock) { closed = true; pending.clear(); entries.clear(); }
        worker.shutdownNow();
    }

    private static final class Transport {
        static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static Result lookupOfficial(String name) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name))
            .timeout(Duration.ofSeconds(5)).header("Accept", "application/json")
            .header("User-Agent", "Arcadium/" + ClientBuild.INSTANCE.getVERSION()).GET().build();
        HttpResponse<String> response = Transport.HTTP.send(request,
            HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), 4096));
        if (response.statusCode() == 404 || response.statusCode() == 204 || response.statusCode() == 400)
            return new Result(null, 60_000, 0);
        if (response.statusCode() == 429) return new Result(null, 60_000, 60_000);
        if (response.statusCode() != 200) return new Result(null, 30_000, 15_000);
        return new Result(parseProfile(response.body(), name), 300_000, 0);
    }

    static UUID parseProfile(String body, String requested) {
        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.get("name").getAsJsonPrimitive().isString() || !json.get("id").getAsJsonPrimitive().isString()) return null;
            String name = json.get("name").getAsString(), id = json.get("id").getAsString();
            if (!name.equalsIgnoreCase(requested) || id.length() != 32) return null;
            for (int i = 0; i < id.length(); i++) {
                char ch = id.charAt(i);
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F')) return null;
            }
            UUID uuid = UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                + "-" + id.substring(16, 20) + "-" + id.substring(20));
            return uuid.version() == 4 ? uuid : null;
        } catch (RuntimeException ignored) { return null; }
    }
}
