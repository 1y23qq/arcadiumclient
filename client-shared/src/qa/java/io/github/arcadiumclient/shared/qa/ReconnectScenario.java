// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.qa;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReconnectScenario {
    public interface Client {
        Object screen();
        boolean disconnected(Object screen);
        boolean atParent();
        void connect(int port, boolean rawAddress);
        Object reconnectButton(Object screen);
        Object backButton(Object screen);
        int buttonY(Object button);
        String buttonText(Object button);
        void click(Object screen, Object button) throws Exception;
        Object target();
        String address();
        void reinitialize(Object screen);
        default boolean legacyLoginDisconnect() { return false; }
        default void capture(String name) { }
        void finish(Map<String, Object> report, Throwable failure);
    }
    private static final class DropServer implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        final AtomicInteger attempts = new AtomicInteger();
        DropServer(boolean loginDisconnect) throws Exception {
            Thread thread = new Thread(() -> {
                while (!server.isClosed()) try (Socket socket = server.accept()) {
                    attempts.incrementAndGet(); socket.setSoTimeout(1500);
                    if (loginDisconnect) {
                        InputStream input = socket.getInputStream();
                        input.skipNBytes(varInt(input)); input.skipNBytes(varInt(input));
                        byte[] text = "{\"text\":\"Arcadium reconnect fixture\"}".getBytes(StandardCharsets.UTF_8);
                        ByteArrayOutputStream payload = new ByteArrayOutputStream(); payload.write(0); varInt(payload, text.length); payload.write(text);
                        OutputStream output = socket.getOutputStream(); varInt(output, payload.size()); output.write(payload.toByteArray()); output.flush();
                    } else socket.getInputStream().read();
                }
                catch (Exception closed) { if (!server.isClosed()) throw new IllegalStateException(closed); }
            }, "Arcadium reconnect fixture");
            thread.setDaemon(true); thread.start();
        }
        int port() { return server.getLocalPort(); }
        public void close() throws Exception { server.close(); }
        private static int varInt(InputStream input) throws Exception {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) { int next = input.read(); if (next < 0) throw new EOFException(); value |= (next & 127) << shift; if ((next & 128) == 0) return value; }
            throw new IOException("Invalid fixture frame");
        }
        private static void varInt(OutputStream output, int value) throws Exception {
            while ((value & ~127) != 0) { output.write((value & 127) | 128); value >>>= 7; } output.write(value);
        }
    }
    private final Client client;
    private final DropServer first, second;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Double> delays = new ArrayList<>();
    private final long started = System.nanoTime();
    private long shown, manual, back;
    private Object previous;
    private int phase;
    private boolean resized, done;

    public ReconnectScenario(Client client) throws Exception {
        this.client = client; first = new DropServer(client.legacyLoginDisconnect()); second = new DropServer(client.legacyLoginDisconnect());
        report.put("syntheticLoopbackOnly", true);
    }
    public void tick() {
        if (done) return;
        try {
            long now = System.nanoTime();
            require(now - started < 90_000_000_000L, "Reconnect QA timed out at phase " + phase);
            Object screen = client.screen();
            switch (phase) {
                case 0 -> { client.connect(first.port(), false); phase++; }
                case 1 -> {
                    if (!client.disconnected(screen)) return;
                    verify(screen, first.port()); require(client.buttonText(client.reconnectButton(screen)).equals("Reconnect (3)"), "Initial countdown did not start at 3");
                    previous = screen; shown = now; client.capture("reconnect-countdown"); phase++;
                }
                case 2, 3 -> {
                    if (phase == 2 && !resized && now - shown > 1_100_000_000L) { client.reinitialize(screen); resized = true; }
                    if (!client.disconnected(screen) || screen == previous) return;
                    verify(screen, first.port()); double delay = (now - shown) / 1e9;
                    require(delay >= 2.75 && delay < 6.0, "Retry did not preserve its three-second deadline: " + delay);
                    delays.add(delay); shown = now; previous = screen;
                    if (++phase == 4) {
                        Object button = client.reconnectButton(screen);
                        manual = now; client.click(screen, button); Object target = client.target(); client.click(screen, button);
                        require(client.target() == target, "Double activation created a second connection");
                        report.put("singleFlight", true);
                    }
                }
                case 4 -> {
                    if (!client.disconnected(screen) || screen == previous) return;
                    verify(screen, first.port()); require(now - manual < 2_500_000_000L, "Manual retry waited for the countdown");
                    report.put("manualDelaySeconds", (now - manual) / 1e9);
                    client.click(screen, client.backButton(screen)); back = now; phase++;
                }
                case 5 -> {
                    require(client.atParent(), "Back did not restore the server browser");
                    require(client.target() == null, "Back retained the pending retry destination");
                    if (now - back < 4_200_000_000L) return;
                    require(first.attempts.get() == 4, "Retry continued after Back or a click connected twice: " + first.attempts.get());
                    report.put("backCancelsAutomaticRetry", true); client.connect(second.port(), true); phase++;
                }
                case 6 -> {
                    if (!client.disconnected(screen)) return;
                    verify(screen, second.port()); previous = screen; shown = now; phase++;
                }
                case 7 -> {
                    if (!client.disconnected(screen) || screen == previous) return;
                    verify(screen, second.port()); require(now - shown >= 2_750_000_000L, "Second server retried too early");
                    require(second.attempts.get() == 2, "Second server was not retried");
                    client.capture("reconnect-other-server"); client.click(screen, client.backButton(screen));
                    report.put("differentDestinationAndDirectAddress", true);
                    report.put("resizePreservesDeadline", resized); report.put("automaticRetryDelaysSeconds", delays);
                    report.put("firstServerConnections", first.attempts.get()); report.put("secondServerConnections", second.attempts.get());
                    finish(null);
                }
                default -> throw new IllegalStateException("Unknown reconnect phase");
            }
        } catch (Throwable failure) { finish(failure); }
    }
    private void verify(Object screen, int port) {
        Object retry = client.reconnectButton(screen), backButton = client.backButton(screen);
        require(retry != null && backButton != null, "Native disconnect screen is missing a button");
        require(client.buttonY(retry) >= client.buttonY(backButton) + 20, "Reconnect is not below Back");
        require(client.address().equals("127.0.0.1:" + port), "Retry destination changed");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void finish(Throwable failure) {
        if (done) return;
        done = true; report.put("passed", failure == null); report.put("phase", phase);
        report.put("firstServerConnections", first.attempts.get()); report.put("secondServerConnections", second.attempts.get());
        report.put("finalScreen", client.screen() == null ? "none" : client.screen().getClass().getName());
        if (failure != null) report.put("failure", failure.toString());
        try { first.close(); second.close(); } catch (Exception closeFailure) { if (failure != null) failure.addSuppressed(closeFailure); }
        client.finish(report, failure);
    }
}
