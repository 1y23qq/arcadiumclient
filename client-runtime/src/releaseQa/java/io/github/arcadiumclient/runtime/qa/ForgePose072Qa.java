// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline;
import io.github.arcadiumclient.runtime.client.optimizations.NativePlayerPosePipeline;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

public final class ForgePose072Qa {
    private static final String[] MODES = {"observe", "observe", "mutate", "mutate", "cancel-player", "cancel-player", "cancel-living", "cancel-living"};
    private static final String[] COUNTERS = {"playerPre", "livingPre", "livingPost", "playerPost", "canceled", "mutations"};
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, List<int[]>> golden = new LinkedHashMap<>();
    private static final List<int[]> sameFrameNative = new ArrayList<>();
    private static final long[] totals = new long[COUNTERS.length];
    private static Class<?> probe;
    private static int phase, ticks, wait, scenario, measuredFrames, verifiedMatrices;
    private static boolean done, collectGolden, screenshot;
    private static long startBody, startArmor;
    private static float maximumNativeError, maximumGpuError;
    private static String failure;
    private static long captureBegins, playerCaptureBegins, originalPartCalls;
    private static final Map<String, Object> captures = new LinkedHashMap<>(), batches = new LinkedHashMap<>();
    private static Field armorGlint;
    private static Object itemsConfig, originalArmorGlint;
    private static String effectiveArmorGlint;
    private static boolean nativeArmorExpected;
    private static long nativeReferenceParts, glintModelParts;
    private static int glintDepth;

    private ForgePose072Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.forgePose072.output") != null; }
    private static boolean gpu() { return (scenario & 1) != 0; }

    public static void tick() {
        if (done) return;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            require(++ticks < 6000, "Forge pose QA timed out");
            if (failure != null) throw new IllegalStateException(failure);
            wait++;
            if (phase == 0 && wait > 60 && minecraft.screen != null && minecraft.screen.getClass().getSimpleName().contains("MainMenu")) {
                require("ArcadiumTest".equals(minecraft.getSession().getUsername()) && "0".equals(minecraft.getSession().getAccessToken()), "Forge pose QA requires the isolated fake account");
                require(Boolean.getBoolean("arcadium.forge.prepared"), "Forge was not prepared by the actual launch path");
                Class<?> loaderType = Class.forName("net.minecraftforge.fml.common.Loader");
                Object loader = loaderType.getMethod("instance").invoke(null);
                Map<?, ?> mods = (Map<?, ?>)loaderType.getMethod("getIndexedModList").invoke(loader);
                Object container = mods.get("arcadiumrenderprobe");
                require(container != null, "Real Forge probe mod was not loaded");
                Object mod = container.getClass().getMethod("getMod").invoke(container);
                probe = mod.getClass();
                require(probe.getField("registered").getBoolean(null), "FML initialization did not register the real event observer");
                report.put("actualForgePrepared", true); report.put("loadedForgeMod", probe.getName());
                report.put("purpose", "Native Forge callback, cancellation and pose parity; not an FPS benchmark");
                report.put("settings", Map.of("minimumPlayers", 1, "gpuModelTransforms", false, "gpuNameTags", false,
                        "gpuParticles", false, "retainedHudBatches", false, "worldRenderScale", false,
                        "renderDistance", 3, "fpsLimit", 120, "renderDelta", .5f));
                Class<?> animationConfig = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
                Object animation = animationConfig.getMethod("instance").invoke(null);
                itemsConfig = animationConfig.getField("items").get(animation); armorGlint = itemsConfig.getClass().getField("armorGlint");
                originalArmorGlint = armorGlint.get(itemsConfig);
                String override = System.getProperty("arcadium.forgePose072.armorGlint");
                if (override != null) for (Object value : armorGlint.getType().getEnumConstants())
                    if (((Enum<?>)value).name().equals(override)) armorGlint.set(itemsConfig, value);
                effectiveArmorGlint = ((Enum<?>)armorGlint.get(itemsConfig)).name();
                nativeArmorExpected = !effectiveArmorGlint.equals("V1_15") && !effectiveArmorGlint.equals("V1_19_4");
                report.put("effectiveArmorGlint", effectiveArmorGlint); report.put("compactArmorExpected", nativeArmorExpected);
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                Skin3DConfig.INSTANCE.chooseEnabled(false);
                BetaOptimizationsConfig.setGpuModelTransforms(false); BetaOptimizationsConfig.setGpuPlayerPipeline(false); BetaOptimizationsConfig.setGpuEquipmentPipeline(false);
                BetaOptimizationsConfig.setGpuNameTags(false); BetaOptimizationsConfig.setGpuParticles(false);
                BetaOptimizationsConfig.setWorldRenderScale(false); BetaOptimizationsConfig.setRetainedHudBatches(false);
                BetaOptimizationsConfig.setMinimumPlayers(1);
                minecraft.options.pauseOnUnfocus = false; minecraft.options.vsync = false; minecraft.options.fpsLimit = 120;
                minecraft.options.viewDistance = 3; minecraft.options.cloudRenderMode = 0; minecraft.options.viewBobbing = false;
                minecraft.options.perspective = 0; minecraft.options.hideGui = true;
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                minecraft.startGame("arcadium-forge-pose072", "Forge Pose QA", new WorldSettings(72072, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && minecraft.world != null && minecraft.player != null && wait > 90) {
                minecraft.openScreen(null); minecraft.player.abilities.flying = true;
                minecraft.player.updatePositionAndAngles(0, 19, -10, 0, 5);
                minecraft.player.lastYaw = minecraft.player.yaw; minecraft.player.lastPitch = minecraft.player.pitch;
                minecraft.world.setTimeOfDay(6000);
                Frozen target = new Frozen(minecraft, new GameProfile(new UUID(0x7207200000000000L, 1), "ForgePoseProbe"));
                target.updatePositionAndAngles(0, 17, 3, 190, 12);
                target.lastYaw = target.yaw; target.lastPitch = target.pitch;
                target.bodyYaw = target.lastBodyYaw = 190; target.headYaw = target.lastHeadYaw = 205;
                target.walkAnimationProgress = 2.4f; target.walkAnimationSpeed = target.lastWalkAnimationSpeed = .6f;
                target.attackAnimationProgress = target.lastAttackAnimationProgress = .35f;
                target.setEquipment(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
                net.minecraft.item.Item[] armor = {net.minecraft.item.Items.DIAMOND_BOOTS, net.minecraft.item.Items.DIAMOND_LEGGINGS, net.minecraft.item.Items.DIAMOND_CHESTPLATE, net.minecraft.item.Items.DIAMOND_HELMET};
                for (int slot = 1; slot <= 4; slot++) {
                    net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(armor[slot - 1]);
                    stack.addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING, 1); target.setEquipment(slot, stack);
                }
                minecraft.world.forceEntity(72072, target); probe.getField("target").set(null, target);
                report.put("animationReference", AnimationPose072Qa.verify(minecraft));
                report.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
                report.put("resolution", minecraft.width + "x" + minecraft.height);
                phase = 2; scenario = 0; selectScenario();
            }
            if (minecraft.world != null) minecraft.world.setTimeOfDay(6000);
            if (phase == 2 && minecraft.player != null) {
                minecraft.player.yaw = minecraft.player.lastYaw = 0;
                minecraft.player.pitch = minecraft.player.lastPitch = 5;
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void selectScenario() throws ReflectiveOperationException {
        probe.getMethod("reset").invoke(null); probe.getField("mode").set(null, MODES[scenario]);
        BetaOptimizationsConfig.setGpuPlayerPipeline(gpu()); BetaOptimizationsConfig.setGpuEquipmentPipeline(gpu());
        Arrays.fill(totals, 0); measuredFrames = 0; wait = 0;
        startBody = NativePlayerPosePipeline.bodyPartsQueued; startArmor = NativePlayerPosePipeline.armorPartsQueued;
        report.put("currentScenario", name());
    }

    private static String name() { return (gpu() ? "gpu-" : "native-") + MODES[scenario]; }

    public static void beforeWorld() {
        if (done || phase != 2) return;
        try {
            sameFrameNative.clear();
            require(Boolean.TRUE.equals(probe.getMethod("restored").invoke(null)), "Previous native Post callback did not restore its mutation");
            probe.getMethod("reset").invoke(null);
            collectGolden = !gpu() && !MODES[scenario].startsWith("cancel") && measuredFrames == 10;
        } catch (Throwable error) { failure = error.toString(); }
    }

    public static void afterWorld() {
        if (done || phase != 2 || failure != null) return;
        try {
            Object problem = probe.getField("failure").get(null); require(problem == null, "Real Forge observer failed: " + problem);
            require(Boolean.TRUE.equals(probe.getMethod("restored").invoke(null)), "Native model mutation leaked after Post");
            long[] values = new long[COUNTERS.length];
            for (int i = 0; i < values.length; i++) values[i] = probe.getField(COUNTERS[i]).getLong(null);
            if (values[0] == 0) { require(wait < 200, "Target received no actual RenderPlayerEvent.Pre callbacks"); return; }
            String expected;
            if (MODES[scenario].equals("cancel-player")) {
                require(values[1] == 0 && values[2] == 0 && values[3] == 0 && values[4] == values[0], "Canceled player still rendered or posted completion");
                expected = "playerPre";
            } else if (MODES[scenario].equals("cancel-living")) {
                require(values[1] == values[0] && values[2] == 0 && values[3] == values[0] && values[4] == values[0], "Canceled living callback changed dispatch behavior");
                expected = "playerPre,livingPre,playerPost";
            } else {
                require(values[1] == values[0] && values[2] == values[0] && values[3] == values[0] && values[4] == 0, "Native player/living Pre/Post parity failed");
                require(values[5] == (MODES[scenario].equals("mutate") ? values[0] : 0), "Native mutation callback count changed");
                expected = "playerPre,livingPre,livingPost,playerPost";
            }
            String actual = probe.getField("sequence").get(null).toString();
            String repeated = String.join(",", java.util.Collections.nCopies((int)values[0], expected));
            require(actual.equals(repeated), "Actual Forge callback order changed: " + actual);
            for (int i = 0; i < totals.length; i++) totals[i] += values[i];
            measuredFrames++;
            if (measuredFrames == 30) {
                long body = NativePlayerPosePipeline.bodyPartsQueued - startBody, armor = NativePlayerPosePipeline.armorPartsQueued - startArmor;
                Map<String, Object> result = new LinkedHashMap<>(); result.put("passed", false); result.put("frames", measuredFrames);
                result.put("gpuPlayerPipeline", gpu()); result.put("gpuEquipmentPipeline", gpu());
                for (int i = 0; i < totals.length; i++) result.put(COUNTERS[i], totals[i]);
                result.put("compactBodyParts", body); result.put("compactArmorParts", armor);
                report.put(name(), result);
                result.put("effectiveArmorGlint", effectiveArmorGlint); result.put("compactArmorExpected", nativeArmorExpected);
                if (gpu() && !MODES[scenario].startsWith("cancel")) require(body > 0 && (!nativeArmorExpected || armor > 0), "Native callback GPU body/armor path was inactive: " + NativePlayerPosePipeline.status());
                if (MODES[scenario].startsWith("cancel")) require(body == 0 && armor == 0, "Canceled player still queued compact geometry");
                result.put("passed", true); screenshot = true;
                if (++scenario == MODES.length) { phase = 3; return; }
                selectScenario();
            }
        } catch (Throwable error) { failure = error.toString(); }
    }

    public static void source(IntBuffer data) {
        if (done || phase != 2 || data.remaining() % 19 != 0 || !collectGolden) return;
        List<int[]> rows = golden.computeIfAbsent(MODES[scenario], ignored -> new ArrayList<>());
        for (int at = data.position(); at < data.limit(); at += 19) {
            int[] row = new int[19]; for (int i = 0; i < row.length; i++) row[i] = data.get(at + i); rows.add(row);
        }
        report.put("nativeReferenceActor-" + MODES[scenario], actorState());
    }

    public static boolean sameFrameReferenceNeeded() {
        return enabled() && !done && phase == 2 && gpu() && !MODES[scenario].startsWith("cancel") && measuredFrames >= 12 && measuredFrames < 14;
    }
    public static void nativeReference(int[] row) { sameFrameNative.add(row); nativeReferenceParts++; }
    public static void referenceFailure(Throwable error) { failure = "Native same-frame reference failed: " + error; }
    public static void beginGlint() { if (enabled() && phase == 2 && !done) glintDepth++; }
    public static void endGlint() { if (enabled() && phase == 2 && !done) glintDepth--; }
    public static void glintPart(net.minecraft.client.render.model.ModelPart part) {
        if (enabled() && phase == 2 && !done && glintDepth > 0 && part.visible && !part.invisible && !part.boxes.isEmpty()) glintModelParts++;
    }

    public static void captureBegin(Object capture, boolean player) {
        if (!enabled() || phase != 2 || done) return;
        captureBegins++; if (player) playerCaptureBegins++;
    }

    public static void originalPart() { if (enabled() && phase == 2 && !done) originalPartCalls++; }

    public static void captureModel(Object capture) {
        if (!enabled() || phase != 2 || done) return;
        try {
            Map<String, Object> values = diagnostics();
            for (String key : List.of("player", "armorLayer", "modelActive", "finished", "matrixMode", "pass")) values.put(key, String.valueOf(field(capture, key)));
            Object actor = actor();
            values.put("actorActive", actor != null && Boolean.TRUE.equals(actor.getClass().getMethod("isBatchActive").invoke(actor)));
            values.put("sameActiveCapture", actor != null && field(actor, "activeCapture") == capture);
            captures.put(name() + "-pass" + renderPass(), values);
        } catch (Throwable error) { captures.put(name() + "-diagnosticError", error.toString()); }
    }

    public static void batch(boolean active) {
        if (!enabled() || phase != 2 || done) return;
        try { Map<String, Object> values = diagnostics(); values.put("active", active); batches.put(name() + "-pass" + renderPass(), values); }
        catch (Throwable error) { batches.put(name() + "-diagnosticError", error.toString()); }
    }

    public static void layer(Object capture, Object layer, boolean active) {
        if (!enabled() || phase != 2 || done) return;
        try {
            Map<String, Object> values = diagnostics(); values.put("began", active); values.put("layer", layer.getClass().getName());
            for (String key : List.of("armorLayer", "modelActive", "finished", "matrixMode", "pass")) values.put(key, String.valueOf(field(capture, key)));
            captures.put(name() + "-layer-" + layer.getClass().getName(), values);
        } catch (Throwable error) { captures.put(name() + "-layerError", error.toString()); }
    }

    private static int renderPass() throws ReflectiveOperationException {
        return (int)Class.forName("net.minecraftforge.client.MinecraftForgeClient").getMethod("getRenderPass").invoke(null);
    }

    private static Object actor() throws ReflectiveOperationException {
        return Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing").getMethod("current").invoke(null);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private static Map<String, Object> diagnostics() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("modelTransforms", GpuModelTransforms.status()); values.put("visiblePlayers", GpuModelTransforms.visiblePlayers());
        values.put("gpuPlayerPipeline", BetaOptimizationsConfig.getGpuPlayerPipeline()); values.put("gpuEquipmentPipeline", BetaOptimizationsConfig.getGpuEquipmentPipeline());
        values.put("minimumPlayers", BetaOptimizationsConfig.getMinimumPlayers()); values.put("directPlayers", GpuPlayerPipeline.playersDrawn);
        values.put("nativeCallbackRoutes", GpuPlayerPipeline.nativeCallbackRoutes); values.put("nativePoses", NativePlayerPosePipeline.status());
        return values;
    }

    private static Map<String, Object> actorState() {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            var player = (RemoteClientPlayerEntity)probe.getField("target").get(null); var camera = Minecraft.getInstance().player;
            values.put("position", List.of(player.x, player.y, player.z)); values.put("lastPosition", List.of(player.lastX, player.lastY, player.lastZ));
            values.put("age", player.ticks); values.put("head", List.of(player.lastHeadYaw, player.headYaw, player.lastBodyYaw, player.bodyYaw, player.lastPitch, player.pitch));
            values.put("walk", List.of(player.walkAnimationProgress, player.lastWalkAnimationSpeed, player.walkAnimationSpeed));
            values.put("swing", List.of(player.lastAttackAnimationProgress, player.attackAnimationProgress));
            values.put("camera", List.of(camera.x, camera.y, camera.z, camera.lastX, camera.lastY, camera.lastZ, (double)camera.getEyeHeight()));
        } catch (Throwable error) { values.put("error", error.toString()); }
        return values;
    }

    public static boolean verifyEnabled(IntBuffer data) {
        return !done && phase == 2 && gpu() && !MODES[scenario].startsWith("cancel") && measuredFrames >= 12 && measuredFrames < 14 && GpuModelTransforms.hasJobs(data);
    }

    public static void verify(int buffer, IntBuffer original) {
        if (failure != null) return;
        try {
            int previous = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER); IntBuffer result = BufferUtils.createIntBuffer(original.remaining());
            try { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffer); GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, result); }
            finally { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, previous); }
            List<int[]> output = new ArrayList<>();
            for (int p = 0; p < result.limit(); p += 19) { int[] row = new int[19]; for (int i = 0; i < 19; i++) row[i] = result.get(p + i); output.add(row); }
            List<int[]> nativeRows = sameFrameNative; require(!nativeRows.isEmpty(), "No native same-frame reference geometry was captured");
            for (int p = original.position(); p < original.limit(); p += 19) {
                if (Float.intBitsToFloat(original.get(p + 1)) != -9876543f) continue;
                Matrix4f matrix = GpuModelTransforms.resolve((int)Float.intBitsToFloat(original.get(p)), new Matrix4f());
                int[] expected = new int[19]; for (int c = 0; c < 4; c++) for (int r = 0; r < 3; r++) expected[c * 3 + r] = Float.floatToRawIntBits(matrix.get(c, r));
                for (int i = 12; i < 19; i++) expected[i] = original.get(p + i);
                float nativeError = match(expected, nativeRows, true), gpuError = match(expected, output, true);
                if (nativeError >= .001f || gpuError >= .001f) {
                    report.put("failedExpected", expected); report.put("failedActor", actorState());
                    report.put("nearestNative", nativeRows.stream().sorted(java.util.Comparator.comparingDouble(row -> matrixError(expected, row))).limit(4).toList());
                }
                require(nativeError < .001f && gpuError < .001f, "Native/GPU pose differs: " + nativeError + " / " + gpuError);
                maximumNativeError = Math.max(maximumNativeError, nativeError); maximumGpuError = Math.max(maximumGpuError, gpuError); verifiedMatrices++;
            }
        } catch (Throwable error) { failure = error.toString(); }
    }

    private static float match(int[] expected, List<int[]> candidates, boolean matchTime) {
        float best = Float.POSITIVE_INFINITY;
        for (int[] actual : candidates) {
            boolean metadata = true; for (int i = matchTime ? 12 : 13; i < 19; i++) metadata &= expected[i] == actual[i];
            if (!metadata) continue;
            float error = 0; for (int i = 0; i < 12; i++) error = Math.max(error, Math.abs(Float.intBitsToFloat(expected[i]) - Float.intBitsToFloat(actual[i])));
            best = Math.min(best, error);
        }
        return best;
    }

    private static float matrixError(int[] expected, int[] actual) {
        float error = 0; for (int i = 0; i < 12; i++) error = Math.max(error, Math.abs(Float.intBitsToFloat(expected[i]) - Float.intBitsToFloat(actual[i]))); return error;
    }

    public static void frame() {
        if (done) return;
        try {
            if (failure != null) throw new IllegalStateException(failure);
            if (screenshot) {
                screenshot = false; Minecraft mc = Minecraft.getInstance();
                net.minecraft.client.Screenshot.take(mc.gameDir, "forge-pose072-" + Math.max(0, scenario - 1) + ".png", mc.width, mc.height, mc.getRenderTarget());
            }
            if (phase == 3) {
                require(verifiedMatrices > 0, "No compact native poses were verified against original geometry");
                report.put("verifiedMatrices", verifiedMatrices); report.put("maximumNativeMatrixError", maximumNativeError); report.put("maximumGpuMatrixError", maximumGpuError);
                report.put("callbacksPreserved", true); report.put("cancelPlayerPreserved", true); report.put("cancelLivingPreserved", true); report.put("modPoseMutationPreserved", true);
                report.put("nativeSameFrameReferenceParts", nativeReferenceParts); report.put("glintModelPartCalls", glintModelParts);
                report.put("sameFrameNativeOracle", true);
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void finish(Throwable error) {
        if (done) return; done = true;
        report.put("passed", error == null); if (error != null) { report.put("error", error.toString()); error.printStackTrace(); }
        report.put("finalDiagnostics", diagnostics()); report.put("nativeCaptureBegins", captureBegins); report.put("nativePlayerCaptureBegins", playerCaptureBegins);
        report.put("originalRecordPartCalls", originalPartCalls); report.put("captureDiagnostics", captures); report.put("batchDiagnostics", batches);
        try {
            if (probe != null) { probe.getMethod("reset").invoke(null); probe.getField("target").set(null, null); }
            if (armorGlint != null) armorGlint.set(itemsConfig, originalArmorGlint);
            Path output = Path.of(System.getProperty("arcadium.forgePose072.output")); Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final class Frozen extends RemoteClientPlayerEntity {
        Frozen(Minecraft minecraft, GameProfile profile) { super(minecraft.world, profile); ticks = 160; }
        @Override public void tick() { ticks = 160; }
    }
}
