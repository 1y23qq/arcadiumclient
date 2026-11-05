// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import io.github.arcadiumclient.runtime.client.optimizations.ModernGlintShaderPatch;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ModernGlint072Qa {
    private static final String[] STYLES = {"V1_19_4", "V1_15", "V1_19_4"};
    private static final String[] CASES = {"V1_19_4", "V1_15", "V1_19_4-hurt-fallback"};
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static Field styleField;
    private static Object items, originalStyle;
    private static Frozen actor;
    private static int ticks, wait, phase, style, warmFrames, guardedDraws, orderChecks;
    private static int guard, genericGuard, originalIndexed, originalGeneric, alignment;
    private static long originalOffset, originalSize;
    private static boolean done, normalSeen;
    private static String failure;
    private static long styleLayers, styleParts;
    private static long styleNativeLayers;
    private static final java.util.Set<Integer> verifiedPrograms = new HashSet<>();
    private static final float[] nativeTextureMatrix = new float[16];
    private static boolean nativeTextureReady;
    private static int comparisonMode, textureChecks;
    private static double maximumTextureError;
    private static java.lang.reflect.Method currentCapture;
    private static Field capturedTextureMatrix;
    private static boolean baseOnly;
    private static final IdentityHashMap<net.minecraft.client.render.model.ModelPart, float[]> nativeModelViews = new IdentityHashMap<>();
    private static final float[] cameraModelView = new float[16];
    private static int modelViewChecks;
    private static double maximumModelViewError;
    private static double maximumRootModelViewError;
    private static final List<String> pixelFailures = new java.util.ArrayList<>();
    private static int glintDepth;
    private static boolean zeroBindingGuard;
    private static int zeroRangeChecks, zeroMaterialDrawChecks;

    private ModernGlint072Qa() {}
    public static boolean enabled() { return System.getProperty("arcadium.modernGlint072.output") != null; }

    public static void tick() {
        if (done) return;
        try {
            Minecraft mc = Minecraft.getInstance(); wait++;
            require(++ticks < 6000, "Modern glint QA timed out");
            if (failure != null) throw new IllegalStateException(failure);
            if (phase == 0 && wait > 60 && mc.screen != null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Modern glint QA requires the isolated fake account");
                require(!Boolean.getBoolean("arcadium.forge.prepared"), "Positive modern glint fixture must not fake or enable Forge");
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                Skin3DConfig.INSTANCE.chooseEnabled(false);
                BetaOptimizationsConfig.setGpuModelTransforms(false); BetaOptimizationsConfig.setGpuPlayerPipeline(false);
                BetaOptimizationsConfig.setGpuEquipmentPipeline(true); BetaOptimizationsConfig.setMinimumPlayers(1);
                BetaOptimizationsConfig.setGpuNameTags(false); BetaOptimizationsConfig.setGpuParticles(false);
                BetaOptimizationsConfig.setWorldRenderScale(false); BetaOptimizationsConfig.setRetainedHudBatches(false);
                mc.options.pauseOnUnfocus = false; mc.options.vsync = false; mc.options.fpsLimit = 120; mc.options.viewDistance = 3;
                mc.options.cloudRenderMode = 0; mc.options.perspective = 0; mc.options.viewBobbing = false; mc.options.hideGui = true;
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                Class<?> configType = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
                Object config = configType.getMethod("instance").invoke(null); items = configType.getField("items").get(config);
                styleField = items.getClass().getField("armorGlint"); originalStyle = styleField.get(items); selectStyle();
                report.put("purpose", "Same-frame native/modern GPU pixel and GL state correctness; not FPS data");
                report.put("experimentalModernArmorRoute", Boolean.getBoolean("arcadium.modernGlint072.experimental"));
                report.put("settings", Map.of("fpsLimit", 120, "renderDistance", 3, "minimumPlayers", 1, "gpuPlayerPipeline", false,
                        "gpuModelTransforms", false, "gpuNameTags", false, "gpuParticles", false, "worldRenderScale", false,
                        "retainedHudBatches", false, "renderDelta", .5f));
                mc.startGame("arcadium-modern-glint072", "Modern Glint QA", new WorldSettings(720721, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 100) {
                mc.openScreen(null); mc.player.abilities.flying = true; mc.player.updatePositionAndAngles(0, 19, -10, 0, 5);
                actor = new Frozen(mc, new GameProfile(new UUID(0x7210000000000000L, 1), "ModernGlintQA"));
                actor.updatePositionAndAngles(0, 17, 3, 190, 12);
                actor.setEquipment(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
                net.minecraft.item.Item[] pieces = {net.minecraft.item.Items.DIAMOND_BOOTS, net.minecraft.item.Items.DIAMOND_LEGGINGS, net.minecraft.item.Items.DIAMOND_CHESTPLATE, net.minecraft.item.Items.DIAMOND_HELMET};
                for (int slot = 1; slot <= 4; slot++) {
                    var stack = new net.minecraft.item.ItemStack(pieces[slot - 1]); stack.addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING, 1); actor.setEquipment(slot, stack);
                }
                mc.world.forceEntity(721072, actor); freeze(mc);
                report.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); report.put("resolution", mc.width + "x" + mc.height);
                phase = 2; warmFrames = 0;
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void selectStyle() throws IllegalAccessException {
        boolean found = false;
        for (Object value : styleField.getType().getEnumConstants()) if (((Enum<?>)value).name().equals(STYLES[style])) { styleField.set(items, value); found = true; }
        require(found, "Requested native armor glint style is unavailable: " + STYLES[style]);
        styleLayers = ModernArmorGlintPipeline.layersSupported; styleParts = ModernArmorGlintPipeline.glintParts;
        styleNativeLayers = ModernArmorGlintPipeline.nativeLayers;
    }

    private static void freeze(Minecraft mc) {
        if (actor == null) return;
        actor.ticks = 160; actor.bodyYaw = actor.lastBodyYaw = 190; actor.headYaw = actor.lastHeadYaw = 205;
        actor.pitch = actor.lastPitch = 12; actor.yaw = actor.lastYaw = 190;
        actor.walkAnimationProgress = 2.4f; actor.walkAnimationSpeed = actor.lastWalkAnimationSpeed = .6f;
        actor.attackAnimationProgress = actor.lastAttackAnimationProgress = .35f;
        actor.damagedTimer = style == 2 ? 5 : 0;
        actor.lastX = actor.prevX = actor.x; actor.lastY = actor.prevY = actor.y; actor.lastZ = actor.prevZ = actor.z;
        mc.player.yaw = mc.player.lastYaw = 0; mc.player.pitch = mc.player.lastPitch = 5;
        mc.player.lastX = mc.player.prevX = mc.player.x; mc.player.lastY = mc.player.prevY = mc.player.y; mc.player.lastZ = mc.player.prevZ = mc.player.z;
        mc.world.setTimeOfDay(6000);
    }

    public static void world(float delta, long deadline, Operation<Void> original) {
        if (done || phase != 2) { original.call(delta, deadline); return; }
        try {
            Minecraft mc = Minecraft.getInstance(); freeze(mc); normalSeen = false;
            if (++warmFrames <= (style == 0 ? 80 : 60)) {
                zeroBindingGuard = style == 0 && warmFrames <= 20;
                BetaOptimizationsConfig.setGpuEquipmentPipeline(!zeroBindingGuard);
                guardBegin();
                try { original.call(.5f, deadline); guardCheck(); }
                finally { guardEnd(); zeroBindingGuard = false; BetaOptimizationsConfig.setGpuEquipmentPipeline(true); }
                return;
            }
            String effectiveStyle = ((Enum<?>)styleField.get(items)).name();
            require(effectiveStyle.equals(STYLES[style]), "Another callback changed the requested native armor style to " + effectiveStyle);
            if (style < 2) require(ModernArmorGlintPipeline.layersSupported > styleLayers && ModernArmorGlintPipeline.glintParts > styleParts,
                    "Modern adapter did not activate: " + ModernArmorGlintPipeline.status() + " shaderPins=" + ModernGlintShaderPatch.vertexPatched + "/" + ModernGlintShaderPatch.fragmentPatched);
            else require(ModernArmorGlintPipeline.layersSupported == styleLayers && ModernArmorGlintPipeline.glintParts == styleParts &&
                    ModernArmorGlintPipeline.nativeLayers > styleNativeLayers, "Unsupported hurt armor was not retained by the original scoped layer");
            if (style < 2) baseControl(mc, deadline, original);
            BetaOptimizationsConfig.setGpuEquipmentPipeline(false); normalSeen = false;
            comparisonMode = 1; nativeTextureReady = false; nativeModelViews.clear();
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage nativeImage = capture(mc);
            BetaOptimizationsConfig.setGpuEquipmentPipeline(true); normalSeen = false;
            comparisonMode = 2;
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage optimized = capture(mc);
            comparisonMode = 0;
            Path directory = Path.of(System.getProperty("arcadium.modernGlint072.output")).toAbsolutePath().getParent(); Files.createDirectories(directory);
            ImageIO.write(nativeImage, "png", directory.resolve(CASES[style] + "-native.png").toFile()); ImageIO.write(optimized, "png", directory.resolve(CASES[style] + "-gpu.png").toFile());
            int maximum = 0, changed = 0;
            for (int y = 0; y < nativeImage.getHeight(); y++) for (int x = 0; x < nativeImage.getWidth(); x++) {
                int a = nativeImage.getRGB(x, y), b = optimized.getRGB(x, y); if (a != b) changed++;
                for (int shift = 0; shift < 32; shift += 8) maximum = Math.max(maximum, Math.abs((a >>> shift & 255) - (b >>> shift & 255)));
            }
            report.put(CASES[style], Map.of("maximumChannelError", maximum, "changedPixels", changed,
                    "layers", ModernArmorGlintPipeline.layersSupported - styleLayers, "taggedGlintStates", ModernArmorGlintPipeline.glintParts - styleParts,
                    "originalFallbackLayers", ModernArmorGlintPipeline.nativeLayers - styleNativeLayers, "effectiveStyle", effectiveStyle,
                    "mode", style < 2 ? "captured-modern-glint" : "original-hurt-glint"));
            if (maximum > 2) pixelFailures.add(CASES[style] + " exceeds the native 8-bit instance-color tolerance2: " + maximum);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after modern glint comparison");
            if (++style == STYLES.length) phase = 3;
            else { selectStyle(); warmFrames = 0; }
        } catch (Throwable error) { failure = error.toString(); }
    }

    private static void baseControl(Minecraft mc, long deadline, Operation<Void> original) throws Exception {
        baseOnly = true;
        try {
            BetaOptimizationsConfig.setGpuEquipmentPipeline(false); normalSeen = false; comparisonMode = 1; nativeModelViews.clear();
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage nativeImage = capture(mc);
            BetaOptimizationsConfig.setGpuEquipmentPipeline(true); normalSeen = false; comparisonMode = 2;
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage optimized = capture(mc);
            Path directory = Path.of(System.getProperty("arcadium.modernGlint072.output")).toAbsolutePath().getParent();
            ImageIO.write(nativeImage, "png", directory.resolve(CASES[style] + "-base-only-native.png").toFile());
            ImageIO.write(optimized, "png", directory.resolve(CASES[style] + "-base-only-gpu.png").toFile());
            int maximum = 0, changed = 0, aboveTwo = 0;
            for (int y = 0; y < nativeImage.getHeight(); y++) for (int x = 0; x < nativeImage.getWidth(); x++) {
                int a = nativeImage.getRGB(x, y), b = optimized.getRGB(x, y), error = 0;
                for (int shift = 0; shift < 32; shift += 8) error = Math.max(error, Math.abs((a >>> shift & 255) - (b >>> shift & 255)));
                maximum = Math.max(maximum, error); if (a != b) changed++; if (error > 2) aboveTwo++;
            }
            report.put(CASES[style] + "-base-control-diagnostic", Map.of("glintOmittedInBothModes", true, "maximumChannelError", maximum, "changedPixels", changed, "pixelsAboveTwo", aboveTwo));
        } finally { baseOnly = false; comparisonMode = 0; }
    }

    public static boolean omitGlint() { return enabled() && baseOnly; }
    public static void beginGlint() { if (enabled()) glintDepth++; }
    public static void endGlint() { if (enabled()) glintDepth--; }
    public static boolean tracingNative() { return enabled() && !done && phase == 2 && style < 2 && !baseOnly && comparisonMode == 1 && glintDepth > 0; }
    public static boolean tracingGpu() { return enabled() && !done && phase == 2 && style < 2 && !baseOnly && comparisonMode == 2; }
    public static String currentCase() { return CASES[style]; }
    public static boolean needsModelViewReference() { return enabled() && !done && phase == 2 && comparisonMode == 2; }
    public static void referenceFailure(Throwable error) { failure = error.toString(); }
    public static void captureCamera() {
        if (needsModelViewReference()) GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, cameraModelView);
    }
    public static void nativeModelView(net.minecraft.client.render.model.ModelPart part) {
        if (!enabled() || done || phase != 2 || comparisonMode != 1 ||
                ((io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess)part).arcadium$baseModel().getClass() != net.minecraft.client.render.model.entity.HumanoidModel.class) return;
        float[] matrix = new float[16]; GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, matrix); nativeModelViews.put(part, matrix);
    }
    public static void optimizedModelView(net.minecraft.client.render.model.ModelPart part, org.joml.Matrix4fc local) {
        float[] reference = nativeModelViews.get(part); if (reference == null) return;
        var actual = new org.joml.Matrix4f().set(cameraModelView).mul(local);
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++)
            maximumModelViewError = Math.max(maximumModelViewError, Math.abs(actual.get(column, row) - reference[column * 4 + row]));
        modelViewChecks++;
    }
    public static void optimizedRootModelView(org.joml.Matrix4fc local) {
        float[] nativeRoot = new float[16]; GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, nativeRoot);
        var actual = new org.joml.Matrix4f().set(cameraModelView).mul(local);
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++)
            maximumRootModelViewError = Math.max(maximumRootModelViewError, Math.abs(actual.get(column, row) - nativeRoot[column * 4 + row]));
    }

    private static void guardBegin() {
        originalGeneric = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING); originalIndexed = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7);
        originalOffset = originalIndexed == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, 7);
        originalSize = originalIndexed == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 7);
        if (zeroBindingGuard) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, 0); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0); return;
        }
        if (guard == 0) {
            alignment = Math.max(16, GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT));
            guard = GL15.glGenBuffers(); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, guard); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, alignment * 8L, GL15.GL_STATIC_DRAW);
            genericGuard = GL15.glGenBuffers(); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, genericGuard); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, 128, GL15.GL_STATIC_DRAW);
        }
        GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 7, guard, alignment, alignment * 4L);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, genericGuard);
    }
    private static void guardCheck() {
        if (zeroBindingGuard) {
            require(GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7) == 0 &&
                    GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) == 0, "No-material native draw did not restore an unbound SSBO7/generic binding");
            zeroRangeChecks++; return;
        }
        require(GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7) == guard &&
                GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, 7) == alignment &&
                GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 7) == alignment * 4L &&
                GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) == genericGuard, "Modern glint did not restore SSBO7 ID/range/generic binding");
        guardedDraws++;
    }
    private static void guardEnd() {
        if (originalIndexed != 0 && originalSize > 0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 7, originalIndexed, originalOffset, originalSize);
        else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, originalIndexed);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, originalGeneric);
    }

    public static void draw() {
        if (!enabled() || done || phase != 2) return;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM); if (program == 0) return;
        int pass = GL20.glGetUniformLocation(program, "uGlintPass"), count = GL20.glGetUniformLocation(program, "uArcadiumModernGlintCount");
        if (pass < 0) return;
        if (GL20.glGetUniformi(program, pass) < 0) {
            normalSeen = true;
            if (zeroBindingGuard && count >= 0) {
                require(GL20.glGetUniformi(program, count) == 0 && GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7) != 0 &&
                        GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 7) >= 64, "Globally patched no-material native shader has no valid dummy SSBO7");
                zeroMaterialDrawChecks++;
            }
            return;
        }
        if (count < 0 || GL20.glGetUniformi(program, count) == 0) return;
        require(GL43.glGetProgramResourceIndex(program, GL43.GL_SHADER_STORAGE_BLOCK, "ArcadiumModernGlintMatrices") != GL43.GL_INVALID_INDEX,
                "Modern glint draw has no linked material matrix block");
        verifiedPrograms.add(program);
        if (!normalSeen || GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != GL11.GL_EQUAL || GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ||
                !GL11.glIsEnabled(GL11.GL_BLEND) || GL11.glGetInteger(GL11.GL_BLEND_SRC) != GL11.GL_SRC_COLOR || GL11.glGetInteger(GL11.GL_BLEND_DST) != GL11.GL_ONE)
            failure = "Modern GLINT did not retain normal-before-glint/depth/blend state";
        orderChecks++;
    }

    public static long clock(long original, double speed) { return enabled() ? (long)(1234567L * speed * 8.0) : original; }

    public static void textureMatrix() {
        if (!enabled() || done || phase != 2 || comparisonMode == 0 || style >= 2) return;
        try {
            if (!ModernArmorGlintPipeline.active() && comparisonMode == 1) {
                GL11.glGetFloatv(GL11.GL_TEXTURE_MATRIX, nativeTextureMatrix); nativeTextureReady = true;
            } else if (ModernArmorGlintPipeline.active() && comparisonMode == 2 && nativeTextureReady) {
                if (currentCapture == null) {
                    Class<?> capture = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture");
                    currentCapture = capture.getDeclaredMethod("current"); currentCapture.setAccessible(true);
                    capturedTextureMatrix = capture.getDeclaredField("textureMatrices"); capturedTextureMatrix.setAccessible(true);
                }
                org.joml.Matrix4fc matrix = (org.joml.Matrix4fc)capturedTextureMatrix.get(currentCapture.invoke(null));
                for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++)
                    maximumTextureError = Math.max(maximumTextureError, Math.abs(matrix.get(column, row) - nativeTextureMatrix[column * 4 + row]));
                textureChecks++;
            }
        } catch (Throwable error) { failure = error.toString(); }
    }

    public static void frame() {
        if (done) return;
        if (failure != null) finish(new IllegalStateException(failure));
        else if (phase == 3) {
            try {
                require(pixelFailures.isEmpty(), "Modern glint full RGBA quality failed: " + pixelFailures);
                require(zeroRangeChecks >= 20 && zeroMaterialDrawChecks > 0 && ModernArmorGlintPipeline.dummyProgramBinds > 0,
                        "No proof of valid no-material draws with an originally unbound SSBO7");
                require(guardedDraws > 100 && orderChecks > 0 && textureChecks > 0 && maximumTextureError < 1e-6, "Missing range/order/texture matrix correctness evidence");
                report.put("nonzeroSsbo7RangeChecks", guardedDraws); report.put("glintOrderingChecks", orderChecks); finish(null);
            } catch (Throwable error) { finish(error); }
        }
    }

    private static BufferedImage capture(Minecraft mc) {
        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING); IntBuffer pixels = BufferUtils.createIntBuffer(mc.width * mc.height);
        try { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getRenderTarget().frameBufferId); GL11.glReadPixels(0, 0, mc.width, mc.height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous); }
        BufferedImage image = new BufferedImage(mc.width, mc.height, BufferedImage.TYPE_INT_ARGB); int[] row = new int[mc.width];
        for (int y = 0; y < mc.height; y++) { pixels.position((mc.height - 1 - y) * mc.width); pixels.get(row); image.setRGB(0, y, mc.width, 1, row, 0, mc.width); }
        return image;
    }
    private static void finish(Throwable error) {
        if (done) return; done = true; report.put("passed", error == null);
        report.put("nonzeroSsbo7RangeChecks", guardedDraws); report.put("glintOrderingChecks", orderChecks);
        report.put("verifiedProgramHandles", verifiedPrograms); report.put("vertexSourcePinMatched", ModernGlintShaderPatch.vertexPatched);
        report.put("fragmentSourcePinMatched", ModernGlintShaderPatch.fragmentPatched); report.put("nativeTextureMatrixChecks", textureChecks);
        report.put("maximumNativeTextureMatrixError", maximumTextureError); report.put("finalOpenGlError", GL11.glGetError());
        report.put("actualFixedFunctionModelViewChecks", modelViewChecks); report.put("maximumFixedFunctionModelViewError", maximumModelViewError);
        report.put("maximumCurrentDriverRootModelViewError", maximumRootModelViewError);
        report.put("fullRgbaPixelFailures", pixelFailures);
        report.put("fixedFunctionAndInstancedGlintPixelTrace", ModernGlintPixelTrace072Qa.report());
        report.put("originallyUnboundSsbo7WorldChecks", zeroRangeChecks); report.put("zeroMaterialNativeDrawChecks", zeroMaterialDrawChecks);
        report.put("dummyProgramBinds", ModernArmorGlintPipeline.dummyProgramBinds); report.put("materialProgramBinds", ModernArmorGlintPipeline.materialProgramBinds);
        report.put("originalVertexArmorParts", io.github.arcadiumclient.runtime.client.optimizations.NativePlayerPosePipeline.originalArmorPartsQueued);
        if (error != null) { report.put("error", error.toString()); report.put("diagnostic", ModernArmorGlintPipeline.status()); error.printStackTrace(); }
        try {
            if (styleField != null) styleField.set(items, originalStyle);
            Path output = Path.of(System.getProperty("arcadium.modernGlint072.output")); Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (guard != 0) GL15.glDeleteBuffers(guard); if (genericGuard != 0) GL15.glDeleteBuffers(genericGuard);
        } catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static final class Frozen extends RemoteClientPlayerEntity {
        Frozen(Minecraft minecraft, GameProfile profile) { super(minecraft.world, profile); ticks = 160; }
        @Override public void tick() { ticks = 160; }
    }
}
