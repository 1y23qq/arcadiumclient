package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.click;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.nodes;
import static io.github.arcadiumclient.featuretest.Visual033Qa.object;
import static io.github.arcadiumclient.featuretest.Visual033Qa.properties;
import static io.github.arcadiumclient.featuretest.Visual033Qa.rectangle;
import static io.github.arcadiumclient.featuretest.Visual033Qa.semanticsRoot;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;
import static io.github.arcadiumclient.featuretest.Visual033Qa.staticField;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class Crosshair034Qa {
    private static final String CUSTOM = "io.github.arcadiumclient.runtime.client.features.CustomCrosshair";
    private static final String RENDERER = "io.github.arcadiumclient.runtime.client.features.CustomCrosshairRenderer";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final Map<Path, byte[]> FILES = new LinkedHashMap<>();
    private static Object custom, editor, originalDocument, originalMesh, shell;
    private static Screen parent;
    private static Path file, output;
    private static String search;
    private static int stage, ticks, draws, builds, probe, mouseX, mouseY, frames, frameStart;
    private static int paintedX, paintedY;
    private static boolean begun, done, pending;
    private static Throwable renderFailure;

    private Crosshair034Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.crosshair034Qa") || done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Crosshair QA requires the offline fixture account");
        require(mc.world != null && mc.player != null && mc.isSingleplayer(), "Crosshair QA requires its loaded local world");
        if (!begun) begin(mc, checks);
        if (renderFailure != null) throw new IllegalStateException("Custom crosshair pixel probe failed", renderFailure);
        require(++ticks < 140, "Crosshair UI QA timed out at stage " + stage);
        switch (stage) {
            case 0 -> {
                if (ticks < 12) return false;
                require(mc.screen != null && mc.screen.getClass().getSimpleName().contains("OneConfigUIScreen"), "The normal Crosshair Tweaks settings route did not open");
                click(mc.screen, "Open editor");
            }
            case 1 -> {
                if (ticks < 8 || mc.screen == null || !mc.screen.getClass().getSimpleName().equals("CustomCrosshairScreen")) return false;
                editor = mc.screen;
                require(Boolean.FALSE.equals(call(custom, "enabled")), "The fresh editor did not start disabled");
                click(editor, "Enabled");
                require(Boolean.TRUE.equals(call(custom, "enabled")), "The visible Enabled control did not enable the actual custom crosshair");
                click(editor, "Presets");
            }
            case 2 -> {
                if (ticks < 6) return false;
                clickNth(editor, "Use", 1);
            }
            case 3 -> {
                if (ticks < 6) return false;
                require("Compact".equals(field(stat(CUSTOM, "design"), "name")) && Boolean.TRUE.equals(stat(CUSTOM, "isActive")), "The selected preset did not build an active render mesh");
                capture(mc, "crosshair034-editor-preset.png");
                click(editor, "Done");
            }
            case 4 -> {
                if (ticks < 6 || mc.screen == editor) return false;
                JsonObject saved = persisted();
                require(saved.get("enabled").getAsBoolean() && "Compact".equals(saved.getAsJsonObject("current").get("name").getAsString()), "Done did not persist the enabled preset");
                mc.openScreen(null);
                draws = count("renderedCount"); builds = ((Number) stat(CUSTOM, "meshBuildCount")).intValue();
                frameStart = frames;
                pending = true; probe = 0;
            }
            case 5 -> {
                if (!naturalHud(mc, "preset")) return false;
                require(((Number) stat(CUSTOM, "meshBuildCount")).intValue() == builds, "An unchanged enabled preset rebuilt every frame");
                capture(mc, "crosshair034-world-preset.png");
                RESULT.put("presetEnabledThroughUiAndSaved", true);
                openSettings();
            }
            case 6 -> {
                if (ticks < 10) return false;
                click(mc.screen, "Open editor");
            }
            case 7 -> {
                if (ticks < 8 || mc.screen == null || !mc.screen.getClass().getSimpleName().equals("CustomCrosshairScreen")) return false;
                editor = mc.screen;
                require(Boolean.TRUE.equals(call(editor, "getEnabled")) && Boolean.TRUE.equals(call(custom, "enabled")), "Reopening the editor lost its enabled state");
                click(editor, "Canvas");
            }
            case 8 -> {
                if (ticks < 8) return false;
                Object canvas = description(editor, "Pixel crosshair canvas.");
                List<Float> bounds = rectangle(canvas, mc, "Canvas pointer target");
                int dimension = (Integer) field(call(editor, "getDraft"), "canvasSize");
                float x = bounds.get(0) + (dimension / 2f + 0.5f) / dimension * (bounds.get(2) - bounds.get(0));
                float y = bounds.get(1) + (dimension / 2f + 0.5f) / dimension * (bounds.get(3) - bounds.get(1));
                float ratio = ((Number) call(stat("org.polyfrost.oneconfig.api.platform.v1.Platform", "screen"), "pixelRatio")).floatValue();
                paintedX = Math.round(x * ratio); paintedY = mc.height - Math.round(y * ratio) - 1;
                Mouse.setCursorPosition(paintedX, paintedY);
                RESULT.put("canvasBounds", bounds);
            }
            case 9 -> {
                if (ticks < 4) return false;
                ((ScreenProbeAccess) editor).arcadium$click(paintedX / new Window(mc).getScale(), (mc.height - paintedY - 1) / new Window(mc).getScale(), 0);
            }
            case 10 -> {
                if (ticks < 4) return false;
                ((ScreenProbeAccess) editor).arcadium$release(paintedX / new Window(mc).getScale(), (mc.height - paintedY - 1) / new Window(mc).getScale(), 0);
            }
            case 11 -> {
                if (ticks < 6) return false;
                int[] pixels = (int[]) field(stat(CUSTOM, "design"), "pixels");
                RESULT.put("paintedCanvasPixels", java.util.Arrays.stream(pixels).filter(pixel -> pixel >>> 24 != 0).count());
                require(java.util.Arrays.stream(pixels).anyMatch(pixel -> pixel >>> 24 != 0), "A real canvas pointer press did not update the render design");
                require(Boolean.TRUE.equals(stat(CUSTOM, "isActive")), "The painted canvas did not rebuild an active cached mesh");
                capture(mc, "crosshair034-editor-canvas.png");
                click(editor, "Done");
            }
            case 12 -> {
                if (ticks < 6 || mc.screen == editor) return false;
                JsonObject saved = persisted();
                require(saved.get("enabled").getAsBoolean() && saved.getAsJsonObject("current").get("mode").getAsInt() == 1, "Done did not preserve the enabled canvas mode");
                boolean painted = false;
                for (var pixel : saved.getAsJsonObject("current").getAsJsonArray("pixels")) if (pixel.getAsInt() >>> 24 != 0) painted = true;
                require(painted, "Painted canvas pixels were not saved");
                mc.openScreen(null);
                draws = count("renderedCount"); frameStart = frames; pending = true; probe = 1;
            }
            case 13 -> {
                if (!naturalHud(mc, "canvas")) return false;
                require(((Number) ((Map<?, ?>) RESULT.get("canvasPixelsWithCulling")).get("pixels")).intValue() > 0,
                        "The Canvas enabled, painted and saved through the real UI submitted vertices but produced zero visible pixels with culling enabled");
                require(((Number) ((Map<?, ?>) RESULT.get("presetPixelsWithCulling")).get("pixels")).intValue() > 0,
                        "The preset enabled through the real UI produced zero visible pixels with culling enabled");
                capture(mc, "crosshair034-world-canvas.png");
                RESULT.put("canvasPointerPaintSavedAndRendered", true);
                openSettings();
            }
            case 14 -> {
                if (ticks < 10) return false;
                click(mc.screen, "Open editor");
            }
            case 15 -> {
                if (ticks < 8 || mc.screen == null || !mc.screen.getClass().getSimpleName().equals("CustomCrosshairScreen")) return false;
                editor = mc.screen;
                require(Boolean.TRUE.equals(call(editor, "getEnabled")) && ((Integer) field(call(editor, "getDraft"), "mode")) == 1, "Editor reopening lost its enabled painted canvas");
                click(editor, "Enabled");
                click(editor, "Done");
            }
            case 16 -> {
                if (ticks < 6 || mc.screen == editor) return false;
                require(!persisted().get("enabled").getAsBoolean() && Boolean.FALSE.equals(stat(CUSTOM, "isActive")), "The UI disable action did not restore vanilla crosshair selection");
                restore(mc);
                RESULT.put("uiDisableRestoresVanilla", true);
                RESULT.put("completed", true); done = true; return true;
            }
            default -> throw new IllegalStateException("Unknown crosshair UI QA stage");
        }
        stage++; ticks = 0; return false;
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        Path game = mc.gameDir.toPath().toRealPath();
        require(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath().equals(game), "Crosshair QA must use its exact isolated game directory");
        begun = true; checks.put("crosshair034", RESULT); RESULT.put("screenshots", CAPTURES);
        require(mc.player.isAlive(), "Crosshair QA requires a live fixture player after earlier gameplay checks");
        RESULT.put("hudImplementation", mc.gui.getClass().getName());
        parent = mc.screen; custom = object(CUSTOM);
        require(Boolean.FALSE.equals(call(custom, "enabled")), "Crosshair UI QA requires a fresh disabled fixture");
        originalDocument = new Gson().fromJson(new Gson().toJson(staticField(CUSTOM, "data").get(null)), staticField(CUSTOM, "data").get(null).getClass());
        originalMesh = stat(CUSTOM, "mesh");
        file = ((Path) staticField(CUSTOM, "file").get(null)).toAbsolutePath().normalize(); backup(file, game);
        Object config = object("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig");
        Path folder = ((Path) call(stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active"), "getFolder")).toAbsolutePath().normalize();
        backup(folder.resolve((String) field(config, "id")), game);
        require(!Class.forName("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig").getField("disableCrosshair").getBoolean(null), "Fresh Crosshair Tweaks unexpectedly disables all crosshairs");
        shell = object("org.polyfrost.oneconfig.internal.ui.shell.ShellState"); search = (String) call(shell, "getSearchQuery");
        output = game.resolve("forge-qa"); Files.createDirectories(output);
        mouseX = Mouse.getX(); mouseY = Mouse.getY();
        openSettings();
    }
    private static void openSettings() throws Exception {
        call(shell, "setSearchQuery", "");
        Object route = Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute")
                .getConstructor(String.class, String.class).newInstance("crosshairtweaks.json", "Custom Crosshair");
        stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", route);
    }
    public static void afterFrame() {
        if (!begun || done) return;
        frames++;
        if (!pending || renderFailure != null) return;
        pending = false;
        try { RESULT.put(probe == 0 ? "presetPixelsWithCulling" : "canvasPixelsWithCulling", pixels(Minecraft.getInstance())); }
        catch (Throwable failure) { renderFailure = failure; }
    }
    private static Map<String, Object> pixels(Minecraft mc) throws Exception {
        Object state = Class.forName("org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState").getConstructor(int.class).newInstance(330);
        call(state, "capture");
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), fbo = GL30.glGenFramebuffers(), texture = GL11.glGenTextures();
        Window window = new Window(mc);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glOrtho(0, window.getScaledWidth(), window.getScaledHeight(), 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL20.glUseProgram(0); GlStateManager.bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, mc.width, mc.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo); GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Crosshair pixel framebuffer is incomplete");
            GL11.glViewport(0, 0, mc.width, mc.height); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.disableLighting(); GlStateManager.enableCull(); GlStateManager.enableDepthTest();
            GL11.glClearColor(3f / 255, 5f / 255, 7f / 255, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            require(Boolean.TRUE.equals(stat(RENDERER, "render")), "The design enabled in the editor was not renderable");
            boolean cullRestored = GL11.glIsEnabled(GL11.GL_CULL_FACE), depthRestored = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            require(cullRestored && depthRestored, "Custom crosshair drawing leaked culling or depth state");
            int size = 96;
            ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
            GL11.glReadPixels(mc.width / 2 - size / 2, mc.height / 2 - size / 2, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            int ink = 0;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int at = ((size - y - 1) * size + x) * 4;
                int rgb = (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255;
                if (rgb != 0x030507) ink++;
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
            String name = "crosshair034-pixels-" + probe + ".png"; ImageIO.write(image, "PNG", output.resolve(name).toFile()); CAPTURES.add("forge-qa/" + name);
            return Map.of("pixels", ink, "cullingRestored", cullRestored, "depthRestored", depthRestored, "frontFace", GL11.glGetInteger(GL11.GL_FRONT_FACE));
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(mode);
            call(state, "restore"); stat("org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLStateKt", "resyncTextureBindCache");
            GL30.glDeleteFramebuffers(fbo); GlStateManager.deleteTextures(texture);
        }
    }
    private static Object description(Object screen, String prefix) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Object value = properties(node).get("ContentDescription"); if (value != null && value.toString().contains(prefix)) return node;
        }
        throw new IllegalStateException("The canvas pointer target is missing");
    }
    private static void clickNth(Object screen, String label, int nth) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Map<String, Object> values = properties(node);
            if (!values.containsKey("OnClick") || !(values.get("Text") instanceof Collection<?> texts)) continue;
            for (Object text : texts) if (label.equals(call(text, "getText")) && nth-- == 0) {
                require(Boolean.TRUE.equals(call(call(values.get("OnClick"), "getAction"), "invoke")), "The preset Use control rejected a click"); return;
            }
        }
        throw new IllegalStateException("The preset Use control was not found");
    }
    private static JsonObject persisted() throws Exception { return new Gson().fromJson(Files.readString(file), JsonObject.class); }
    private static int count(String method) throws Exception { return ((Number) stat(RENDERER, method)).intValue(); }
    private static boolean naturalHud(Minecraft mc, String name) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("frames", frames - frameStart);
        state.put("renderCountBefore", draws); state.put("renderCountAfter", count("renderedCount"));
        state.put("directProbePending", pending); state.put("ticks", ticks);
        state.put("screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
        state.put("hideGui", mc.options.hideGui); state.put("perspective", mc.options.perspective);
        state.put("focused", mc.focused); state.put("paused", mc.isPaused());
        state.put("health", mc.player.getHealth()); state.put("alive", mc.player.isAlive());
        state.put("gameMode", mc.interactionManager.getGameMode().name());
        state.put("customActive", stat(CUSTOM, "isActive"));
        RESULT.put(name + "NaturalHud", state);
        require(mc.player.isAlive(), "The fixture player died before the natural custom crosshair render: " + state);
        if (ticks < 10 || pending || count("renderedCount") <= draws + 1) {
            require(ticks < 100, "The normal HUD did not submit the " + name + " enabled through its UI: " + state);
            return false;
        }
        return true;
    }
    private static void backup(Path path, Path game) throws Exception {
        path = path.toAbsolutePath().normalize(); require(path.startsWith(game), "Crosshair QA would modify a file outside the fixture");
        Path ancestor = path; while (!Files.exists(ancestor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        require(ancestor.toRealPath().startsWith(game), "Crosshair QA found a link outside the fixture");
        FILES.put(path, Files.isRegularFile(path) ? Files.readAllBytes(path) : null);
    }
    private static void restore(Minecraft mc) throws Exception {
        mc.openScreen(null); staticField(CUSTOM, "data").set(null, originalDocument); staticField(CUSTOM, "cachedMesh").set(null, originalMesh);
        call(shell, "setSearchQuery", search);
        for (var entry : FILES.entrySet()) if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue());
        Mouse.setCursorPosition(mouseX, mouseY); mc.openScreen(parent);
    }
    private static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); CAPTURES.add(name); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
