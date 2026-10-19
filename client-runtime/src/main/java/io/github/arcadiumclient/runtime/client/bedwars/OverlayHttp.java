// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class OverlayHttp implements AutoCloseable {
    public enum Credential { NONE, HYPIXEL, SERAPH }

    public static final class Response {
        public final int status;
        public final String body;
        public final long retryAfterSeconds;
        public final long rateRemaining;
        public final long rateResetSeconds;

        public Response(int status, String body) { this(status, body, 0, -1, 0); }
        public Response(int status, String body, long retry, long remaining, long reset) {
            this.status = status;
            this.body = body;
            retryAfterSeconds = retry;
            rateRemaining = remaining;
            rateResetSeconds = reset;
        }
    }

    public interface Transport { Response exchange(URI uri, Map<String, String> headers, String body) throws IOException; }
    private final Transport transport;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), task -> {
                Thread thread = new Thread(task, "arcadium-bedwars-http");
                thread.setDaemon(true);
                return thread;
            });
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    public OverlayHttp() { this(OverlayHttp::exchange); }
    public OverlayHttp(Transport transport) { this.transport = transport; }

    public <T> CompletableFuture<T> submit(BooleanSupplier active, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable job = () -> {
            if (result.isCancelled() || !active.getAsBoolean()) { result.cancel(false); return; }
            try {
                T value = action.get();
                if (!active.getAsBoolean()) result.cancel(false);
                else result.complete(value);
            } catch (CancellationException ignored) {
                result.cancel(false);
            } catch (Throwable error) {
                result.completeExceptionally(error);
                if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
            }
        };
        try { workers.execute(job); }
        catch (RejectedExecutionException ignored) { result.completeExceptionally(new IOException("Overlay queue full")); }
        result.whenComplete((value, error) -> { if (result.isCancelled()) workers.remove(job); });
        return result;
    }

    public Response request(String address, Credential credential, String key, String body, BooleanSupplier active) {
        URI uri;
        try { uri = URI.create(address); }
        catch (IllegalArgumentException ignored) { return new Response(0, ""); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) return new Response(0, "");
        if (credential == Credential.HYPIXEL && !"api.hypixel.net".equalsIgnoreCase(host)) throw new IllegalArgumentException("Hypixel credential host");
        if (credential == Credential.SERAPH && !"api.seraph.si".equalsIgnoreCase(host)) throw new IllegalArgumentException("Seraph credential host");
        if (!active.getAsBoolean()) throw new CancellationException();
        if (System.currentTimeMillis() < blockedUntil.getOrDefault(host, 0L)) return new Response(429, "");
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json,text/html;q=0.8");
        headers.put("User-Agent", "Arcadium/0.3.3 BedwarsOverlay");
        if (credential != Credential.NONE && key != null && !key.isEmpty()) {
            headers.put(credential == Credential.HYPIXEL ? "API-Key" : "seraph-api-key", key);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!active.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
            try {
                Response response = transport.exchange(uri, Collections.unmodifiableMap(headers), body);
                if (!active.getAsBoolean()) throw new CancellationException();
                if (response.status == 429) {
                    block(host, Math.max(30, response.retryAfterSeconds));
                } else if (response.status >= 500 && attempt == 0 && body == null) {
                    continue;
                } else if (response.status >= 400 && response.status != 404) {
                    block(host, Math.min(300, 15L << Math.min(4, failures.merge(host, 1, Integer::sum))));
                } else if (response.status >= 200 && response.status < 300) {
                    failures.remove(host);
                    if (response.rateRemaining == 0) block(host, Math.max(1, response.rateResetSeconds));
                }
                return response;
            } catch (IOException ignored) {
                if (attempt == 1 || body != null) {
                    block(host, 30);
                    return new Response(0, "");
                }
            }
        }
        return new Response(0, "");
    }

    private void block(String host, long seconds) {
        blockedUntil.merge(host, System.currentTimeMillis() + Math.min(300, seconds) * 1000L, Math::max);
    }

    private static Response exchange(URI uri, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod(body == null ? "GET" : "POST");
        headers.forEach(connection::setRequestProperty);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            String text = "";
            try (InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
                if (in != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] bytes = new byte[8192];
                    int read;
                    while ((read = in.read(bytes)) != -1) {
                        if (out.size() + read > 4 * 1024 * 1024) throw new IOException("Overlay response too large");
                        out.write(bytes, 0, read);
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                    }
                    text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return new Response(status, text, numberHeader(connection, "Retry-After"),
                    numberHeader(connection, "RateLimit-Remaining"), numberHeader(connection, "RateLimit-Reset"));
        } finally { connection.disconnect(); }
    }

    private static long numberHeader(HttpURLConnection connection, String name) {
        try { return Long.parseLong(connection.getHeaderField(name)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    @Override public void close() { workers.shutdownNow(); }
}
