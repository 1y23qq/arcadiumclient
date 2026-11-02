// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Property;

public final class LivePaired066Qa {
    private static final boolean ENABLED = Boolean.getBoolean("arcadium.hypixelLab.paired");
    private static final int BLOCK = 64, DISCARD = 8, INCLUDED = BLOCK - DISCARD, MAX_PAIRS = 8192;
    private static final long DURATION = 60_000_000_000L;
    private static final String[] FLAGS = {"compactNameTagPreparation", "reduceRenderHookAllocations", "efficientMotionBlur"};
    private static final String PREFIX = "io.github.arcadiumclient.runtime.client.optimizations.";
    private static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static Field[] flags;
    private static Field renderHooks;
    private static List<Counter> counters;
    private static Schedule schedule;
    private static Mode[] modes;
    private static Block[] blocks;
    private static boolean[] orders;
    private static long[][] pairNs;
    private static double[][] pairPlayers;
    private static Path output, resultOutput;
    private static Object world;
    private static boolean armed, collecting, pending, finished, applied, appliedMode, negativeControl;
    private static long started, lastTail, stopped, seed;
    private static String failure;
    private static int treatmentFieldsPresent;
    private static Map<String, Object> settingsBefore, countersBefore, environment;
    private static double[] firstView;

    private LivePaired066Qa() { }
    public static boolean handles(String stage) { return ENABLED && "paired-live".equals(stage); }
    public static boolean running() { return armed || collecting || pending; }

    public static void arm(Path labOutput, boolean bedwars, boolean moving, JsonObject control) {
        if (!ENABLED) return;
        if (running()) cancel("Controller restarted an incomplete run");
        output = labOutput.toAbsolutePath();
        resultOutput = output.resolveSibling("paired-live-result.json");
        failure = null; armed = collecting = pending = finished = applied = false;
        flags = null; renderHooks = null; counters = null; schedule = null; modes = null; blocks = null;
        settingsBefore = countersBefore = environment = null; firstView = null; world = null; treatmentFieldsPresent = 0;
        started = lastTail = stopped = 0;
        try {
            Minecraft mc = Minecraft.getInstance();
            require(!moving, "Ordinary movement must finish before the paired run");
            require(validScene(mc, bedwars), "Start in the live Hypixel Bedwars world with the GUI closed");
            requireLeanMixins();
            Class<?> config = BetaOptimizationsConfig.class;
            for (String name : List.of("gpuModelTransforms", "gpuPlayerPipeline", "gpuEquipmentPipeline")) {
                require(requiredField(config, name).getBoolean(null), "Base GPU setting must remain ON: " + name);
            }
            require(requiredField(config, "minimumPlayers").getInt(null) == 1, "Minimum Visible Players must be1 in both modes");
            flags = new Field[FLAGS.length];
            int present = 0;
            for (int i = 0; i < FLAGS.length; i++) { flags[i] = optionalField(config, FLAGS[i]); if (flags[i] != null) present++; }
            treatmentFieldsPresent = present;
            require(present == 0 || present == FLAGS.length, "A partial treatment-field set is not a valid A/A or A/B cohort");
            negativeControl = present == 0;
            renderHooks = null;
            try { renderHooks = optionalField(Class.forName(PREFIX + "RenderHookAllocationControl"), "enabled"); }
            catch (ClassNotFoundException ignored) { }
            require(negativeControl || renderHooks != null, "The render-hook control is missing from the treatment build");
            if (negativeControl) renderHooks = null;
            counters = bindCounters();
            seed = control.has("pairedSeed") ? control.get("pairedSeed").getAsLong() : System.nanoTime();
            if (seed == 0) seed = 0x0665EEDL;
            schedule = new Schedule(seed, DURATION);
            modes = new Mode[]{new Mode(), new Mode()};
            blocks = new Block[]{new Block(), new Block()};
            orders = new boolean[MAX_PAIRS]; pairNs = new long[2][MAX_PAIRS]; pairPlayers = new double[2][MAX_PAIRS];
            world = mc.world;
            firstView = view(mc);
            settingsBefore = settings(); countersBefore = counterValues(); environment = environment(mc);
            armed = true;
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("stage", "paired-live"); status.put("pairedState", "armed");
            status.put("negativeControl", negativeControl); status.put("requestedSeconds", 60);
            status.put("blockFrames", BLOCK); status.put("discardFramesPerBlock", DISCARD);
            status.put("resultFile", resultOutput.toString());
            writeAtomic(output, status);
        } catch (Throwable error) {
            failure = error.toString(); pending = true;
            finalizeRun();
        }
    }

    public static void head(boolean bedwars) {
        if (!ENABLED || finished) return;
        if (pending) { finalizeRun(); return; }
        if (!armed && !collecting) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.world != world) { fail("World changed during the paired run"); finalizeRun(); return; }
            if (!validScene(mc, bedwars)) { fail("Left Bedwars, disconnected, or opened a GUI"); finalizeRun(); return; }
            if (armed) {
                started = System.nanoTime(); lastTail = 0; armed = false; collecting = true;
                orders[0] = schedule.firstOn;
            }
            boolean on = schedule.mode();
            if (!applied || appliedMode != on) apply(on);
        } catch (Throwable error) { fail(error.toString()); finalizeRun(); }
    }

    public static void tail(boolean bedwars) {
        if (!collecting) return;
        long now = System.nanoTime();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.world != world) { fail("World changed during the paired run"); return; }
            if (!validScene(mc, bedwars)) { fail("Left Bedwars, disconnected, or opened a GUI"); return; }
            long elapsed = lastTail == 0 ? 0 : now - lastTail;
            lastTail = now;
            if (schedule.included()) {
                require(elapsed > 0, "Nonpositive frame interval");
                blocks[schedule.mode() ? 1 : 0].add(elapsed, mc);
            }
            int boundary = schedule.endFrame(now - started);
            if (boundary >= 2) {
                int pair = schedule.pairs - 1;
                require(pair < MAX_PAIRS, "Paired sampler capacity exceeded");
                for (int mode = 0; mode < 2; mode++) {
                    Block block = blocks[mode];
                    require(block.count == INCLUDED, "Incomplete pair block");
                    modes[mode].commit(block);
                    pairNs[mode][pair] = block.ns;
                    pairPlayers[mode][pair] = block.players / (double) INCLUDED;
                    block.clear();
                }
                if (boundary == 3) { stopped = now; collecting = false; pending = true; }
                else orders[schedule.pairs] = schedule.firstOn;
            }
        } catch (Throwable error) { fail(error.toString()); }
    }

    public static void sceneTick(boolean bedwars) {
        if (collecting && !bedwars) fail("Left the Bedwars sidebar scene");
    }

    public static void cancel(String reason) {
        if (!running()) return;
        fail(reason);
        finalizeRun();
    }

    private static void fail(String reason) {
        if (failure == null) failure = reason;
        if (stopped == 0) stopped = System.nanoTime();
        armed = collecting = false; pending = true;
    }

    private static void apply(boolean on) throws Exception {
        if (flags != null) for (Field flag : flags) if (flag != null) flag.setBoolean(null, on);
        if (renderHooks != null) renderHooks.setBoolean(null, on);
        appliedMode = on; applied = true;
    }

    private static boolean validScene(Minecraft mc, boolean bedwars) {
        return bedwars && mc.world != null && mc.player != null && mc.screen == null && mc.getCurrentServerEntry() != null
            && "mc.hypixel.net".equals(mc.getCurrentServerEntry().ip);
    }

    private static void requireLeanMixins() throws Exception {
        try (var stream = LivePaired066Qa.class.getClassLoader().getResourceAsStream("arcadium-release-qa.mixins.json")) {
            require(stream != null, "QA mixin metadata missing");
            JsonObject metadata = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var client = metadata.getAsJsonArray("client");
            require(client.size() == 2 && client.get(0).getAsString().equals("HypixelLabMixin")
                && client.get(1).getAsString().equals("HypixelLabKeyProbe"), "Use only the two lean Hypixel Lab mixins");
        }
    }

    private static Field requiredField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true);
        require(Modifier.isStatic(field.getModifiers()), "Expected a static benchmark field: " + name);
        return field;
    }
    private static Field optionalField(Class<?> owner, String name) throws Exception {
        try { return requiredField(owner, name); } catch (NoSuchFieldException missing) { return null; }
    }

    private static List<Counter> bindCounters() throws Exception {
        List<Counter> bound = new ArrayList<>();
        for (String[] group : new String[][]{
            {"GpuModelTransforms", "offloadedParts", "dispatches", "sortedBatches", "cpuRecoveries", "fallbackParts"},
            {"GpuPlayerPipeline", "playersDrawn", "partsQueued", "rejectedState", "rejectedModel", "rejectedLayers"},
            {"GpuEquipmentPipeline", "statesQueued", "partsQueued", "glintStates", "leatherStates", "fallbacks"},
            {"EfficientMotionBlur", "swappedFrames", "skippedCopies", "nativeFrames"},
            {"RenderHookAllocationControl", "fusedSites", "particleSites", "layerSites"}}) {
            try {
                Class<?> type = Class.forName(PREFIX + group[0]);
                for (int i = 1; i < group.length; i++) {
                    Field field = optionalField(type, group[i]);
                    if (field != null) bound.add(new Counter(group[0] + "." + group[i], field));
                }
            } catch (ClassNotFoundException ignored) { }
        }
        return bound;
    }
    private record Counter(String name, Field field) { }
    private static Map<String, Object> counterValues() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        if (counters != null) for (Counter counter : counters) values.put(counter.name, counter.field.get(null));
        return values;
    }

    private static Map<String, Object> settings() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<String, Object> beta = new LinkedHashMap<>();
        for (var entry : BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet()) if (entry.getValue() instanceof Property<?> property) {
            Map<String, Object> setting = new LinkedHashMap<>();
            setting.put("value", property.get()); setting.put("title", Objects.toString(property.getTitle(), entry.getKey()));
            setting.put("category", Objects.toString(property.getMetadata("category"), ""));
            beta.put(entry.getKey(), setting);
        }
        snapshot.put("beta", beta);
        try {
            Config blur = (Config) Class.forName("org.polyfrost.polyblur.client.PolyBlurConfig").getField("INSTANCE").get(null);
            Map<String, Object> values = new LinkedHashMap<>();
            for (var entry : blur.getTree().map.entrySet()) if (entry.getValue() instanceof Property<?> property) values.put(entry.getKey(), property.get());
            snapshot.put("motionBlur", values);
        } catch (ReflectiveOperationException ignored) { }
        Minecraft mc = Minecraft.getInstance();
        snapshot.put("game", Map.of("vsync", mc.options.vsync, "fpsLimit", mc.options.fpsLimit, "guiScale", mc.options.guiScale,
            "renderDistance", mc.options.viewDistance, "fancyGraphics", mc.options.fancyGraphics,
            "limitUnfocusedFps", org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps));
        return snapshot;
    }

    private static Map<String, Object> environment(Minecraft mc) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); values.put("openGl", GL11.glGetString(GL11.GL_VERSION));
        values.put("java", System.getProperty("java.version")); values.put("resolution", List.of(mc.width, mc.height));
        values.put("leanMixins", List.of("HypixelLabMixin", "HypixelLabKeyProbe"));
        values.put("frameClock", "runGame TAIL-to-TAIL System.nanoTime"); values.put("gpuTimingQueries", false);
        return values;
    }
    private static double[] view(Minecraft mc) { return new double[]{mc.player.x, mc.player.y, mc.player.z, mc.player.yaw, mc.player.pitch}; }

    private static void finalizeRun() {
        if (finished) return;
        armed = collecting = pending = false;
        try { apply(true); } catch (Throwable error) { if (failure == null) failure = "Could not restore treatment flags: " + error; }
        finished = true;
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stage", "paired-live"); result.put("pairedState", failure == null ? "complete" : "failed");
            result.put("passed", failure == null); if (failure != null) result.put("failure", failure);
            result.put("negativeControl", negativeControl); result.put("seed", Long.toUnsignedString(seed));
            result.put("requestedSeconds", 60); result.put("elapsedSeconds", started == 0 ? 0 : (stopped - started) / 1e9);
            result.put("blockFrames", BLOCK); result.put("discardFramesPerBlock", DISCARD); result.put("includedFramesPerBlock", INCLUDED);
            int pairs = schedule == null ? 0 : schedule.pairs;
            result.put("completedPairs", pairs); result.put("incompletePairDiscarded", failure != null);
            result.put("environment", environment); result.put("settingsBefore", settingsBefore); result.put("settingsAfterAllOnRestore", settings());
            result.put("countersBefore", countersBefore); result.put("countersAfter", counterValues());
            result.put("treatmentFieldNames", FLAGS); result.put("treatmentFieldsPresent", treatmentFieldsPresent);
            result.put("treatmentPerMode", Map.of("OFF", !negativeControl ? List.of(false, false, false) : List.of("missing", "missing", "missing"),
                "ON", !negativeControl ? List.of(true, true, true) : List.of("missing", "missing", "missing")));
            result.put("restoredAvailableTreatmentFlagsOn", true); result.put("baseGpuSettingsModifiedDuringBlocks", false);
            result.put("viewAtStart", firstView);
            if (Minecraft.getInstance().player != null) result.put("viewAtEnd", view(Minecraft.getInstance()));
            if (modes != null) {
                result.put("OFF", modes[0].summary()); result.put("ON", modes[1].summary());
                result.put("paired", pairedSummary(pairs));
            }
            if (output != null) {
                writeAtomic(resultOutput, result);
                writeAtomic(output, result);
            }
        } catch (Throwable error) { error.printStackTrace(); }
        world = null;
    }

    private static Map<String, Object> pairedSummary(int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();
        double mean = 0, square = 0; int onFirst = 0, positive = 0;
        for (int i = 0; i < count; i++) {
            double offMs = pairNs[0][i] / (INCLUDED * 1e6), onMs = pairNs[1][i] / (INCLUDED * 1e6);
            double delta = offMs - onMs; mean += delta; square += delta * delta;
            if (orders[i]) onFirst++; if (delta > 0) positive++;
            details.add(Map.of("index", i, "first", orders[i] ? "ON" : "OFF", "offMeanMs", offMs, "onMeanMs", onMs,
                "offPlayers", pairPlayers[0][i], "onPlayers", pairPlayers[1][i]));
        }
        if (count > 0) {
            mean /= count;
            double variance = count > 1 ? Math.max(0, (square - count * mean * mean) / (count - 1)) : 0;
            double error = 1.96 * Math.sqrt(variance / count);
            result.put("meanFrameMsSavedByOn", mean); result.put("approx95PercentIntervalMs", List.of(mean - error, mean + error));
            result.put("intervalMethod", "Normal approximation over complete-pair frame-time differences");
            result.put("onFirstPairs", onFirst); result.put("offFirstPairs", count - onFirst); result.put("onFasterPairs", positive);
            double offFps = 1e9 * modes[0].count / modes[0].ns, onFps = 1e9 * modes[1].count / modes[1].ns;
            result.put("aggregateFpsGainPercent", (onFps / offFps - 1) * 100);
        }
        result.put("pairs", details);
        return result;
    }

    private static void writeAtomic(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(value));
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    static final class Schedule {
        long random, target;
        int frame, half, pairs;
        boolean firstOn;
        Schedule(long seed, long target) { random = seed == 0 ? 1 : seed; this.target = target; nextOrder(); }
        boolean mode() { return half == 0 ? firstOn : !firstOn; }
        boolean included() { return frame >= DISCARD; }
        int endFrame(long elapsed) {
            if (++frame < BLOCK) return 0;
            frame = 0;
            if (half == 0) { half = 1; return 1; }
            half = 0; pairs++;
            if (elapsed >= target) return 3;
            nextOrder(); return 2;
        }
        private void nextOrder() { random ^= random << 13; random ^= random >>> 7; random ^= random << 17; firstOn = (random & 1) != 0; }
    }

    private static class View {
        final double[] sum = new double[5], min = new double[5], max = new double[5];
        View() { reset(); }
        void reset() { Arrays.fill(sum, 0); Arrays.fill(min, Double.POSITIVE_INFINITY); Arrays.fill(max, Double.NEGATIVE_INFINITY); }
        void add(int axis, double value) { sum[axis] += value; min[axis] = Math.min(min[axis], value); max[axis] = Math.max(max[axis], value); }
        void merge(View other) { for (int i = 0; i < 5; i++) { sum[i] += other.sum[i]; min[i] = Math.min(min[i], other.min[i]); max[i] = Math.max(max[i], other.max[i]); } }
        Map<String, Object> summary(int count) {
            double[] mean = sum.clone(); for (int i = 0; i < 5; i++) mean[i] /= Math.max(1, count);
            return Map.of("order", List.of("x", "y", "z", "yaw", "pitch"), "mean", mean, "min", min.clone(), "max", max.clone());
        }
    }
    private static final class Block {
        final long[] frames = new long[INCLUDED];
        final View view = new View();
        int count, minPlayers = Integer.MAX_VALUE, maxPlayers;
        long ns, players;
        void add(long nanos, Minecraft mc) {
            require(count < INCLUDED, "Block frame capacity exceeded");
            frames[count++] = nanos; ns += nanos;
            int size = mc.world.players.size(); players += size; minPlayers = Math.min(minPlayers, size); maxPlayers = Math.max(maxPlayers, size);
            view.add(0, mc.player.x); view.add(1, mc.player.y); view.add(2, mc.player.z); view.add(3, mc.player.yaw); view.add(4, mc.player.pitch);
        }
        void clear() { count = 0; ns = players = 0; minPlayers = Integer.MAX_VALUE; maxPlayers = 0; view.reset(); }
    }
    private static final class Mode {
        final long[] frames = new long[MAX_PAIRS * INCLUDED];
        final View view = new View();
        int count, minPlayers = Integer.MAX_VALUE, maxPlayers;
        long ns, players;
        void commit(Block block) {
            require(count + INCLUDED <= frames.length, "Mode frame capacity exceeded");
            System.arraycopy(block.frames, 0, frames, count, INCLUDED); count += INCLUDED; ns += block.ns; players += block.players;
            minPlayers = Math.min(minPlayers, block.minPlayers); maxPlayers = Math.max(maxPlayers, block.maxPlayers); view.merge(block.view);
        }
        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>(); result.put("frames", count);
            if (count == 0) return result;
            long[] raw = Arrays.copyOf(frames, count), ordered = raw.clone(); Arrays.sort(ordered);
            int slow = Math.max(1, (count + 99) / 100); long slowNs = 0;
            for (int i = count - slow; i < count; i++) slowNs += ordered[i];
            result.put("meanFrameMs", ns / (count * 1e6)); result.put("fps", 1e9 * count / ns);
            result.put("onePercentLowFps", 1e9 * slow / slowNs); result.put("onePercentLowFrames", slow);
            result.put("onePercentLowDefinition", "FPS from mean duration of slowest ceil(1% of included frames)");
            result.put("p99FrameMs", ordered[Math.min(count - 1, (int) Math.ceil(count * .99) - 1)] / 1e6);
            result.put("includedFrameSeconds", ns / 1e9); result.put("worldPlayersMean", players / (double) count);
            result.put("worldPlayersMin", minPlayers); result.put("worldPlayersMax", maxPlayers); result.put("view", view.summary(count));
            result.put("frameTimesNs", raw);
            return result;
        }
    }

    public static void main(String[] args) {
        require(args.length == 1 && args[0].equals("--self-test"), "Use --self-test");
        Schedule a = new Schedule(0x665EEDL, 1_000_000_000L), b = new Schedule(0x665EEDL, Long.MAX_VALUE);
        int[] included = new int[2]; int boundary = 0;
        for (int i = 0; i < 128; i++) {
            require(a.mode() == b.mode(), "Seeded order differs");
            if (a.included()) included[a.mode() ? 1 : 0]++;
            boundary = a.endFrame((i + 1) * 10_000_000L); b.endFrame((i + 1) * 10_000_000L);
            require(i == 127 || boundary != 3, "Stopped before completing the pair");
        }
        require(boundary == 3 && a.pairs == 1 && included[0] == 56 && included[1] == 56, "Unbalanced completed pair");
        Schedule random = new Schedule(91, Long.MAX_VALUE); int firstOn = 0;
        for (int pair = 0; pair < 100; pair++) {
            if (random.firstOn) firstOn++;
            boolean initial = random.mode();
            for (int i = 0; i < 64; i++) random.endFrame(0);
            require(random.mode() != initial, "Pair did not contain both modes");
            for (int i = 0; i < 64; i++) random.endFrame(0);
        }
        require(firstOn > 0 && firstOn < 100 && random.pairs == 100, "Random order failed");
        System.out.println("LivePaired066Qa schedule self-test PASS: balanced pairs,8 discarded frames,seed reproducibility,complete-pair stop,randomized order");
    }
}
