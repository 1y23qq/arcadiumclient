// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.EfficientMotionBlur;
import io.github.arcadiumclient.runtime.mixin.compat.polyblur.HybridBlurChainAccess;
import io.github.arcadiumclient.runtime.mixin.compat.polyblur.HybridBlurPassAccess;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.shaders.BlendMode;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.resource.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.config.v1.Config;

public final class MotionBlur066Qa {
    private static final String PACK = "Arcadium066BlurAlpha.zip";
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, Object> originalConfig = new LinkedHashMap<>();
    private static final List<Map<String, Object>> cases = new ArrayList<>();
    private static final List<Context> contexts = new ArrayList<>();
    private static final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer ints = BufferUtils.createIntBuffer(4);
    private static Config blurConfig;
    private static Class<?> hybrid;
    private static Method snapshot, render;
    private static Field chainField, widthField, heightField, lastFrameField, clockField;
    private static List<ResourcePacks.UnopenedPack> originalPacks;
    private static List<String> originalPackOptions, originalIncompatible;
    private static long originalFrame, checks, rgbaBytes;
    private static long nativeCopyFrames, nativeCopyChangedFrames, stockCopyFrames, stockCopyChangedFrames;
    private static boolean originalEfficient, originalVsync, originalUnfocused, originalDither, done;
    private static int ticks;

    private MotionBlur066Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.motionBlur066.output") != null; }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken())
                && mc.world == null, "Motion blur QA requires the isolated offline fixture");
            require(mc.gameDir.toPath().toAbsolutePath().normalize().equals(Path.of("C:/Temp/Arcadium066MotionBlur/game").toAbsolutePath().normalize()),
                "Motion blur QA must use its owned short fixture");
            if (++ticks < 75 || mc.screen == null) return;
            initialize(mc);
            verify(mc);
            finish(null);
        } catch (Throwable failure) { finish(failure); }
    }

    private static void initialize(Minecraft mc) throws Exception {
        require(GL.getCapabilities().OpenGL30, "Native RGBA oracle needs OpenGL 3 compatibility");
        originalEfficient = BetaOptimizationsConfig.getEfficientMotionBlur();
        require(!originalEfficient, "The optional blur optimization must default off");
        var control = BetaOptimizationsConfig.INSTANCE.getTree().getProp("efficientMotionBlur");
        require(control != null && control.getMetadata("visualizer") != null, "The blur optimization has no normal Beta setting");
        originalVsync = mc.options.vsync;
        originalDither = GL11.glIsEnabled(GL11.GL_DITHER);
        originalUnfocused = org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps;
        mc.options.vsync = false; Display.setVSyncEnabled(false);
        org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
        hybrid = Class.forName("org.polyfrost.polyblur.client.blur.phosphor.HybridHandPhosphor");
        snapshot = hybrid.getMethod("snapshotWorld", RenderTarget.class);
        render = hybrid.getMethod("render", RenderTarget.class);
        chainField = field(hybrid, "postChain"); widthField = field(hybrid, "prevWidth"); heightField = field(hybrid, "prevHeight");
        lastFrameField = field(hybrid, "lastFrame");
        clockField = field(Class.forName("org.polyfrost.polyblur.client.blur.FrameClock"), "frame");
        require(chainField.get(null) == null, "Use a fresh fixture without a previous hand-blur chain");
        originalFrame = clockField.getLong(null);
        blurConfig = (Config) Class.forName("org.polyfrost.polyblur.client.PolyBlurConfig").getField("INSTANCE").get(null);
        for (String id : List.of("isEnabled", "blurType", "phosphorMode", "strength", "handBlurStrength", "motionBlurSamples", "blurHand")) {
            var prop = blurConfig.getTree().getProp(id);
            require(prop != null, "The actual PolyBlur setting is missing: " + id);
            originalConfig.put(id, prop.get());
        }
        require(Boolean.TRUE.equals(originalConfig.get("isEnabled")), "Do not disable the real motion blur setting for the oracle");
        report.put("originalBlurSettings", new LinkedHashMap<>(originalConfig));
        originalPacks = List.copyOf(mc.getResourcePacks().getSelectedPacks());
        originalPackOptions = List.copyOf(mc.options.resourcePacks);
        originalIncompatible = List.copyOf(mc.options.incompatibleResourcePacks);
    }

    private static void verify(Minecraft mc) throws Exception {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix();
        try {
            compare("native-control", 0, 8, true);
            for (int mode = 0; mode < 3; mode++) compare("stock-mode-" + mode, mode, 28, true);
            for (int mode = 0; mode < 3; mode++) compare("dither-mode-" + mode, mode, 12, true);
            compare("cache-copy", 2, 12, true);
            compare("cache-opaque", 2, 12, true);
            compare("mode-changes", -1, 24, true);
            compare("resize", 2, 18, true);
            for (int mode = 0; mode < 3; mode++) compare("same-chain-resize-" + mode, mode, 18, true);
            for (String scenario : List.of("copy-modulation", "linear-filter", "target-size", "aux-alias", "pass-order", "option-toggle")) {
                compare(scenario, 2, 12, true);
            }
            closeContexts();
            applyAlphaPack(mc);
            for (int mode = 0; mode < 3; mode++) compare("custom-alpha-mode-" + mode, mode, 14, false);
            closeContexts();
            compareRetainedReload(mc);
            compare("stock-after-reload", 2, 16, true);
            report.put("cases", cases);
            report.put("rgbaBytesCompared", rgbaBytes);
            report.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
            report.put("mainAndHistoryExact", true);
            report.put("worldAndObjectIdentitiesPreserved", true);
            report.put("allThreeModes", true);
            report.put("bootstrapRepeatedFramesAndGaps", true);
            report.put("realResourcePackFallbackAndRecovery", true);
            report.put("retainedCustomProgramAfterStockReloadNative", true);
        } finally {
            closeContexts();
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrix);
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    private static void compare(String scenario, int mode, int count, boolean stock) throws Exception {
        Context reference = new Context(191, 117), candidate = new Context(191, 117);
        comparePair(reference, candidate, scenario, mode, count, stock);
        reference.close(); candidate.close();
        errorFree(scenario);
    }

    private static void compareRetainedReload(Minecraft mc) throws Exception {
        Context reference = new Context(191, 117), candidate = new Context(191, 117);
        comparePair(reference, candidate, "custom-alpha-before-retained-reload", 2, 8, false);
        PostChain nativeChain = reference.chain, optimizedChain = candidate.chain;
        restorePacks(mc);
        comparePair(reference, candidate, "retained-custom-after-stock-reload", 2, 8, false);
        require(reference.chain == nativeChain && candidate.chain == optimizedChain, "Resource reload replaced the retained-chain oracle");
        reference.close(); candidate.close(); closeContexts();
    }

    private static void comparePair(Context reference, Context candidate, String scenario, int mode, int count, boolean stock) throws Exception {
        List<Frame> expected = sequence(reference, scenario, mode, count, false, stock);
        long before = EfficientMotionBlur.swappedFrames;
        boolean candidateEnabled = !scenario.equals("native-control");
        List<Frame> actual = sequence(candidate, scenario, mode, count, candidateEnabled, stock);
        long accelerated = EfficientMotionBlur.swappedFrames - before;
        for (int i = 0; i < count; i++) {
            Frame a = expected.get(i), b = actual.get(i);
            require(a.width == b.width && a.height == b.height, "Framebuffer dimensions differ: " + scenario);
            if (!Arrays.equals(a.main, b.main) || !Arrays.equals(a.history, b.history) || !Arrays.equals(a.world, b.world) || !a.state.equals(b.state)) {
                report.put("firstMismatch", Map.of("scenario", scenario, "frame", i, "mainEqual", Arrays.equals(a.main, b.main),
                    "historyEqual", Arrays.equals(a.history, b.history), "worldEqual", Arrays.equals(a.world, b.world),
                    "stateEqual", a.state.equals(b.state), "stateDifference", a.state.difference(b.state)));
                writeImage(a.main, a.width, a.height, "mismatch-native-main.png");
                writeImage(b.main, b.width, b.height, "mismatch-optimized-main.png");
                writeImage(a.history, a.width, a.height, "mismatch-native-history.png");
                writeImage(b.history, b.width, b.height, "mismatch-optimized-history.png");
                writeImage(a.world, a.width, a.height, "mismatch-native-world.png");
                writeImage(b.world, b.width, b.height, "mismatch-optimized-world.png");
            }
            comparePixels(a.main, b.main, scenario, i, "main");
            comparePixels(a.history, b.history, scenario, i, "history");
            comparePixels(a.world, b.world, scenario, i, "world");
            require(a.state.equals(b.state), "GL tail differs in " + scenario + " frame " + i + ": " + a.state.difference(b.state));
        }
        require(stock && candidateEnabled ? accelerated > 0 : accelerated == 0, "Unexpected history-copy path in " + scenario + ": " + EfficientMotionBlur.status());
        Frame lastA = expected.get(count - 1), lastB = actual.get(count - 1);
        if (scenario.equals("stock-mode-2") || scenario.equals("custom-alpha-mode-2")) {
            writeImage(lastA.main, lastA.width, lastA.height, scenario + "-native-main.png");
            writeImage(lastB.main, lastB.width, lastB.height, scenario + "-optimized-main.png");
            writeImage(lastA.history, lastA.width, lastA.height, scenario + "-native-history.png");
            writeImage(lastB.history, lastB.width, lastB.height, scenario + "-optimized-history.png");
        }
        cases.add(Map.of("scenario", scenario, "frames", count, "acceleratedFrames", accelerated,
            "rgbaMainAndHistoryEqual", true, "rgbaWorldEqual", true, "glTailEqual", true));
    }

    private static List<Frame> sequence(Context context, String scenario, int mode, int count, boolean optimized, boolean stock) throws Exception {
        List<Frame> frames = new ArrayList<>();
        Field cache = Arrays.stream(BlendMode.class.getDeclaredFields()).filter(value -> java.lang.reflect.Modifier.isStatic(value.getModifiers())
            && value.getType() == BlendMode.class).findFirst().orElseThrow();
        cache.setAccessible(true); cache.set(null, null);
        GlStateManager.disableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        if (scenario.equals("cache-copy")) new BlendMode(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL14.GL_FUNC_ADD).apply();
        if (scenario.equals("cache-opaque")) new BlendMode().apply();
        if (originalDither || scenario.startsWith("dither-")) GL11.glEnable(GL11.GL_DITHER); else GL11.glDisable(GL11.GL_DITHER);
        for (int i = 0; i < count; i++) {
            configure();
            context.install();
            if (scenario.equals("resize") && (i == 5 || i == 11)) context.resize(i == 5 ? 173 : 191, i == 5 ? 103 : 117);
            PostChain sameChain = context.chain;
            if (scenario.startsWith("same-chain-resize") && (i == 5 || i == 11)) {
                context.resizeSameChain(i == 5 ? 173 : 191, i == 5 ? 103 : 117);
            }
            int currentMode = mode < 0 ? i / 4 % 3 : mode;
            blurConfig.getTree().getProp("phosphorMode").setAs(currentMode);
            blurConfig.getTree().getProp("handBlurStrength").setAs(i % 7 == 0 ? 1f : i % 7 == 1 ? 10f : 5f);
            boolean option = optimized && !(scenario.equals("option-toggle") && i >= 3 && i < 7);
            BetaOptimizationsConfig.INSTANCE.getTree().getProp("efficientMotionBlur").setAs(option);
            long frame = 1000L + i + (i >= 8 ? 5 : 0) - (i == 15 ? 1 : 0);
            clockField.setLong(null, frame);
            boolean alpha = scenario.contains("mode") && i % 4 < 2;
            upload(context.main, pattern(context.main.viewWidth, context.main.viewHeight, i, false, alpha));
            snapshot.invoke(null, context.main);
            context.update();
            if (scenario.startsWith("same-chain-resize") && sameChain != null) require(context.chain == sameChain, "The same-chain resize oracle replaced its chain");
            RenderTarget swap = context.chain.getTempTarget("swap"), previous = context.chain.getTempTarget("previous"), world = context.chain.getTempTarget("world");
            int[] mainIds = ids(context.main), worldIds = ids(world), swapIds = ids(swap), previousIds = ids(previous);
            var chain = (HybridBlurChainAccess) context.chain;
            var composite = (HybridBlurPassAccess) chain.arcadium$blurPasses().get(1);
            boolean altered = i >= 3 && i < 7;
            switch (scenario) {
                case "copy-modulation" -> chain.arcadium$blurPasses().get(2).getEffect().getUniform("ColorModulate")
                    .set(altered ? .7f : 1f, altered ? .8f : 1f, altered ? .9f : 1f, altered ? .6f : 1f);
                case "linear-filter" -> previous.setFilterMode(altered ? GL11.GL_LINEAR : GL11.GL_NEAREST);
                case "target-size" -> previous.viewWidth = altered ? context.main.viewWidth - 1 : context.main.viewWidth;
                case "aux-alias" -> composite.arcadium$blurAuxAssets().set(0, altered ? world : previous);
                case "pass-order" -> { if (altered) java.util.Collections.swap(chain.arcadium$blurPasses(), 2, 3); }
                default -> { }
            }
            upload(context.main, pattern(context.main.viewWidth, context.main.viewHeight, i, true, alpha));
            long before = EfficientMotionBlur.swappedFrames;
            render.invoke(null, context.main);
            context.lastFrame = lastFrameField.getLong(null);
            boolean accelerated = EfficientMotionBlur.swappedFrames != before;
            boolean expected = option && stock && !(altered && List.of("copy-modulation", "linear-filter", "target-size", "aux-alias", "pass-order").contains(scenario));
            require(accelerated == expected, "Wrong native/optimized selection: " + scenario + " frame " + i + " | " + EfficientMotionBlur.status());
            require(Arrays.equals(mainIds, ids(context.main)) && Arrays.equals(worldIds, ids(world)), "Main/world attachments changed: " + scenario);
            require(context.chain.getTempTarget("swap") == swap && context.chain.getTempTarget("previous") == previous
                && chain.arcadium$blurMain() == context.main, "A render target object identity changed");
            require(Arrays.equals(ids(swap), accelerated ? previousIds : swapIds) && Arrays.equals(ids(previous), accelerated ? swapIds : previousIds),
                "Private attachment ownership did not exactly follow the chosen path");
            State state = State.capture();
            byte[] historyPixels = read(previous);
            byte[] mainPixels = read(context.main);
            if (!optimized) {
                nativeCopyFrames++;
                byte[] scratchPixels = read(swap);
                boolean changedGuard = altered && List.of("copy-modulation", "linear-filter", "target-size", "aux-alias", "pass-order").contains(scenario);
                boolean plainCopy = !scenario.contains("custom") && !changedGuard;
                if (plainCopy) stockCopyFrames++;
                if (!Arrays.equals(scratchPixels, historyPixels)) {
                    nativeCopyChangedFrames++;
                    if (plainCopy) stockCopyChangedFrames++;
                    if (!report.containsKey("nativeHistoryCopyChange")) {
                        int mismatch = 0; while (scratchPixels[mismatch] == historyPixels[mismatch]) mismatch++;
                        report.put("nativeHistoryCopyChange", Map.of("scenario", scenario, "frame", i, "byte", mismatch,
                            "scratch", Byte.toUnsignedInt(scratchPixels[mismatch]), "history", Byte.toUnsignedInt(historyPixels[mismatch]),
                            "dither", GL11.glIsEnabled(GL11.GL_DITHER)));
                        writeImage(scratchPixels, context.main.width, context.main.height, "native-copy-scratch.png");
                        writeImage(historyPixels, context.main.width, context.main.height, "native-copy-history.png");
                    }
                }
                if (!Arrays.equals(mainPixels, historyPixels) && !report.containsKey("nativeHistoryVsMainChange")) {
                    int mismatch = 0; while (mainPixels[mismatch] == historyPixels[mismatch]) mismatch++;
                    report.put("nativeHistoryVsMainChange", Map.of("scenario", scenario, "frame", i, "byte", mismatch,
                        "main", Byte.toUnsignedInt(mainPixels[mismatch]), "history", Byte.toUnsignedInt(historyPixels[mismatch]),
                        "dither", GL11.glIsEnabled(GL11.GL_DITHER)));
                }
            }
            frames.add(new Frame(context.main.viewWidth, context.main.viewHeight,
                mainPixels, historyPixels, read(world), state));
            if (scenario.equals("pass-order") && altered) java.util.Collections.swap(chain.arcadium$blurPasses(), 2, 3);
        }
        return frames;
    }

    private static void configure() {
        GL20.glUseProgram(0); GL30.glBindVertexArray(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager.colorMask(true, true, true, true); GlStateManager.depthMask(true);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.loadIdentity();
    }

    private static ByteBuffer pattern(int width, int height, int frame, boolean hand, boolean alpha) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        int handX = Math.floorMod(frame * 13, width + 45) - 20, handY = 15 + Math.floorMod(frame * 7, height / 2);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean foreground = hand && x >= handX && x < handX + 42 && y >= handY && y < handY + 39;
            int r = Math.floorMod(x * 5 + frame * 9 + ((x + frame * 3) / 13 % 2) * 53, 256);
            int g = Math.floorMod(y * 7 + frame * 3, 256), b = Math.floorMod(x * 3 + y * 4 + frame * 11, 256);
            int a = alpha ? (x * 11 + y * 17 + frame * 23) & 255 : 255;
            pixels.put((byte) (foreground ? 235 : r)).put((byte) (foreground ? 67 + y % 91 : g))
                .put((byte) (foreground ? 22 + x % 47 : b)).put((byte) (foreground ? (frame * 13 + x + y) & 255 : a));
        }
        return pixels.flip();
    }

    private static void upload(RenderTarget target, ByteBuffer pixels) {
        GlStateManager.activeTexture(GL13.GL_TEXTURE0); GlStateManager.bindTexture(target.colorTextureId);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, target.width, target.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GlStateManager.bindTexture(0);
    }
    private static byte[] read(RenderTarget target) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(target.width * target.height * 4);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, target.width, target.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.remaining()]; pixels.get(result); return result;
    }
    private static int[] ids(RenderTarget target) { return new int[]{target.frameBufferId, target.colorTextureId, target.depthBufferId}; }
    private static void comparePixels(byte[] a, byte[] b, String scenario, int frame, String target) throws Exception {
        if (!Arrays.equals(a, b)) {
            int mismatch = 0; while (mismatch < Math.min(a.length, b.length) && a[mismatch] == b[mismatch]) mismatch++;
            throw new IllegalStateException("RGBA mismatch " + scenario + " frame " + frame + " target " + target + " byte " + mismatch
                + " native=" + (mismatch < a.length ? Byte.toUnsignedInt(a[mismatch]) : -1) + " optimized=" + (mismatch < b.length ? Byte.toUnsignedInt(b[mismatch]) : -1));
        }
        rgbaBytes += a.length;
    }

    private static void applyAlphaPack(Minecraft mc) throws Exception {
        Path directory = mc.getResourcePacks().getDirectory().toPath().toAbsolutePath().normalize();
        require(directory.startsWith(mc.gameDir.toPath().toAbsolutePath().normalize()), "The QA resource pack directory escaped its fixture");
        Files.createDirectories(directory);
        Path pack = directory.resolve(PACK);
        String shader;
        try (var stream = mc.getResourceManager().getResource(new Identifier("minecraft", "shaders/program/phosphor_hand.fsh")).asStream()) {
            shader = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("mix(world.rgb, phosphor, mask), 1.0", "mix(world.rgb, phosphor, mask), 0.37");
        }
        require(shader.contains("mask), 0.37"), "The stock shader alpha mutation did not match");
        if (Files.exists(pack)) {
            try (var zip = new java.util.zip.ZipFile(pack.toFile())) {
                require(zip.getEntry("arcadium-qa-owner.txt") != null, "Refusing to replace an unowned resource pack");
            }
        }
        try (var zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            entry(zip, "pack.mcmeta", "{\"pack\":{\"pack_format\":1,\"description\":\"Blur RGBA oracle\"}}");
            entry(zip, "arcadium-qa-owner.txt", "MotionBlur066Qa");
            entry(zip, "assets/minecraft/shaders/program/phosphor_hand.fsh", shader);
        }
        mc.getResourcePacks().reload();
        var selected = new ArrayList<>(originalPacks);
        selected.add(mc.getResourcePacks().getAvailablePacks().stream().filter(value -> value.getName().equals(PACK)).findFirst().orElseThrow());
        mc.getResourcePacks().setSelectedPacks(selected);
        mc.options.resourcePacks.add(PACK);
        mc.reloadResources();
        try (var stream = mc.getResourceManager().getResource(new Identifier("minecraft", "shaders/program/phosphor_hand.fsh")).asStream()) {
            require(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("mask), 0.37"), "The real resource pack did not override the active shader");
        }
    }
    private static void entry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static void restorePacks(Minecraft mc) {
        if (originalPacks == null) return;
        mc.getResourcePacks().setSelectedPacks(originalPacks);
        mc.options.resourcePacks.clear(); mc.options.resourcePacks.addAll(originalPackOptions);
        mc.options.incompatibleResourcePacks.clear(); mc.options.incompatibleResourcePacks.addAll(originalIncompatible);
        mc.reloadResources();
    }
    private static void closeContexts() throws Exception {
        for (Context context : contexts) context.close();
        contexts.clear();
        if (chainField != null) chainField.set(null, null);
        EfficientMotionBlur.invalidate();
    }

    private static final class Context {
        final RenderTarget main;
        PostChain chain;
        long lastFrame = -1;
        boolean closed;
        Context(int width, int height) {
            main = new RenderTarget(width, height, true); main.setClearColor(0f, 0f, 0f, 0f); contexts.add(this);
        }
        void install() throws Exception {
            chainField.set(null, chain); widthField.setInt(null, chain == null ? -1 : main.viewWidth);
            heightField.setInt(null, chain == null ? -1 : main.viewHeight); lastFrameField.setLong(null, lastFrame);
        }
        void update() throws Exception {
            chain = (PostChain) chainField.get(null); require(chain != null, "The actual Hybrid hand chain did not load");
        }
        void resize(int width, int height) { main.resize(width, height); }
        void resizeSameChain(int width, int height) throws Exception {
            main.resize(width, height);
            chain.resize(width, height);
            widthField.setInt(null, width); heightField.setInt(null, height);
        }
        void close() {
            if (closed) return; closed = true;
            if (chain != null) chain.close(); main.destroyBuffers();
        }
    }
    private record Frame(int width, int height, byte[] main, byte[] history, byte[] world, State state) { }
    private record State(int[] scalar, float[] matrices) {
        static State capture() {
            var values = new ArrayList<Integer>();
            for (int query : new int[]{GL30.GL_READ_FRAMEBUFFER_BINDING, GL30.GL_DRAW_FRAMEBUFFER_BINDING, GL20.GL_CURRENT_PROGRAM,
                GL30.GL_VERTEX_ARRAY_BINDING, GL15.GL_ARRAY_BUFFER_BINDING, GL13.GL_ACTIVE_TEXTURE, GL13.GL_CLIENT_ACTIVE_TEXTURE,
                GL11.GL_TEXTURE_BINDING_2D, GL11.GL_MATRIX_MODE, GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB,
                GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA}) values.add(GL11.glGetInteger(query));
            for (int query : new int[]{GL11.GL_DEPTH_TEST, GL11.GL_ALPHA_TEST, GL11.GL_BLEND, GL11.GL_LIGHTING,
                GL11.GL_CULL_FACE, GL11.GL_SCISSOR_TEST, GL30.GL_FRAMEBUFFER_SRGB, GL11.GL_DITHER}) values.add(GL11.glIsEnabled(query) ? 1 : 0);
            values.add(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0);
            for (int query : new int[]{GL11.GL_VIEWPORT, GL11.GL_COLOR_WRITEMASK}) {
                ints.clear(); GL11.glGetIntegerv(query, ints); for (int i = 0; i < 4; i++) values.add(ints.get(i));
            }
            float[] matrices = new float[52]; int offset = 0;
            for (int query : new int[]{GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX}) {
                floats.clear(); GL11.glGetFloatv(query, floats); for (int i = 0; i < 16; i++) matrices[offset++] = floats.get(i);
            }
            floats.clear(); GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, floats); for (int i = 0; i < 4; i++) matrices[offset++] = floats.get(i);
            return new State(values.stream().mapToInt(Integer::intValue).toArray(), matrices);
        }
        @Override public boolean equals(Object value) { return value instanceof State other && Arrays.equals(scalar, other.scalar) && Arrays.equals(matrices, other.matrices); }
        @Override public int hashCode() { return Arrays.hashCode(scalar) * 31 + Arrays.hashCode(matrices); }
        String difference(State other) {
            for (int i = 0; i < scalar.length; i++) if (scalar[i] != other.scalar[i]) return "state " + i + ": " + scalar[i] + "/" + other.scalar[i];
            for (int i = 0; i < matrices.length; i++) if (matrices[i] != other.matrices[i]) return "matrix " + i;
            return "unknown";
        }
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void writeImage(byte[] bytes, int width, int height, String name) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int p = (y * width + x) * 4;
            image.setRGB(x, height - 1 - y, Byte.toUnsignedInt(bytes[p + 3]) << 24 | Byte.toUnsignedInt(bytes[p]) << 16
                | Byte.toUnsignedInt(bytes[p + 1]) << 8 | Byte.toUnsignedInt(bytes[p + 2]));
        }
        ImageIO.write(image, "png", output().getParent().resolve(name).toFile());
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new IllegalStateException(message); }
    private static void errorFree(String stage) { require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native GL error after " + stage); }
    private static Path output() { return Path.of(System.getProperty("arcadium.motionBlur066.output")).toAbsolutePath(); }
    private static void finish(Throwable failure) {
        done = true;
        try {
            closeContexts();
            if (blurConfig != null) for (var value : originalConfig.entrySet()) blurConfig.getTree().getProp(value.getKey()).setAs(value.getValue());
            BetaOptimizationsConfig.INSTANCE.getTree().getProp("efficientMotionBlur").setAs(originalEfficient);
            if (clockField != null) clockField.setLong(null, originalFrame);
            Minecraft mc = Minecraft.getInstance();
            mc.options.vsync = originalVsync; Display.setVSyncEnabled(originalVsync);
            org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = originalUnfocused;
            restorePacks(mc);
            report.put("originalSettingsAndPacksRestored", true);
        } catch (Throwable restore) { if (failure == null) failure = restore; }
        report.put("passed", failure == null); report.put("checks", checks); report.put("syntheticOfflineFixture", true);
        report.put("historyFrames", EfficientMotionBlur.swappedFrames); report.put("historyCopiesSkipped", EfficientMotionBlur.skippedCopies);
        report.put("cases", cases); report.put("rgbaBytesCompared", rgbaBytes);
        report.put("nativeHistoryCopyFrames", nativeCopyFrames); report.put("nativeHistoryCopyChangedFrames", nativeCopyChangedFrames);
        report.put("nativeStockCopyFrames", stockCopyFrames); report.put("nativeStockCopyChangedFrames", stockCopyChangedFrames);
        report.put("actualInitialDither", originalDither);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); }
        try { Files.createDirectories(output().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
