// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import io.github.arcadiumclient.runtime.client.chat.ChatHistoryStore;
import io.github.arcadiumclient.runtime.client.chat.ChatTextMarks;
import io.github.arcadiumclient.runtime.client.chat.ImagePreviewCache;
import io.github.arcadiumclient.runtime.client.chat.PublicImageFetcher;
import io.github.arcadiumclient.runtime.client.chat.WeakIdentityMap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public final class ClientUtilitiesPureTest {
    public static void main(String[] args) throws Exception {
        WeakIdentityMap<String, Long> metadata = new WeakIdentityMap<>();
        String first = new String("identical text"), second = new String("identical text");
        metadata.put(first, 1000L); metadata.put(second, 2000L);
        check(metadata.getOrDefault(first, 0L) == 1000L && metadata.getOrDefault(second, 0L) == 2000L,
                "Equal but distinct chat components retain independent receipt times and connection origins");
        check(metadata.computeIfAbsent(second, unused -> 3000L) == 2000L,
                "Refreshing the same component preserves its own receipt time");
        TogglePress key = new TogglePress();
        check(key.rising(true, true), "First physical press toggles once");
        for (int i = 0; i < 100; i++) check(!key.rising(true, true), "Holding a key never repeats the toggle");
        key.rising(false, true);
        check(!key.rising(true, false), "Typing in an ineligible screen does not toggle");
        check(!key.rising(true, true), "Closing a screen while still holding a key does not create another press");
        key.rising(false, true);
        check(key.rising(true, true), "A later independent press toggles again");
        check("[x12]".equals(ChatTextMarks.counter(12, 5)), "Legacy counter format is retained as a data preference");
        check("2x".equals(ChatTextMarks.counter(2, 9)), "Suffix counter format works");
        String decorated = "" + ChatTextMarks.TIMESTAMP + "12:30 " + ChatTextMarks.END + "hello "
                + ChatTextMarks.COUNTER + "(2)" + ChatTextMarks.END;
        check("12:30 hello (2)".equals(ChatTextMarks.strip(decorated)), "Zero-width color metadata leaves visible text unchanged");
        check("\u2063Arcadium\u2064".equals(ChatTextMarks.strip("\u2063Arcadium\u2064")), "Arcadium's independent animated prefix markers are preserved");
        check("styled§l!§r".equals(ChatTextMarks.withoutColors("§7styled§l!§r")), "RGB segments replace palette colors while retaining bold and reset instructions");

        Path folder = Files.createTempDirectory("arcadium-history-check-");
        ChatHistoryStore history = new ChatHistoryStore(folder);
        String server = "../../private-world";
        List<ChatHistoryStore.Entry> entries = List.of(new ChatHistoryStore.Entry("{\"text\":\"こんにちは\"}", 12345),
                new ChatHistoryStore.Entry("{\"text\":\"hello\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/help\"}}", 12000));
        history.save(server, entries);
        check(history.path(server).getParent().equals(folder.toAbsolutePath()), "Server names never become filesystem paths");
        check(history.load(server).equals(entries), "UTF-8 components, click metadata and reception times round-trip exactly");
        check(history.load("another-server").isEmpty(), "Servers have isolated histories");
        Files.write(history.path("broken"), new byte[]{1, 2, 3});
        fails(() -> history.load("broken"), "Truncated history is rejected");
        check(Files.size(history.path("broken")) == 3, "Reading a corrupt history never rewrites the file");

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        BufferedImage fixture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        fixture.setRGB(0, 0, 0xFF3AA0FF);
        ImageIO.write(fixture, "png", encoded);
        byte[] png = encoded.toByteArray();
        AtomicInteger requests = new AtomicInteger();
        PublicImageFetcher fetcher = new PublicImageFetcher(host -> new InetAddress[]{InetAddress.getByName("1.1.1.1")}, (uri, address) -> {
            requests.incrementAndGet();
            check(address.getHostAddress().equals("1.1.1.1"), "The transport receives the exact validated address");
            return new PublicImageFetcher.Reply(200, Map.of("content-type", "image/png"), png);
        });
        check(fetcher.fetch("https://images.example/test.png").getRGB(0, 0) == 0xFF3AA0FF, "Validated image transport decodes the original pixels");
        for (String url : List.of("file:///private.png", "ftp://images.example/a.png", "https://user:secret@images.example/a.png", "http://images.example:0/a.png"))
            fails(() -> fetcher.fetch(url), "Unsupported schemes, credentials and invalid ports are rejected");
        check(requests.get() == 1, "Rejected image URLs never reach the transport");
        for (String ip : List.of("127.0.0.1", "0.0.0.0", "10.2.3.4", "169.254.169.254", "172.31.2.3", "192.168.0.1", "100.64.0.1", "::1", "fc00::1", "fe80::1"))
            check(!PublicImageFetcher.publicAddress(InetAddress.getByName(ip)), "Private and special-use targets are denied: " + ip);
        PublicImageFetcher redirect = new PublicImageFetcher(host -> new InetAddress[]{InetAddress.getByName(host.equals("safe.example") ? "1.1.1.1" : "127.0.0.1")},
                (uri, address) -> new PublicImageFetcher.Reply(302, Map.of("location", "http://internal.example/secret.png"), new byte[0]));
        fails(() -> redirect.fetch("https://safe.example/p.png"), "Redirect targets are independently validated");
        PublicImageFetcher mixed = new PublicImageFetcher(host -> new InetAddress[]{InetAddress.getByName("1.1.1.1"), InetAddress.getByName("192.168.0.1")},
                (uri, address) -> { throw new AssertionError("Mixed public/private DNS must not reach transport"); });
        fails(() -> mixed.fetch("https://mixed.example/p.png"), "Mixed DNS answers cannot bypass the public-address policy");

        CountDownLatch release = new CountDownLatch(1);
        PublicImageFetcher blocked = new PublicImageFetcher(host -> new InetAddress[]{InetAddress.getByName("1.1.1.1")}, (uri, address) -> {
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            return new PublicImageFetcher.Reply(200, Map.of("content-type", "image/png"), png);
        });
        try (ImagePreviewCache cache = new ImagePreviewCache(blocked)) {
            for (int i = 0; i < 500; i++) cache.request("https://images.example/" + i + ".png");
            check(cache.pendingCount() <= 10, "Hovering many links cannot create an unbounded work queue");
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (cache.pendingCount() != 0 && System.nanoTime() < deadline) Thread.sleep(10);
            check(cache.pendingCount() == 0, "Queued image jobs complete and release their pending keys");
            check(cache.pixelCount() <= 8 * 1024 * 1024, "Image caching respects its decoded-pixel budget");
        }
        Files.delete(history.path(server)); Files.delete(history.path("broken")); Files.delete(folder);
        System.out.println("Independent sprint, chat metadata, archive and public-image checks passed");
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
    private static void fails(Throwing action, String message) throws Exception {
        try { action.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
