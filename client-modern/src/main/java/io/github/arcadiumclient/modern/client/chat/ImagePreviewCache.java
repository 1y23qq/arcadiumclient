// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.chat;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ImagePreviewCache implements AutoCloseable {
    private final PublicImageFetcher fetcher;
    private final ThreadPoolExecutor workers;
    private final LinkedHashMap<String, BufferedImage> ready = new LinkedHashMap<>(8, .75f, true);
    private final LinkedHashMap<String, Long> failed = new LinkedHashMap<>();
    private final Set<String> pending = new HashSet<>();
    private long pixels;

    public ImagePreviewCache(PublicImageFetcher fetcher) {
        this.fetcher = fetcher;
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), task -> {
            Thread thread = new Thread(task, "Arcadium-Image-Preview"); thread.setDaemon(true); return thread;
        });
    }

    public synchronized BufferedImage request(String link) {
        if (link == null || link.length() > 4096 || workers.isShutdown()) return null;
        BufferedImage image = ready.get(link);
        if (image != null) return image;
        if (pending.contains(link) || failed.getOrDefault(link, 0L) > System.nanoTime()) return null;
        pending.add(link);
        try { workers.execute(() -> load(link)); }
        catch (RejectedExecutionException busy) { pending.remove(link); fail(link, 2); }
        return null;
    }

    private void load(String link) {
        try {
            BufferedImage image = fetcher.fetch(link);
            synchronized (this) {
                if (workers.isShutdown()) return;
                ready.put(link, image); pixels += (long) image.getWidth() * image.getHeight();
                while (ready.size() > 8 || pixels > 8 * 1024 * 1024) {
                    String oldest = ready.keySet().iterator().next();
                    BufferedImage removed = ready.remove(oldest);
                    pixels -= (long) removed.getWidth() * removed.getHeight();
                }
            }
        } catch (Exception ignored) { synchronized (this) { fail(link, 300); } }
        finally { synchronized (this) { pending.remove(link); } }
    }

    private void fail(String link, int seconds) {
        failed.remove(link); failed.put(link, System.nanoTime() + seconds * 1_000_000_000L);
        while (failed.size() > 64) failed.remove(failed.keySet().iterator().next());
    }

    public synchronized int pendingCount() { return pending.size(); }
    public synchronized long pixelCount() { return pixels; }
    @Override public synchronized void close() { workers.shutdownNow(); ready.clear(); failed.clear(); pixels = 0; }
}
