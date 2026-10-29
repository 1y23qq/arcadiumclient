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
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
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
    private final LongSupplier clock;
    private final ThreadPoolExecutor workers = workers(8, 128, "arcadium-bedwars-http");
    private final ThreadPoolExecutor fallbackWorkers = workers(2, 64, "arcadium-bedwars-fallback");
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "arcadium-bedwars-delay");
        thread.setDaemon(true);
        return thread;
    });
    private final Semaphore timerSlots = new Semaphore(128);
    private final Set<CompletableFuture<?>> outstanding = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    public OverlayHttp() { this(OverlayHttp::exchange); }
    public OverlayHttp(Transport transport) { this(transport, System::currentTimeMillis); }
    public OverlayHttp(Transport transport, LongSupplier clock) {
        this.transport = transport;
        this.clock = clock;
        timers.setRemoveOnCancelPolicy(true);
    }

    public <T> CompletableFuture<T> submit(BooleanSupplier active, Supplier<T> action) {
        return submit(workers, active, action);
    }

    <T> CompletableFuture<T> submitFallback(BooleanSupplier active, Supplier<T> action) {
        return submit(fallbackWorkers, active, action);
    }

    CompletableFuture<Void> defer(Runnable action, long delayMillis) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!timerSlots.tryAcquire()) {
            result.completeExceptionally(new IOException("Overlay delay queue full"));
            return result;
        }
        outstanding.add(result);
        if (closed.get()) {
            outstanding.remove(result);
            timerSlots.release();
            result.cancel(false);
            return result;
        }
        ScheduledFuture<?> timer;
        try {
            timer = timers.schedule(() -> {
                try { if (!result.isDone()) action.run(); result.complete(null); }
                catch (Throwable error) {
                    result.completeExceptionally(error);
                    if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            outstanding.remove(result);
            timerSlots.release();
            result.completeExceptionally(new IOException("Overlay closed"));
            return result;
        }
        result.whenComplete((value, error) -> {
            timer.cancel(false);
            timerSlots.release();
            outstanding.remove(result);
        });
        if (closed.get()) result.cancel(false);
        return result;
    }

    private <T> CompletableFuture<T> submit(ThreadPoolExecutor executor, BooleanSupplier active, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        FutureTask<Void> job = new FutureTask<>(() -> {
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
        }, null);
        outstanding.add(result);
        result.whenComplete((value, error) -> {
            outstanding.remove(result);
            if (result.isCancelled()) { job.cancel(true); executor.remove(job); }
        });
        if (closed.get()) { result.cancel(false); return result; }
        try { executor.execute(job); }
        catch (RejectedExecutionException ignored) { result.completeExceptionally(new IOException("Overlay queue full")); }
        return result;
    }

    private static ThreadPoolExecutor workers(int count, int queueSize, String name) {
        return new ThreadPoolExecutor(count, count, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueSize), task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    public Response request(String address, Credential credential, String key, String body, BooleanSupplier active) {
        return request(address, credential, key, body, active, 2);
    }

    public Response requestOnce(String address, Credential credential, String key, BooleanSupplier active) {
        return request(address, credential, key, null, active, 1);
    }

    public void resetHypixelBackoff() {
        blockedUntil.remove("api.hypixel.net");
        failures.remove("api.hypixel.net");
    }

    private Response request(String address, Credential credential, String key, String body, BooleanSupplier active, int attempts) {
        URI uri;
        try { uri = URI.create(address); }
        catch (IllegalArgumentException ignored) { return new Response(0, ""); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) return new Response(0, "");
        if (credential == Credential.HYPIXEL && !"api.hypixel.net".equalsIgnoreCase(host)) throw new IllegalArgumentException("Hypixel credential host");
        if (credential == Credential.SERAPH && !"api.seraph.si".equalsIgnoreCase(host)) throw new IllegalArgumentException("Seraph credential host");
        if (!active.getAsBoolean()) throw new CancellationException();
        if (clock.getAsLong() < blockedUntil.getOrDefault(host, 0L)) return new Response(429, "");
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json,text/html;q=0.8");
        headers.put("User-Agent", "Arcadium Hypixel Stats (+https://arcadiumclient.com)");
        if (credential != Credential.NONE && key != null && !key.isEmpty()) {
            headers.put(credential == Credential.HYPIXEL ? "API-Key" : "seraph-api-key", key);
        }
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (!active.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
            try {
                Response response = transport.exchange(uri, Collections.unmodifiableMap(headers), body);
                if (!active.getAsBoolean()) throw new CancellationException();
                if (response.status == 429) {
                    block(host, Math.max(30, response.retryAfterSeconds));
                } else if (credential == Credential.HYPIXEL && (response.status == 401 || response.status == 403)) {
                    return response;
                } else if (response.status >= 500 && attempt + 1 < attempts && body == null) {
                    continue;
                } else if (response.status >= 400 && response.status != 404) {
                    block(host, Math.min(60, 5L << Math.min(4, failures.merge(host, 1, Integer::sum) - 1)));
                } else if (response.status >= 200 && response.status < 300) {
                    failures.remove(host);
                    if (response.rateRemaining == 0) block(host, Math.max(1, response.rateResetSeconds));
                }
                return response;
            } catch (IOException ignored) {
                if (!active.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
                if (attempt + 1 == attempts || body != null) {
                    block(host, 30);
                    return new Response(0, "");
                }
            }
        }
        return new Response(0, "");
    }

    private void block(String host, long seconds) {
        blockedUntil.merge(host, clock.getAsLong() + Math.min(300, seconds) * 1000L, Math::max);
    }

    private static Response exchange(URI uri, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(3000);
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

    @Override public void close() {
        closed.set(true);
        outstanding.forEach(future -> future.cancel(true));
        workers.shutdownNow();
        fallbackWorkers.shutdownNow();
        timers.shutdownNow();
    }
}
