package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;

public final class AccountChooserQa {
    private static final String BRIDGE = "io.github.arcadiumclient.runtime.client.launcher.NativeAccountBridge";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Long> CAPTURES = new LinkedHashMap<>();
    private static final Map<String, Integer> REQUESTS = new LinkedHashMap<>();
    private static Class<?> bridge;
    private static Object bridgeInstance, listResult, jsonNull;
    private static FixtureWriter writer;
    private static int ticks, phase, phaseTicks;
    private static long pointer;
    private static boolean done;
    private static String capture;

    private AccountChooserQa() {}
    public static boolean enabled() { return Boolean.getBoolean("arcadium.featureTest.accountChooser"); }
    public static long pointer() { return pointer; }

    public static void tick() {
        if (!enabled() || done) return;
        try {
            var mc = Minecraft.getInstance();
            if (++ticks == 1) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Chooser QA requires the isolated offline session");
                Path storage = Path.of(System.getProperty("arcadium.launcherDataDir")).toAbsolutePath().normalize();
                require(storage.startsWith(mc.gameDir.toPath().toAbsolutePath().normalize()), "Chooser QA account storage must stay in the game fixture");
                mc.options.pauseOnUnfocus = false;
                mc.options.guiScale = 2;
                bridge = Class.forName(BRIDGE);
                bridgeInstance = bridge.getField("INSTANCE").get(null);
                bridge.getMethod("addOffline", String.class).invoke(bridgeInstance, "BlockTester");
                bridge.getMethod("addOffline", String.class).invoke(bridgeInstance, "Claude");
                Method request = null;
                for (Method method : bridge.getDeclaredMethods()) if (method.getName().equals("request")) request = method;
                require(request != null, "Native account request method was not found");
                request.setAccessible(true);
                listResult = ((CompletableFuture<?>) request.invoke(bridgeInstance, "list", Map.of(), null)).get(10, TimeUnit.SECONDS);
                bridge.getMethod("close").invoke(bridgeInstance);
                System.setProperty("arcadium.launcherExecutable", mc.gameDir.toPath().resolve("missing-fixture-launcher.exe").toString());
                jsonNull = Class.forName("kotlinx.serialization.json.JsonNull").getField("INSTANCE").get(null);
                mc.openScreen((Screen) Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu").getConstructor().newInstance());
                RESULT.put("version", "0.2.8");
                RESULT.put("syntheticAccountIpcFixture", true);
                RESULT.put("providerLoginStarted", false);
            }
            require(++phaseTicks < 400, "Account chooser phase timed out: " + phase);
            if (capture != null || !CAPTURES.isEmpty()) return;
            switch (phase) {
                case 0 -> { if (ticks >= 60) { installFixture(); click(mc, 40, 25); advance(); } }
                case 1 -> { if (phaseTicks >= 30) { take("accounts"); advance(); } }
                case 2 -> { click(mc, 64, 78); advance(); }
                case 3 -> { if (phaseTicks >= 15) { take("chooser"); advance(); } }
                case 4 -> { click(mc, 64, 98); advance(); }
                case 5 -> { if (phaseTicks >= 15) { require(count("login") == 0 && count("cookie") == 0, "Back started a provider login"); take("back"); advance(); } }
                case 6 -> { click(mc, 64, 78); advance(); }
                case 7 -> { if (phaseTicks >= 15) { click(mc, 36, 78); advance(); } }
                case 8 -> { if (phaseTicks >= 15 && count("login") == 1) { take("microsoft-pending"); advance(); } }
                case 9 -> { click(mc, 64, 92); advance(); }
                case 10 -> { if (phaseTicks >= 15 && count("cancel") >= 1) { take("microsoft-cancelled"); advance(); } }
                case 11 -> { click(mc, 64, 78); advance(); }
                case 12 -> { if (phaseTicks >= 15) { click(mc, 92, 78); advance(); } }
                case 13 -> { if (phaseTicks >= 15 && count("cookie") == 1) { take("cookie-pending"); advance(); } }
                case 14 -> { click(mc, 64, 92); advance(); }
                case 15 -> { if (phaseTicks >= 15 && count("cancel") >= 2) { take("cookie-cancelled"); advance(); } }
                case 16 -> { click(mc, 64, 78); advance(); }
                case 17 -> { if (phaseTicks >= 15) { writer.failNext = true; click(mc, 36, 78); advance(); } }
                case 18 -> { if (phaseTicks >= 20 && count("login") == 2 && writer.failures == 1) { take("microsoft-error"); advance(); } }
                case 19 -> { click(mc, 64, 110); advance(); }
                case 20 -> { if (phaseTicks >= 15) { take("error-back"); advance(); } }
                case 21 -> { click(mc, 64, 78); advance(); }
                case 22 -> { if (phaseTicks >= 15) { writer.failNext = true; click(mc, 92, 78); advance(); } }
                case 23 -> { if (phaseTicks >= 20 && count("cookie") == 2 && writer.failures == 2) { take("cookie-error"); advance(); } }
                case 24 -> { click(mc, 40, 25); advance(); }
                case 25 -> { if (phaseTicks >= 15) { take("closed"); advance(); } }
                case 26 -> {
                    RESULT.put("passed", true);
                    RESULT.put("requests", new LinkedHashMap<>(REQUESTS));
                    RESULT.put("fixtureFailures", writer.failures);
                    write(mc);
                    done = true;
                    bridge.getMethod("close").invoke(bridgeInstance);
                    System.out.println("Arcadium account chooser QA: completed");
                    if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                }
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void installFixture() throws Exception {
        Class<?> connection = Class.forName(BRIDGE + "$Connection");
        writer = new FixtureWriter();
        var constructor = connection.getDeclaredConstructor(Process.class, BufferedWriter.class);
        constructor.setAccessible(true);
        Object fixture = constructor.newInstance(new FixtureProcess(), new BufferedWriter(writer));
        Field pending = connection.getDeclaredField("pending"); pending.setAccessible(true);
        writer.pending = (Map<Long, CompletableFuture<Object>>) pending.get(fixture);
        Field field = bridge.getDeclaredField("connection"); field.setAccessible(true); field.set(bridgeInstance, fixture);
    }

    private static void click(Minecraft mc, int x, int y) {
        require(mc.screen != null && mc.screen.getClass().getName().contains("ArcadiumMainMenu"), "Actual Arcadium menu did not stay open");
        float px = (x + 0.5f) * mc.width / mc.screen.width;
        float py = (y + 0.5f) * mc.height / mc.screen.height;
        pointer = ((long) Float.floatToRawIntBits(px) << 32) | (Float.floatToRawIntBits(py) & 0xffffffffL);
        ((ScreenProbeAccess) mc.screen).arcadium$click(x, y, 0);
        ((ScreenProbeAccess) mc.screen).arcadium$release(x, y, 0);
    }

    public static void afterFrame() {
        if (!enabled() || done) return;
        try {
            var mc = Minecraft.getInstance();
            if (capture != null) {
                Screenshot.take(mc.gameDir, "account-chooser-qa-" + capture, mc.width, mc.height, mc.getRenderTarget());
                CAPTURES.put(capture, System.currentTimeMillis());
                capture = null;
            }
            for (String name : new ArrayList<>(CAPTURES.keySet())) {
                Path input = mc.gameDir.toPath().resolve("screenshots/account-chooser-qa-" + name);
                if (!Files.isRegularFile(input)) continue;
                byte[] bytes = Files.readAllBytes(input);
                int size = bytes.length;
                if (size >= 12 && bytes[size - 8] == 'I' && bytes[size - 7] == 'E' && bytes[size - 6] == 'N' && bytes[size - 5] == 'D') {
                    Files.createDirectories(output(mc));
                    Files.write(output(mc).resolve(name), bytes);
                    CAPTURES.remove(name);
                } else require(System.currentTimeMillis() - CAPTURES.get(name) < 15000, "Account chooser PNG write timed out");
            }
        } catch (Throwable error) { fail(error); }
    }

    private static int count(String op) { synchronized (REQUESTS) { return REQUESTS.getOrDefault(op, 0); } }
    private static void advance() { phase++; phaseTicks = 0; }
    private static void take(String name) { capture = name + ".png"; }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
    private static Path output(Minecraft mc) { return mc.gameDir.toPath().resolve("account-chooser-qa"); }
    private static void write(Minecraft mc) throws Exception { Files.createDirectories(output(mc)); Files.writeString(output(mc).resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); }
    private static void fail(Throwable error) {
        if (done) return;
        done = true;
        RESULT.put("passed", false); RESULT.put("phase", phase); RESULT.put("failure", error.toString()); RESULT.put("requests", new LinkedHashMap<>(REQUESTS));
        error.printStackTrace();
        try { var mc = Minecraft.getInstance(); write(mc); Screenshot.take(mc.gameDir, "account-chooser-qa-failure.png", mc.width, mc.height, mc.getRenderTarget()); if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop(); }
        catch (Throwable ignored) {}
    }

    private static final class FixtureWriter extends Writer {
        private final StringBuilder buffer = new StringBuilder();
        private Map<Long, CompletableFuture<Object>> pending;
        private volatile boolean failNext;
        private volatile int failures;
        @Override public synchronized void write(char[] chars, int offset, int length) { buffer.append(chars, offset, length); }
        @Override public synchronized void flush() {
            for (String line : buffer.toString().split("\\R")) {
                if (line.isBlank()) continue;
                var message = JsonParser.parseString(line).getAsJsonObject();
                long id = message.get("id").getAsLong();
                String op = message.get("op").getAsString();
                synchronized (REQUESTS) { REQUESTS.merge(op, 1, Integer::sum); }
                var future = pending.get(id);
                if (op.equals("list")) future.complete(listResult);
                else if (op.equals("cancel")) future.complete(jsonNull);
                else if (op.equals("login") || op.equals("cookie")) {
                    if (failNext) { failNext = false; failures++; future.completeExceptionally(new IllegalStateException("Fixture account service unavailable")); }
                } else throw new IllegalStateException("Unexpected fixture account operation: " + op);
            }
            buffer.setLength(0);
        }
        @Override public void close() {}
    }

    private static final class FixtureProcess extends Process {
        private volatile boolean alive = true;
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { alive = false; }
        @Override public boolean isAlive() { return alive; }
    }
}
