// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class LiveRenderer072Qa {
    private static final boolean ENABLED = System.getProperty("arcadium.hypixelLab.output") != null;
    private static final long WARMUP = 25_000_000_000L, DURATION = 30_000_000_000L;
    private static final int IDLE = 0, WARMING = 1, RECORDING = 2, PENDING = 3, FINISHED = 4;
    private static final String PREFIX = "io.github.arcadiumclient.runtime.client.optimizations.";
    private static final String[] REASONS = {"", "Setup validation failed", "The native window lost foreground focus", "A GUI opened",
            "The world or camera entity changed", "The actual view changed", "Framebuffer/window resolution changed", "Render distance changed",
            "Vsync, FPS cap or unfocused-FPS setting changed", "A numeric/boolean setting changed", "The scene left Hypixel Bedwars",
            "The controller changed during capture", "Frame capacity exceeded", "Invalid frame interval", "Sampler validation failed", "Required GPU submissions were not observed"};
    private static final long[] frames = new long[524288];
    private static final String[] COUNTER_CLASSES = {"GpuPlayerPipeline", "NativePlayerPosePipeline", "GpuEquipmentPipeline", "GpuModelTransforms",
            "GpuPlayerPackets", "GpuVoxelSkinPipeline", "GpuResidentPlayerState", "GpuResolvedInstanceCache", "GpuTerrainCommands", "GpuNameTags", "RetainedNameTags",
            "RetainedHudBuffers", "GpuParticles", "FastInvisibleHolograms", "RenderHookAllocationControl", "EntityNameFormatting"};
    public static long worldIdentityChanges, nonNullWorldChanges, sameWorldCalls, worldJoins, worldLeaves;
    private static int state, reason, count, sequence, minimumPlayers, maximumPlayers;
    private static long armedAt, firstPresent, lastPresent, stopped, elapsedNs, playerTotal;
    private static Object world, player, options;
    private static Entity camera;
    private static double x, y, z;
    private static float yaw, pitch;
    private static boolean vanillaGame, vanillaMods, interGame, interMods, requireHealthyGpu;
    private static Binding[] betaBindings = {}, gameBindings = {};
    private static Map<String, Object> betaBefore, gameBefore, countersBefore, presence;
    private static Map<String, Long> transitionsBefore;
    private static Path output, resultOutput;
    private static volatile WatchService watcher;
    private static volatile boolean controlChanged;
    private static String setupException;

    private LiveRenderer072Qa() { }
    public static boolean handles(String stage) { return ENABLED && "renderer-live".equals(stage); }
    public static boolean running() { return state == WARMING || state == RECORDING || state == PENDING; }
    public static boolean changedController() { return controlChanged; }

    public static void worldChange(Object previous, Object incoming) {
        if (!ENABLED) return;
        if (previous == incoming) { sameWorldCalls++; return; }
        worldIdentityChanges++;
        if (previous != null && incoming != null) nonNullWorldChanges++;
        else if (previous == null && incoming != null) worldJoins++;
        else if (previous != null) worldLeaves++;
    }

    public static void arm(Path labOutput, boolean bedwars, boolean moving, JsonObject control) {
        if (!ENABLED) return;
        closeWatcher();
        state = IDLE; reason = 0; count = 0; elapsedNs = playerTotal = 0; firstPresent = lastPresent = stopped = 0;
        controlChanged = false; setupException = null; minimumPlayers = Integer.MAX_VALUE; maximumPlayers = 0;
        requireHealthyGpu = control.has("requireHealthyGpu") && control.get("requireHealthyGpu").getAsBoolean();
        betaBefore = gameBefore = countersBefore = presence = null;
        output = labOutput.toAbsolutePath(); resultOutput = output.resolveSibling("renderer-live-result-" + (++sequence) + ".json");
        try {
            require(!moving && !control.has("move") && !control.has("command"), "Finish ordinary input before arming");
            requireLeanMixins();
            Minecraft mc = Minecraft.getInstance();
            world = mc.world; player = mc.player; camera = mc.getCamera(); options = mc.options;
            require(world != null && player != null && camera != null, "A native scene is required");
            x = camera.x; y = camera.y; z = camera.z; yaw = camera.yaw; pitch = camera.pitch;
            vanillaGame = OneConfigConfig.useVanillaFontInGame; vanillaMods = OneConfigConfig.useVanillaFontForModText;
            interGame = OneConfigConfig.useInterInGame; interMods = OneConfigConfig.useInterForModText;
            betaBefore = betaSnapshot(); gameBefore = numericSnapshot(mc.options, false);
            betaBindings = betaBindings(); gameBindings = bindings(mc.options.getClass(), mc.options, false);
            presence = counterPresence(); transitionsBefore = transitions();
            int invalid = validate(mc, bedwars); require(invalid == 0, REASONS[invalid]);
            if (control.has("settings")) for (var setting : control.getAsJsonObject("settings").entrySet()) {
                Object actual = betaBefore.get(setting.getKey());
                require(actual != null && setting.getValue().isJsonPrimitive(), "Requested validation setting is missing");
                if (actual instanceof Boolean bool) require(setting.getValue().getAsBoolean() == bool, "Requested setting does not match the running game");
                else require(setting.getValue().getAsDouble() == ((Number) actual).doubleValue(), "Requested setting does not match the running game");
            }
            Files.createDirectories(output.getParent());
            startWatcher(); armedAt = System.nanoTime(); state = WARMING; writeStatus("warming");
        } catch (Throwable failure) { setupException = failure.getClass().getName(); reason = 1; state = PENDING; finalizeRun(); }
    }

    public static void head(boolean bedwars) {
        if (state != WARMING && state != RECORDING) return;
        try { int invalid = validate(Minecraft.getInstance(), bedwars); if (invalid != 0) fail(invalid); }
        catch (Throwable ignored) { fail(14); }
    }

    public static void tail(boolean bedwars) {
        if (state != WARMING && state != RECORDING) return;
        head(bedwars);
        if (state != RECORDING) return;
        long now = System.nanoTime();
        if (lastPresent == 0) { firstPresent = lastPresent = now; return; }
        long interval = now - lastPresent; lastPresent = now;
        if (interval <= 0) { fail(13); return; }
        if (count == frames.length) { fail(12); return; }
        frames[count++] = interval; elapsedNs += interval;
        int players = Minecraft.getInstance().world.players.size();
        playerTotal += players; minimumPlayers = Math.min(minimumPlayers, players); maximumPlayers = Math.max(maximumPlayers, players);
        if (now - firstPresent >= DURATION) { stopped = now; state = PENDING; }
    }

    public static void tick(boolean bedwars) {
        if (state == PENDING) { finalizeRun(); return; }
        if (state != WARMING) return;
        head(bedwars);
        if (state == PENDING) { finalizeRun(); return; }
        if (System.nanoTime() - armedAt < WARMUP) return;
        try {
            countersBefore = counters(); betaBefore = betaSnapshot(); gameBefore = numericSnapshot(Minecraft.getInstance().options, false);
            transitionsBefore = transitions();
            writeStatus("recording"); lastPresent = firstPresent = 0; state = RECORDING;
        } catch (Throwable failure) { setupException = failure.getClass().getName(); fail(14); finalizeRun(); }
    }

    private static int validate(Minecraft mc, boolean bedwars) {
        if (controlChanged) return 11;
        if (!Display.isActive()) return 2;
        if (mc.screen != null) return 3;
        if (mc.world != world || mc.player != player || mc.getCamera() != camera) return 4;
        if (Double.doubleToRawLongBits(camera.x) != Double.doubleToRawLongBits(x) || Double.doubleToRawLongBits(camera.y) != Double.doubleToRawLongBits(y)
                || Double.doubleToRawLongBits(camera.z) != Double.doubleToRawLongBits(z) || Float.floatToRawIntBits(camera.yaw) != Float.floatToRawIntBits(yaw)
                || Float.floatToRawIntBits(camera.pitch) != Float.floatToRawIntBits(pitch)) return 5;
        if (mc.width != 1920 || mc.height != 1080 || Display.getWidth() != 1920 || Display.getHeight() != 1080) return 6;
        if (mc.options.viewDistance != 8) return 7;
        if (mc.options.vsync || mc.options.fpsLimit != 260 || OneConfigConfig.limitUnfocusedFps) return 8;
        if (mc.options != options || vanillaGame != OneConfigConfig.useVanillaFontInGame || vanillaMods != OneConfigConfig.useVanillaFontForModText
                || interGame != OneConfigConfig.useInterInGame || interMods != OneConfigConfig.useInterForModText) return 9;
        for (Binding binding : betaBindings) if (!binding.matches()) return 9;
        for (Binding binding : gameBindings) if (!binding.matches()) return 9;
        if (!bedwars || mc.getCurrentServerEntry() == null || !"mc.hypixel.net".equals(mc.getCurrentServerEntry().ip)) return 10;
        return 0;
    }

    private static void fail(int code) { if (reason == 0) reason = code; stopped = System.nanoTime(); state = PENDING; }

    private static void finalizeRun() {
        if (state == FINISHED) return;
        state = FINISHED; closeWatcher();
        try {
            Map<String, Object> betaAfter = betaSnapshot(), gameAfter = numericSnapshot(Minecraft.getInstance().options, false), after = counters();
            if (reason == 0 && (controlChanged || !betaAfter.equals(betaBefore) || !gameAfter.equals(gameBefore))) reason = controlChanged ? 11 : 9;
            if (reason == 0 && count <= 100) reason = 14;
            Map<String, Object> gpuHealth = health(countersBefore, after);
            if (reason == 0 && requireHealthyGpu && !Boolean.TRUE.equals(gpuHealth.get("positiveGpuPath"))) reason = 15;
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("schema", "arcadium.live-renderer-sampler.v1"); report.put("stage", "renderer-live"); report.put("state", reason == 0 ? "complete" : "failed");
            report.put("passed", reason == 0 && count > 100); report.put("profiling", false); report.put("gpuTimingQueries", false); report.put("gpuReadbacks", false);
            report.put("warmupSeconds", 25); report.put("requestedSeconds", 30); report.put("includedFrames", count); report.put("includedSeconds", elapsedNs / 1e9);
            report.put("actualWarmupSeconds", firstPresent == 0 ? 0 : (firstPresent - armedAt) / 1e9);
            report.put("view", Map.of("x", x, "y", y, "z", z, "yaw", yaw, "pitch", pitch));
            report.put("failure", reason == 0 ? null : REASONS[reason]); report.put("setupExceptionClass", setupException);
            report.put("betaBefore", betaBefore); report.put("betaAfter", betaAfter); report.put("gameBefore", gameBefore); report.put("gameAfter", gameAfter);
            report.put("fontSettings", Map.of("vanillaInGame", vanillaGame, "vanillaForMods", vanillaMods, "interInGame", interGame, "interForMods", interMods));
            report.put("unfocusedFpsEnabled", OneConfigConfig.limitUnfocusedFps);
            report.put("counterPresence", presence); report.put("countersBefore", countersBefore); report.put("countersAfter", after);
            report.put("counterDeltas", deltas(countersBefore, after)); report.put("transitionsBefore", transitionsBefore); report.put("transitionsAfter", transitions());
            report.put("frameClock", "Native runGame TAIL-to-TAIL wall intervals after presentation; no GPU execution-time inference");
            report.put("population", Map.of("minimumLoadedPlayers", count == 0 ? 0 : minimumPlayers, "maximumLoadedPlayers", maximumPlayers,
                    "meanLoadedPlayers", count == 0 ? 0 : playerTotal / (double) count));
            report.put("gpuHealthRequired", requireHealthyGpu); report.put("gpuHealth", gpuHealth);
            report.put("measurementWork", "Preallocated primitive samples and primitive field checks only. File watching is on a blocking daemon; snapshots, allocation and sorting occur outside the measured interval.");
            report.put("comparisonLimits", "Separate public-lobby runs differ in actors and scene activity. Report actual view/population/settings and GPU health; do not attribute all FPS differences to one optimization.");
            if (count > 0) report.put("performance", performance(frames, count, elapsedNs));
            Files.createDirectories(output.getParent());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
            Files.writeString(resultOutput, json); Files.writeString(output, json);
        } catch (Throwable failure) {
            try { Files.writeString(output, "{\"schema\":\"arcadium.live-renderer-sampler.v1\",\"state\":\"failed\",\"passed\":false,\"failure\":\"Could not finalize sampler evidence\"}"); }
            catch (Exception ignored) { }
        }
    }

    private static Map<String, Double> performance(long[] samples, int length, long total) {
        long[] sorted = Arrays.copyOf(samples, length); Arrays.sort(sorted); long slow = 0;
        int lowCount = Math.max(1, (length + 99) / 100); for (int i = length - lowCount; i < length; i++) slow += sorted[i];
        return Map.of("fps", length * 1e9 / total, "onePercentLowFps", lowCount * 1e9 / slow,
                "p99FrameMs", sorted[(int) Math.ceil(length * .99) - 1] / 1e6, "maximumFrameMs", sorted[length - 1] / 1e6);
    }

    private static Map<String, Object> health(Map<String, Object> first, Map<String, Object> last) {
        Map<String, Object> values = new LinkedHashMap<>();
        long players = delta(first, last, "GpuPlayerPipeline", "playersDrawn"), nativeBody = delta(first, last, "NativePlayerPosePipeline", "bodyPartsQueued");
        long dispatches = delta(first, last, "GpuModelTransforms", "dispatches"), parts = delta(first, last, "GpuModelTransforms", "offloadedParts");
        values.put("compactPlayersDelta", players); values.put("nativeBodyPartsDelta", nativeBody); values.put("dispatchesDelta", dispatches); values.put("offloadedPartsDelta", parts);
        values.put("positiveGpuPath", players + nativeBody > 0 && dispatches > 0 && parts > 0);
        values.put("assessment", players + nativeBody > 0 && dispatches > 0 && parts > 0 ? "Positive GPU submissions during the capture" : "GPU counters absent or stalled; FPS can describe a native/fallback path rather than the intended GPU route");
        return values;
    }
    private static long delta(Map<String, Object> first, Map<String, Object> last, String type, String field) {
        if (first == null || !(first.get(type) instanceof Map<?, ?> a) || !(last.get(type) instanceof Map<?, ?> b)) return 0;
        return a.get(field) instanceof Number x && b.get(field) instanceof Number y ? y.longValue() - x.longValue() : 0;
    }
    private static Map<String, Object> deltas(Map<String, Object> first, Map<String, Object> last) {
        Map<String, Object> values = new LinkedHashMap<>(); if (first == null) return values;
        for (String type : COUNTER_CLASSES) if (first.get(type) instanceof Map<?, ?> a && last.get(type) instanceof Map<?, ?> b) {
            Map<String, Object> fields = new TreeMap<>();
            for (var entry : a.entrySet()) if (entry.getValue() instanceof Number x && b.get(entry.getKey()) instanceof Number y) {
                if (x instanceof Float || x instanceof Double || y instanceof Float || y instanceof Double) fields.put(entry.getKey().toString(), y.doubleValue() - x.doubleValue());
                else fields.put(entry.getKey().toString(), y.longValue() - x.longValue());
            }
            values.put(type, fields);
        }
        return values;
    }
    private static Map<String, Long> transitions() { return Map.of("worldIdentityChanges", worldIdentityChanges, "nonNullWorldChanges", nonNullWorldChanges,
            "sameWorldCalls", sameWorldCalls, "worldJoins", worldJoins, "worldLeaves", worldLeaves); }
    private static Map<String, Object> counterPresence() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : COUNTER_CLASSES) try { counterType(name); values.put(name, true); } catch (ClassNotFoundException | LinkageError absent) { values.put(name, false); }
        return values;
    }
    private static Class<?> counterType(String name) throws ClassNotFoundException { return Class.forName(PREFIX + name, false, LiveRenderer072Qa.class.getClassLoader()); }
    private static Map<String, Object> counters() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : COUNTER_CLASSES) {
            Map<String, Object> values = new TreeMap<>();
            try { for (Field field : counterType(name).getFields()) if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                    && scalar(field.getType())) values.put(field.getName(), field.get(null)); }
            catch (ClassNotFoundException | LinkageError absent) { values.clear(); values.put("present", false); }
            result.put(name, values);
        }
        return result;
    }
    private static Map<String, Object> betaSnapshot() {
        Map<String, Object> result = new TreeMap<>();
        for (var entry : BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet()) if (entry.getValue() instanceof Property<?> property
                && (property.get() instanceof Boolean || property.get() instanceof Number)) result.put(entry.getKey(), property.get());
        return result;
    }
    private static Map<String, Object> numericSnapshot(Object receiver, boolean statics) throws Exception {
        Map<String, Object> result = new TreeMap<>();
        for (Class<?> type = receiver.getClass(); type != Object.class; type = type.getSuperclass()) for (Field field : type.getDeclaredFields())
            if (settingField(field, statics)) {
                field.setAccessible(true); result.put(field.getName(), field.get(statics ? null : receiver));
            }
        return result;
    }
    private static Binding[] betaBindings() throws Exception {
        List<Binding> values = new ArrayList<>(); Class<?> type = BetaOptimizationsConfig.class;
        for (String name : betaBefore.keySet()) { Field field = type.getDeclaredField(name); require(Modifier.isStatic(field.getModifiers()) && scalar(field.getType()), "Unsupported beta scalar binding"); values.add(new Binding(field, null)); }
        return values.toArray(Binding[]::new);
    }
    private static Binding[] bindings(Class<?> owner, Object receiver, boolean statics) throws Exception {
        List<Binding> values = new ArrayList<>();
        for (Class<?> type = owner; type != Object.class; type = type.getSuperclass()) for (Field field : type.getDeclaredFields())
            if (settingField(field, statics)) values.add(new Binding(field, statics ? null : receiver));
        return values.toArray(Binding[]::new);
    }
    private static boolean settingField(Field field, boolean statics) { return Modifier.isStatic(field.getModifiers()) == statics && !Modifier.isFinal(field.getModifiers())
            && !field.isSynthetic() && field.getName().indexOf('$') < 0 && scalar(field.getType()); }
    private static boolean scalar(Class<?> type) { return type == boolean.class || type == byte.class || type == short.class || type == int.class || type == long.class || type == float.class || type == double.class; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void requireLeanMixins() throws Exception {
        try (var stream = LiveRenderer072Qa.class.getClassLoader().getResourceAsStream("arcadium-release-qa.mixins.json")) {
            require(stream != null, "Missing QA metadata");
            JsonObject metadata = com.google.gson.JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var client = metadata.getAsJsonArray("client");
            require(client.size() == 2 && client.get(0).getAsString().equals("HypixelLabMixin") && client.get(1).getAsString().equals("HypixelLabKeyProbe"), "Use only the two lean Hypixel mixins");
        }
    }
    private static void writeStatus(String value) throws Exception {
        Files.writeString(output, new GsonBuilder().create().toJson(Map.of("schema", "arcadium.live-renderer-sampler.v1", "stage", "renderer-live", "state", value,
                "warmupSeconds", 25, "requestedSeconds", 30, "runSequence", sequence)));
    }
    private static void startWatcher() throws Exception {
        watcher = output.getFileSystem().newWatchService();
        output.getParent().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        WatchService service = watcher; Path name = output.resolveSibling("control.json").getFileName();
        Thread thread = new Thread(() -> {
            try { while (true) { WatchKey key = service.take(); for (var event : key.pollEvents())
                if ((event.kind() == StandardWatchEventKinds.OVERFLOW || name.equals(event.context())) && watcher == service) controlChanged = true;
                if (!key.reset()) { if (watcher == service) controlChanged = true; return; }
            } } catch (java.nio.file.ClosedWatchServiceException ignored) { } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); if (watcher == service) controlChanged = true; }
        }, "Arcadium QA controller watcher");
        thread.setDaemon(true); thread.start();
    }
    private static void closeWatcher() { if (watcher != null) try { watcher.close(); } catch (Exception ignored) { } finally { watcher = null; } }

    private static final class Binding {
        final VarHandle handle;
        final Object receiver;
        final Class<?> type;
        final boolean bool;
        final long integral, bits;
        Binding(Field field, Object receiver) throws Exception {
            field.setAccessible(true); this.receiver = receiver; type = field.getType();
            handle = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup()).unreflectVarHandle(field);
            Object value = field.get(receiver); bool = value instanceof Boolean b && b; integral = value instanceof Number n ? n.longValue() : 0;
            bits = type == float.class ? Float.floatToRawIntBits((Float) value) : type == double.class ? Double.doubleToRawLongBits((Double) value) : 0;
        }
        boolean matches() {
            if (receiver == null) {
                if (type == boolean.class) return (boolean) handle.get() == bool;
                if (type == int.class) return (int) handle.get() == integral;
                if (type == long.class) return (long) handle.get() == integral;
                if (type == byte.class) return (byte) handle.get() == integral;
                if (type == short.class) return (short) handle.get() == integral;
                if (type == float.class) return Float.floatToRawIntBits((float) handle.get()) == bits;
                return Double.doubleToRawLongBits((double) handle.get()) == bits;
            }
            if (type == boolean.class) return (boolean) handle.get(receiver) == bool;
            if (type == int.class) return (int) handle.get(receiver) == integral;
            if (type == long.class) return (long) handle.get(receiver) == integral;
            if (type == byte.class) return (byte) handle.get(receiver) == integral;
            if (type == short.class) return (short) handle.get(receiver) == integral;
            if (type == float.class) return Float.floatToRawIntBits((float) handle.get(receiver)) == bits;
            return Double.doubleToRawLongBits((double) handle.get(receiver)) == bits;
        }
    }

    private static final class BindingCheck {
        static boolean enabled = true;
        static int count = 7;
        static long total = 9007199254740993L;
        float scale = .75f;
        double gamma = .5;
    }
    public static void main(String[] args) throws Exception {
        BindingCheck value = new BindingCheck();
        Binding enabled = new Binding(BindingCheck.class.getDeclaredField("enabled"), null);
        Binding integer = new Binding(BindingCheck.class.getDeclaredField("count"), null);
        Binding large = new Binding(BindingCheck.class.getDeclaredField("total"), null);
        Binding floating = new Binding(BindingCheck.class.getDeclaredField("scale"), value);
        Binding decimal = new Binding(BindingCheck.class.getDeclaredField("gamma"), value);
        require(enabled.matches() && integer.matches() && large.matches() && floating.matches() && decimal.matches(), "Initial primitive bindings differ");
        BindingCheck.enabled = false; BindingCheck.count++; BindingCheck.total++; value.scale = .5f; value.gamma = .75;
        require(!enabled.matches() && !integer.matches() && !large.matches() && !floating.matches() && !decimal.matches(), "Primitive setting drift was missed");
        long[] exact = new long[100]; Arrays.fill(exact, 10_000_000); exact[99] = 100_000_000;
        Map<String, Double> metrics = performance(exact, 100, 1_090_000_000);
        require(Math.abs(metrics.get("fps") - 100e9 / 1_090_000_000) < 1e-10 && metrics.get("onePercentLowFps") == 10
                && metrics.get("p99FrameMs") == 10 && metrics.get("maximumFrameMs") == 100, "Frame statistics differ");
        require(exact[0] == 10_000_000 && exact[99] == 100_000_000, "Statistics mutated captured samples");
        long allocated = -1;
        if (java.lang.management.ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
            Binding[] checks = {new Binding(BindingCheck.class.getDeclaredField("enabled"), null), new Binding(BindingCheck.class.getDeclaredField("count"), null),
                    new Binding(BindingCheck.class.getDeclaredField("total"), null), new Binding(BindingCheck.class.getDeclaredField("scale"), value), new Binding(BindingCheck.class.getDeclaredField("gamma"), value)};
            for (int i = 0; i < 32000; i++) for (Binding check : checks) require(check.matches(), "Warm primitive value differs");
            long thread = Thread.currentThread().threadId(); allocation.getThreadAllocatedBytes(thread);
            long before = allocation.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100000; i++) for (Binding check : checks) if (!check.matches()) throw new AssertionError("Primitive value differs");
            allocated = allocation.getThreadAllocatedBytes(thread) - before;
            require(allocated == 0, "Primitive frame validation allocated objects after warmup");
        }
        System.out.println("Live renderer sampler self-test PASS: primitive settings/drift, exact longs, FPS/1% low/p99, retained raw samples; binding-loop allocated bytes=" + allocated);
    }
}
