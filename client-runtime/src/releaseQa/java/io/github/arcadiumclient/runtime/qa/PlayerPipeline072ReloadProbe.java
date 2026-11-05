// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class PlayerPipeline072ReloadProbe {
    private static final boolean ENABLED = Boolean.getBoolean("arcadium.playerPipeline072.reloadProbe");
    private static final String PREFIX = "io.github.arcadiumclient.runtime.client.optimizations.";
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static final List<Map<String, Object>> failures = new ArrayList<>();
    private static boolean warming, attempted, reloaded, verified, finalized;
    private static String failure;
    private static Object owner, world;
    private static Object[] oldGeometry;
    private static Map<String, Long> warmCounters, beforeCounters;
    private static int postFrames;
    private static long nativeFlushes, actualDraws, actualInstances;

    private PlayerPipeline072ReloadProbe() { }

    public static void warmup() {
        if (!ENABLED || attempted || warming) return;
        try {
            if ((int) fixture("phase") != 2) return;
            warmCounters = counters(); warming = true;
            evidence.put("warmupCounters", warmCounters);
        } catch (Throwable error) { fail("Could not observe the native warmup", error); }
    }

    public static void reload() {
        if (!ENABLED || attempted) return;
        attempted = true;
        MatrixState matrices = null;
        try {
            require(System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual"), "The reload probe is correctness-only");
            require(warming && (int) fixture("phase") == 4 && (int) fixture("visualStage") == 0, "Reload must follow the initial visual warmup");
            beforeCounters = counters(); Map<String, Long> warmDelta = difference(warmCounters, beforeCounters);
            require(body(warmDelta) > 0, "Warmup did not submit compact/native GPU player parts");
            require(warmDelta.get("dispatches") > 0 && warmDelta.get("offloadedParts") > 0, "Warmup did not execute GPU model transforms");
            owner = backend();
            require(owner != null && owner.getClass().getName().equals("dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer"), "A tracked native model backend is required");
            require(!(boolean) owner.getClass().getMethod("isBatchActive").invoke(owner), "Reload was requested inside an active native batch");
            oldGeometry = boxes(owner).clone();
            require(oldGeometry.length == 2 && oldGeometry[0] != null && oldGeometry[1] != null, "Both native box geometries must exist before reload");
            Minecraft mc = Minecraft.getInstance(); world = mc.world;
            double[] camera = camera(mc);
            matrices = new MatrixState();
            Long invalidations = optionalCounter("GpuPlayerPipeline", "backendInvalidations");
            evidence.put("warmupPositiveGpuDeltas", warmDelta);
            evidence.put("beforeCounters", beforeCounters);
            evidence.put("beforeBoxGeometryCount", 2);
            evidence.put("backendInvalidationCounterPresent", invalidations != null);
            evidence.put("backendInvalidationsBefore", invalidations);
            evidence.put("attempted", true); write("before-reload", false);
            Method reload = owner.getClass().getMethod("reload", Class.forName("org.embeddedt.embeddium.impl.gl.device.CommandList"));
            RenderDevice.enterManagedCode();
            try (var commands = RenderDevice.INSTANCE.createCommandList()) { reload.invoke(owner, commands); }
            finally { RenderDevice.exitManagedCode(); }
            reloaded = true;
            require(mc.world == world && Arrays.equals(camera, camera(mc)), "Native reload changed the scene or camera");
            require(matrices.unchanged(), "Native reload changed the current matrices");
            Object[] empty = boxes(owner);
            require(empty[0] == null && empty[1] == null, "Native reload did not dispose its box geometries");
            Long after = optionalCounter("GpuPlayerPipeline", "backendInvalidations");
            evidence.put("backendInvalidationsImmediatelyAfter", after);
            evidence.put("nativeBoxSlotsCleared", true);
            evidence.put("matrixStatePreserved", true);
            evidence.put("worldAndCameraPreserved", true);
            if (!Boolean.getBoolean("arcadium.playerPipeline072.baseline"))
                require(invalidations != null && after != null && after > invalidations, "The optimized backend invalidation hook did not run");
            write("native-reload-completed", false);
        } catch (Throwable error) { fail("Native model reload failed its immediate checks", error); }
        finally { if (matrices != null) matrices.restoreIfChanged(); }
    }

    public static void flushed(Object backend, Object stats) {
        if (!ENABLED || !reloaded || verified || failure != null || backend != owner || stats == null) return;
        try {
            nativeFlushes++;
            long draws = ((Number) stats.getClass().getMethod("draws").invoke(stats)).longValue();
            long instances = ((Number) stats.getClass().getMethod("instances").invoke(stats)).longValue();
            if (draws > 0 && instances > 0) { actualDraws += draws; actualInstances += instances; }
        } catch (Throwable error) { fail("Could not verify native post-reload draws", error); }
    }

    public static void nativeFailure(Object backend, Throwable error) {
        if (!ENABLED || !reloaded || backend != owner) return;
        fail("The native model backend logged a post-reload failure", error);
    }

    public static void frame() {
        if (!ENABLED || !reloaded || verified || failure != null) return;
        try {
            if (++postFrames < 8) return;
            require(Minecraft.getInstance().world == world, "The world changed during direct-reload verification");
            require(backend() == owner, "The test replaced its native backend instead of reusing the same owner");
            Object[] rebuilt = boxes(owner);
            require(rebuilt[0] != null && rebuilt[1] != null && rebuilt[0] != oldGeometry[0] && rebuilt[1] != oldGeometry[1],
                    "Native box geometry was not recreated after reload");
            Map<String, Long> after = counters(), delta = difference(beforeCounters, after);
            require(body(delta) > 0, "Post-reload compact/native body counters stalled");
            require(delta.get("dispatches") > 0 && delta.get("offloadedParts") > 0, "Post-reload GPU transforms did not execute");
            require(actualDraws > 0 && actualInstances > 0, "Queued post-reload parts never reached native GPU draws");
            evidence.put("sameNativeBackendOwner", true);
            evidence.put("newBoxGeometryCount", 2);
            evidence.put("oldBoxGeometryReferencesReused", false);
            evidence.put("postReloadPositiveGpuDeltas", delta);
            evidence.put("afterCounters", after);
            evidence.put("nativeFlushCalls", nativeFlushes);
            evidence.put("actualNativeDraws", actualDraws);
            evidence.put("actualNativeInstances", actualInstances);
            evidence.put("postReloadFrames", postFrames);
            verified = true; write("post-reload-gpu-verified", true);
        } catch (Throwable error) { fail("Post-reload native GPU geometry did not recover", error); }
    }

    public static Throwable finish(Throwable original) {
        if (!ENABLED || finalized) return original;
        finalized = true;
        if (original != null && failure == null) failure = "The enclosing native fixture failed";
        if (original == null && failure == null && !verified) failure = "The direct native reload probe did not finish its GPU checks";
        write(failure == null ? "complete" : "failed", original == null && failure == null && verified);
        return original != null ? original : failure == null ? null : new IllegalStateException(failure);
    }

    private static Object backend() throws Exception {
        Object candidate = optionalStatic("GpuPlayerPipeline", "lastBackend");
        if (candidate == null) candidate = optionalStatic("NativePlayerPosePipeline", "backend");
        if (candidate == null) candidate = optionalStatic("GpuEquipmentPipeline", "backend");
        return candidate;
    }
    private static Object[] boxes(Object backend) throws Exception {
        Field field = backend.getClass().getDeclaredField("boxGeometries"); field.setAccessible(true); return (Object[]) field.get(backend);
    }
    private static Object fixture(String name) throws Exception {
        Field field = PlayerPipeline072Qa.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static Object optionalStatic(String type, String name) throws Exception {
        try { Field field = Class.forName(PREFIX + type).getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        catch (ClassNotFoundException | NoSuchFieldException absent) { return null; }
    }
    private static Long optionalCounter(String type, String name) throws Exception {
        Object value = optionalStatic(type, name); return value instanceof Number number ? number.longValue() : null;
    }
    private static long counter(String type, String name) throws Exception { Long value = optionalCounter(type, name); return value == null ? 0 : value; }
    private static Map<String, Long> counters() throws Exception {
        return Map.of("compactPlayers", counter("GpuPlayerPipeline", "playersDrawn"), "nativeBodyParts", counter("NativePlayerPosePipeline", "bodyPartsQueued"),
                "dispatches", counter("GpuModelTransforms", "dispatches"), "offloadedParts", counter("GpuModelTransforms", "offloadedParts"));
    }
    private static Map<String, Long> difference(Map<String, Long> first, Map<String, Long> last) {
        Map<String, Long> delta = new LinkedHashMap<>(); first.forEach((key, value) -> delta.put(key, last.get(key) - value)); return delta;
    }
    private static long body(Map<String, Long> counters) { return counters.get("compactPlayers") + counters.get("nativeBodyParts"); }
    private static double[] camera(Minecraft mc) { return new double[]{mc.player.x, mc.player.y, mc.player.z, mc.player.yaw, mc.player.pitch}; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void fail(String reason, Throwable error) {
        if (failure == null) failure = reason;
        while (error.getCause() != null) error = error.getCause();
        List<String> stack = new ArrayList<>();
        for (StackTraceElement element : error.getStackTrace()) {
            if (stack.size() == 10) break;
            stack.add(element.getClassName() + "." + element.getMethodName());
        }
        failures.add(Map.of("exceptionClass", error.getClass().getName(), "methods", stack));
        write("failed", false);
    }
    private static void write(String stage, boolean passed) {
        try {
            evidence.put("schema", "arcadium.model-native-reload.v1"); evidence.put("stage", stage); evidence.put("passed", passed);
            evidence.put("correctnessOnly", true); evidence.put("syntheticOffline", true);
            evidence.put("baseline", Boolean.getBoolean("arcadium.playerPipeline072.baseline"));
            evidence.put("attempted", attempted); evidence.put("actualReloadInvoked", reloaded); evidence.put("postReloadGpuVerified", verified);
            evidence.put("failure", failure); evidence.put("nativeFailures", failures);
            Path output = Path.of(System.getProperty("arcadium.playerPipeline072.output")).toAbsolutePath().resolveSibling("model-reload-probe.json");
            Files.createDirectories(output.getParent()); Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (Exception ignored) { }
    }

    private static final class MatrixState {
        final int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        final FloatBuffer model = matrix(GL11.GL_MODELVIEW_MATRIX), projection = matrix(GL11.GL_PROJECTION_MATRIX);
        private static FloatBuffer matrix(int name) { FloatBuffer values = BufferUtils.createFloatBuffer(16); GL11.glGetFloatv(name, values); return values; }
        boolean unchanged() { return mode == GL11.glGetInteger(GL11.GL_MATRIX_MODE) && model.equals(matrix(GL11.GL_MODELVIEW_MATRIX)) && projection.equals(matrix(GL11.GL_PROJECTION_MATRIX)); }
        void restoreIfChanged() {
            if (unchanged()) return;
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadMatrixf(model);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadMatrixf(projection); GL11.glMatrixMode(mode);
        }
    }
}
