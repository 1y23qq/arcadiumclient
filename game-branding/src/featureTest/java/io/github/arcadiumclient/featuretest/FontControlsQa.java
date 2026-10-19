package io.github.arcadiumclient.featuretest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.text.LiteralText;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class FontControlsQa {
    private static final String CONFIG = "org.polyfrost.oneconfig.internal.OneConfigConfig";
    private static final String SKIA = "org.polyfrost.oneconfig.internal.ui.compose.SkiaFontRenderer";
    private static final String ATLAS = "org.polyfrost.oneconfig.internal.ui.compose.LegacyGameFont";
    private static final String CHAT_OVERLAY = "org.polyfrost.oneconfig.internal.ui.compose.OptionsChatOverlay";
    private static final String GL_STATE = "org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState";
    private static final String GL_CACHE = "org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLStateKt";
    private static final String PACK_NAME = "Arcadium Font Controls QA.zip";
    private static final String PACK_METADATA = "{\"pack\":{\"pack_format\":1,\"description\":\"Owned font metrics fixture\"}}";
    private static final String CHAT_TEXT = "FONT_QA_CHAT_033 " + Long.toUnsignedString(System.nanoTime(), 36) + " Red Bold 日本語";
    private static final int CHAT_ID = 330033;
    private static final String WIDTH_TEXT = "MMMMMMMM";
    private static final String FORMATTED = "§cRed §lBold§r Reset";
    private static final int WIDTH = 512, HEIGHT = 108;
    private static final int[][] MODES = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 0}, {0, 2}, {2, 2}, {2, 1}, {1, 2}};
    private static final String[] SOURCES = {"pack", "vanilla", "Inter"};
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(4);
    private static List<ResourcePacks.Entry> savedPacks;
    private static final List<ResourcePacks.Entry> ownedPackEntries = new ArrayList<>();
    private static Screen savedScreen, packScreen;
    private static Path fixture, output;
    private static Object config, skia, renderResult, shell;
    private static String savedSearch;
    private static PlayerEntity.ChatVisibility chatVisibility;
    private static float chatOpacity;
    private static boolean unicode, fontUnicode, begun, done, renderPending, chatObserved, fixtureOwned, searchEdited;
    private static int phase, ticks, mode, sgaWidth;
    private static int chatCalls, chatReplays, chatCaptures, textCalls, markerCalls, markerReplays, visibleLines;
    private static int nativeScroll, markerLine = -1;
    private static List<String> nativeLineSample = List.of();
    private static final List<String> replayedTextSample = new ArrayList<>();
    private static int[] chatBounds;
    private static double vanillaGame, vanillaMod, packGame, packMod, interGame = -1, interMod = -1;
    private static Throwable renderFailure;
    private static Object guiFamily;

    private FontControlsQa() {}

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        require(Boolean.getBoolean("arcadium.forgeQa") && Boolean.getBoolean("arcadium.release033Qa"), "Font QA was called outside its fixture");
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Font QA requires the isolated offline account");
        try {
            if (!begun) begin(mc, checks);
            if (renderFailure != null) throw new IllegalStateException("Font render probe failed", renderFailure);
            require(++ticks < 160, "Font QA timed out at phase " + phase + ", mode " + mode);
            switch (phase) {
                case 0 -> {
                    if (ticks < 12) return false;
                    packGame = gameWidth(mc, WIDTH_TEXT);
                    packMod = modWidth(WIDTH_TEXT);
                    require(packGame < vanillaGame && packMod < vanillaMod, "The real fixture font did not change game and mod metrics");
                    CHECKS.put("baselineWidths", Map.of("vanillaGame", vanillaGame, "vanillaMod", vanillaMod, "packGame", packGame, "packMod", packMod));
                    select(0);
                    phase = 1;
                    ticks = 0;
                }
                case 1 -> {
                    if (renderResult == null) return false;
                    @SuppressWarnings("unchecked") Map<String, Object> sample = (Map<String, Object>) renderResult;
                    verifySample(sample);
                    SAMPLES.add(sample);
                    renderResult = null;
                    if (++mode < MODES.length) {
                        select(mode);
                        ticks = 0;
                        return false;
                    }
                    verifyConflicts();
                    source(false, 0);
                    source(true, 0);
                    mc.openScreen(new ResourcePacksScreen(savedScreen));
                    packScreen = mc.screen;
                    require(packScreen != null && packScreen.getClass().getName().endsWith("ArcadiumResourcePacksScreen"), "Font switches are missing their Arcadium resource pack screen");
                    phase = 2;
                    ticks = 0;
                }
                case 2 -> {
                    if (ticks < 25) return false;
                    verifyPackSwitches();
                    screenshot(mc, "font-controls-pack-switches.png");
                    Object preferences = Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.PreferencesGraph").getField("INSTANCE").get(null);
                    staticCall("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", preferences);
                    mc.gui.getChat().resetScroll();
                    mc.gui.getChat().addMessage(new LiteralText(CHAT_TEXT), CHAT_ID);
                    phase = 3;
                    ticks = 0;
                }
                case 3 -> {
                    if (ticks % 10 == 0) chatDiagnostics(mc);
                    if (!searchEdited && ticks >= 8 && mc.screen != null && mc.screen.getClass().getName().contains("OneConfigUIScreen")) {
                        editSearch(mc.screen, "Use Inter");
                        searchEdited = true;
                        return false;
                    }
                    if (!chatObserved || ticks < 25) return false;
                    require(mc.screen != null && mc.screen.getClass().getName().contains("OneConfigUIScreen"), "Arcadium Options did not stay open for the chat probe");
                    require(visibleText(mc.screen, "Use Inter in-game") && visibleText(mc.screen, "Use Inter for mod text"), "Independent Inter controls are not visible in Preferences");
                    require(guiFamily == guiFamily(), "The GUI font family changed with game/mod font controls");
                    screenshot(mc, "font-controls-options-chat.png");
                    phase = 4;
                    ticks = 0;
                }
                case 4 -> {
                    Path screen = mc.gameDir.toPath().resolve("screenshots/font-controls-options-chat.png");
                    if (!Files.isRegularFile(screen)) return false;
                    BufferedImage image;
                    try { image = ImageIO.read(screen.toFile()); } catch (java.io.IOException incomplete) { return false; }
                    if (image == null) return false;
                    require(chatBounds != null && pixels(image, chatBounds[0], chatBounds[1], chatBounds[2], chatBounds[3], 0) > 8,
                        "The normal chat replay did not produce visible pixels above Options");
                    Files.copy(screen, output.resolve("options-chat.png"), StandardCopyOption.REPLACE_EXISTING);
                    CHECKS.put("optionsChat", Map.of("nativeReplayObserved", true, "pixelBounds", chatBounds, "visibleGlyphPixels", pixels(image, chatBounds[0], chatBounds[1], chatBounds[2], chatBounds[3], 0)));
                    restore(mc);
                    phase = 5;
                    ticks = 0;
                }
                case 5 -> {
                    if (ticks < 12) return false;
                    for (String flag : flags()) require(!flag(flag), "Font QA left an override enabled: " + flag);
                    require(mc.getResourcePacks().getApplied().equals(savedPacks), "Font QA did not restore the previous pack stack");
                    require(!Files.exists(fixture), "Font QA did not remove its owned pack");
                    CHECKS.put("restored", true);
                    CHECKS.put("completed", true);
                    done = true;
                    return true;
                }
                default -> throw new IllegalStateException("Unknown font QA phase");
            }
            return false;
        } catch (Exception failure) {
            try { restore(mc); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        begun = true;
        checks.put("fontControls", CHECKS);
        CHECKS.put("modes", SAMPLES);
        output = mc.gameDir.toPath().resolve("forge-qa/fonts");
        Files.createDirectories(output);
        savedScreen = mc.screen;
        savedPacks = List.copyOf(mc.getResourcePacks().getApplied());
        unicode = mc.options.forceUnicodeFont;
        fontUnicode = mc.textRenderer.getUnicode();
        chatOpacity = mc.options.chatOpacity;
        chatVisibility = mc.options.chatVisibility;
        mc.options.forceUnicodeFont = false;
        mc.textRenderer.setUnicode(false);
        mc.options.chatOpacity = 1f;
        mc.options.chatVisibility = PlayerEntity.ChatVisibility.FULL;
        config = Class.forName(CONFIG).getField("INSTANCE").get(null);
        skia = Class.forName(SKIA).getField("INSTANCE").get(null);
        for (String flag : flags()) require(!flag(flag), "A new font control is enabled by default: " + flag);
        CHECKS.put("defaultOff", true);
        guiFamily = guiFamily();
        shell = Class.forName("org.polyfrost.oneconfig.internal.ui.shell.ShellState").getField("INSTANCE").get(null);
        savedSearch = (String) invoke(shell, "getSearchQuery");
        sgaWidth = mc.enchantingPhraseRenderer.getWidth(WIDTH_TEXT);
        source(false, 1);
        source(true, 1);
        vanillaGame = gameWidth(mc, WIDTH_TEXT);
        vanillaMod = modWidth(WIDTH_TEXT);
        source(false, 0);
        source(true, 0);
        createPack(mc);
        var packs = mc.getResourcePacks();
        packs.load();
        var selected = new ArrayList<>(savedPacks);
        ResourcePacks.Entry owned = packs.getAvailable().stream().filter(pack -> pack.getName().equals(PACK_NAME)).findFirst().orElseThrow();
        ownedPackEntries.add(owned);
        selected.add(owned);
        packs.apply(selected);
        mc.reloadResources();
        mc.openScreen(null);
    }

    private static void select(int index) throws Exception {
        source(false, MODES[index][0]);
        source(true, MODES[index][1]);
        require(guiFamily == guiFamily(), "A font switch replaced the GUI's Inter font family");
        renderPending = true;
    }

    public static void afterFrame() {
        if (!begun || done || !renderPending || renderFailure != null) return;
        renderPending = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("game", SOURCES[MODES[mode][0]]);
            sample.put("mod", SOURCES[MODES[mode][1]]);
            sample.put("gameWidth", gameWidth(mc, WIDTH_TEXT));
            sample.put("modWidth", modWidth(WIDTH_TEXT));
            require(mc.enchantingPhraseRenderer.getWidth(WIDTH_TEXT) == sgaWidth, "The font switch changed the dedicated enchanting font");
            int nativePlain = mc.textRenderer.getWidth("MMMM");
            int nativeStyled = mc.textRenderer.getWidth("§lMM§rMM");
            require(nativeStyled == nativePlain + 2, "Native bold/reset measurement disagrees with its glyph advances");
            if (MODES[mode][0] != 0) require(mc.textRenderer.getWidth("§lM§cM") == mc.textRenderer.getWidth("MM") + 1,
                "Native color formatting did not reset bold measurement");
            require(Math.abs(modWidth("§lMM§rMM") - modWidth("MMMM") - 2) < 0.01,
                "Mod bold/reset measurement disagrees with its glyph advances");
            require(Math.abs(modWidth("§lM§cM") - modWidth("MM") - 1) < 0.01, "Mod color formatting did not reset bold measurement");
            require(gameWidth(mc, "日本語") > 0 && modWidth("日本語") > 0, "Unicode text has no measured advance");
            int before = ((Number) staticCall(ATLAS, "rasterizationCount")).intValue();
            BufferedImage nativeImage = nativeImage(mc);
            int warmed = ((Number) staticCall(ATLAS, "rasterizationCount")).intValue();
            nativeImage(mc);
            int repeated = ((Number) staticCall(ATLAS, "rasterizationCount")).intValue();
            require(warmed == repeated, "Native Inter glyphs were rasterized again on a repeated frame");
            sample.put("atlasRasterizations", List.of(before, warmed, repeated));
            sample.put("nativePixels", verifyPixels(nativeImage));
            ImageIO.write(nativeImage, "png", output.resolve(String.format("%02d-native-%s-%s.png", mode, SOURCES[MODES[mode][0]], SOURCES[MODES[mode][1]])).toFile());
            BufferedImage modImage = modImage();
            sample.put("modPixels", verifyPixels(modImage));
            ImageIO.write(modImage, "png", output.resolve(String.format("%02d-mod-%s-%s.png", mode, SOURCES[MODES[mode][0]], SOURCES[MODES[mode][1]])).toFile());
            sample.put("formattingMetrics", true);
            sample.put("unicode", true);
            sample.put("enchantingFontPreserved", true);
            renderResult = sample;
        } catch (Throwable failure) { renderFailure = new IllegalStateException("Font render mode " + Arrays.toString(MODES[mode]), failure); }
    }

    private static void verifySample(Map<String, Object> sample) {
        double game = ((Number) sample.get("gameWidth")).doubleValue();
        double mod = ((Number) sample.get("modWidth")).doubleValue();
        int gameSource = MODES[mode][0], modSource = MODES[mode][1];
        if (gameSource == 2 && interGame < 0) {
            interGame = game;
            require(interGame != packGame && interGame != vanillaGame, "The game Inter switch did not select Inter metrics");
        }
        if (modSource == 2 && interMod < 0) {
            interMod = mod;
            require(interMod != packMod && interMod != vanillaMod, "The mod Inter switch did not select Inter metrics");
        }
        double expectedGame = gameSource == 0 ? packGame : gameSource == 1 ? vanillaGame : interGame;
        double expectedMod = modSource == 0 ? packMod : modSource == 1 ? vanillaMod : interMod;
        require(Math.abs(game - expectedGame) < 0.01 && Math.abs(mod - expectedMod) < 0.01,
            "The game and mod font sources are not independent: " + sample);
    }

    private static BufferedImage nativeImage(Minecraft mc) throws Exception {
        Object state = Class.forName(GL_STATE).getConstructor(int.class).newInstance(330);
        invoke(state, "capture");
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int framebuffer = GL30.glGenFramebuffers();
        int texture = GL11.glGenTextures();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL20.glUseProgram(0);
            GlStateManager.bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, WIDTH, HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Native font probe framebuffer is incomplete");
            GL11.glViewport(0, 0, WIDTH, HEIGHT);
            GlStateManager.disableDepthTest();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableTexture();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(16f / 255f, 16f / 255f, 16f / 255f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            int end = mc.textRenderer.draw(WIDTH_TEXT, 8f, 8f, -1, false);
            int measured = mc.textRenderer.getWidth(WIDTH_TEXT);
            CHECKS.put("latestNativeDraw", Map.of("mode", Arrays.toString(MODES[mode]), "measured", measured, "drawnAdvance", end - 8, "drawReturn", end));
            require(end == 8 + measured, "Native measured width differs from the actual draw return position: mode="
                + Arrays.toString(MODES[mode]) + ", measured=" + measured + ", drawnAdvance=" + (end - 8) + ", drawReturn=" + end);
            mc.textRenderer.draw(FORMATTED, 8f, 26f, -1, false);
            mc.textRenderer.draw("Shadow", 8f, 44f, -1, true);
            mc.textRenderer.draw("日本語", 8f, 62f, -1, false);
            mc.textRenderer.draw("§oItalic §nLine", 8f, 80f, -1, false);
            ByteBuffer bytes = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
            GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                int offset = ((HEIGHT - y - 1) * WIDTH + x) * 4;
                image.setRGB(x, y, (bytes.get(offset + 3) & 255) << 24 | (bytes.get(offset) & 255) << 16
                    | (bytes.get(offset + 1) & 255) << 8 | bytes.get(offset + 2) & 255);
            }
            return image;
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            invoke(state, "restore");
            staticCall(GL_CACHE, "resyncTextureBindCache");
            GL30.glDeleteFramebuffers(framebuffer);
            GlStateManager.deleteTextures(texture);
        }
    }

    private static BufferedImage modImage() throws Exception {
        Object companion = Class.forName("org.jetbrains.skia.Surface").getField("Companion").get(null);
        Object surface = invoke(companion, "makeRasterN32Premul", WIDTH, HEIGHT);
        Object image = null, data = null;
        try {
            Object canvas = invoke(surface, "getCanvas");
            invoke(canvas, "clear", 0xff101010);
            drawMod(canvas, WIDTH_TEXT, 8f, 8f, false);
            drawMod(canvas, FORMATTED, 8f, 26f, false);
            drawMod(canvas, "Shadow", 8f, 44f, true);
            drawMod(canvas, "日本語", 8f, 62f, false);
            drawMod(canvas, "§oItalic §nLine", 8f, 80f, false);
            image = invoke(surface, "makeImageSnapshot");
            Object format = Class.forName("org.jetbrains.skia.EncodedImageFormat").getField("PNG").get(null);
            data = invoke(image, "encodeToData", format, 100, 0);
            require(data != null, "The mod font probe could not encode its real raster output");
            return ImageIO.read(new ByteArrayInputStream((byte[]) invoke(data, "getBytes")));
        } finally {
            if (data != null) invoke(data, "close");
            if (image != null) invoke(image, "close");
            invoke(surface, "close");
        }
    }

    private static void drawMod(Object canvas, String text, float x, float y, boolean shadow) throws Exception {
        invoke(skia, "draw", canvas, text, x, y, -1, shadow, 1f);
    }

    private static Map<String, Integer> verifyPixels(BufferedImage image) {
        int red = pixels(image, 8, 22, 380, 39, 1);
        int white = pixels(image, 8, 22, 380, 39, 0);
        int shadow = pixels(image, 8, 42, 180, 59, 2);
        int unicode = pixels(image, 8, 60, 180, 77, 0);
        int decorations = pixels(image, 8, 78, 200, 94, 0);
        require(red > 4 && white > 4, "Formatted red and reset-white glyphs are missing: " + red + "/" + white);
        require(shadow > 2, "The real shadow layer is missing");
        require(unicode > 8, "The real Unicode glyphs are missing");
        require(decorations > 8, "Italic/underline text did not render");
        return Map.of("red", red, "white", white, "shadow", shadow, "unicode", unicode, "decorations", decorations);
    }

    private static int pixels(BufferedImage image, int x0, int y0, int x1, int y1, int kind) {
        int count = 0;
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
                int color = image.getRGB(x, y), red = color >>> 16 & 255, green = color >>> 8 & 255, blue = color & 255;
                if (kind == 0 && red > 170 && green > 170 && blue > 170) count++;
                if (kind == 1 && red > 170 && green < 150 && blue < 150) count++;
                if (kind == 2 && red >= 35 && red <= 100 && Math.abs(red - green) < 3 && Math.abs(red - blue) < 3) count++;
            }
        }
        return count;
    }

    public static void text(String text, float x, float y) {
        if (!begun || done || phase != 3) return;
        textCalls++;
        try {
            if (text != null && replayedTextSample.size() < 12 && Boolean.TRUE.equals(staticCall(CHAT_OVERLAY, "isReplaying"))
                && !replayedTextSample.contains(text)) replayedTextSample.add(text);
        } catch (Throwable failure) { renderFailure = failure; }
        if (text == null || !text.contains("FONT_QA_CHAT_033")) return;
        markerCalls++;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null || !mc.screen.getClass().getName().contains("OneConfigUIScreen")) return;
            Field replaying = Class.forName(CHAT_OVERLAY).getDeclaredField("replaying");
            replaying.setAccessible(true);
            if (!replaying.getBoolean(null)) return;
            markerReplays++;
            MATRIX.clear(); PROJECTION.clear(); VIEWPORT.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
            float[] start = screenPoint(x, y, mc.height);
            float[] end = screenPoint(x + mc.textRenderer.getWidth(text) + 2, y + 11, mc.height);
            chatBounds = new int[] {(int) Math.floor(Math.min(start[0], end[0])), (int) Math.floor(Math.min(start[1], end[1])),
                (int) Math.ceil(Math.max(start[0], end[0])), (int) Math.ceil(Math.max(start[1], end[1]))};
            chatObserved = true;
        } catch (Throwable failure) { renderFailure = failure; }
    }

    public static boolean chatStart(int lineCount) {
        if (!begun || done || phase != 3) return false;
        try {
            visibleLines = lineCount;
            chatCalls++;
            boolean replay = Boolean.TRUE.equals(staticCall(CHAT_OVERLAY, "isReplaying"));
            if (replay) chatReplays++;
            return replay;
        } catch (Throwable failure) { renderFailure = failure; return false; }
    }

    public static void chatEnd(boolean replay) {
        if (!begun || done || phase != 3 || replay) return;
        try {
            if (field(Class.forName(CHAT_OVERLAY), "deferred").get(null) != null) chatCaptures++;
        } catch (Throwable failure) { renderFailure = failure; }
    }

    public static boolean sampleChatLines() { return begun && !done && phase == 3 && chatCalls % 100 == 1; }

    public static void chatLines(int scroll, int markerIndex, List<String> sample) {
        nativeScroll = scroll;
        markerLine = markerIndex;
        nativeLineSample = sample;
    }

    private static void chatDiagnostics(Minecraft mc) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("chatCalls", chatCalls); state.put("deferredCaptures", chatCaptures); state.put("replayCalls", chatReplays);
        state.put("fontDrawCalls", textCalls); state.put("markerDrawCalls", markerCalls); state.put("markerReplayDrawCalls", markerReplays);
        state.put("nativeVisibleLines", visibleLines); state.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        state.put("nativeScroll", nativeScroll); state.put("markerLine", markerLine); state.put("nativeLineSample", nativeLineSample);
        state.put("replayedTextSample", replayedTextSample);
        Class<?> overlay = Class.forName(CHAT_OVERLAY);
        state.put("deferred", field(overlay, "deferred").get(null) != null);
        state.put("replaying", field(overlay, "replaying").get(null));
        state.put("failed", field(overlay, "failed").get(null));
        Object ctx = Class.forName("org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx").getField("INSTANCE").get(null);
        state.put("skiaReady", invoke(ctx, "isReady"));
        state.put("composeActive", field(ctx.getClass(), "composeActive").get(null));
        Object hudManager = Class.forName("org.polyfrost.oneconfig.api.hud.v1.HudManager").getField("INSTANCE").get(null);
        state.put("hudEditingOutsideReplay", invoke(hudManager, "isEditing"));
        CHECKS.put("chatProbe", state);
    }

    private static float[] screenPoint(float x, float y, int height) {
        float[] point = new float[4], clip = new float[4];
        for (int row = 0; row < 4; row++) point[row] = MATRIX.get(row) * x + MATRIX.get(4 + row) * y + MATRIX.get(12 + row);
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) clip[row] += PROJECTION.get(column * 4 + row) * point[column];
        return new float[] {VIEWPORT.get(0) + (clip[0] / clip[3] + 1) * VIEWPORT.get(2) / 2f,
            height - (VIEWPORT.get(1) + (clip[1] / clip[3] + 1) * VIEWPORT.get(3) / 2f)};
    }

    private static void verifyConflicts() throws Exception {
        set("useVanillaFontInGame", true);
        set("useInterInGame", true);
        require(!flag("useVanillaFontInGame") && flag("useInterInGame"), "Inter did not replace vanilla for game text");
        set("useVanillaFontInGame", true);
        require(!flag("useInterInGame"), "Vanilla did not replace Inter for game text");
        set("useVanillaFontForModText", true);
        set("useInterForModText", true);
        require(!flag("useVanillaFontForModText") && flag("useInterForModText"), "Inter did not replace vanilla for mod text");
        set("useVanillaFontForModText", true);
        require(!flag("useInterForModText"), "Vanilla did not replace Inter for mod text");
        CHECKS.put("mutualExclusion", true);
        CHECKS.put("guiInterFamilyPreserved", true);
    }

    private static void verifyPackSwitches() throws Exception {
        for (String label : List.of("Use vanilla font in-game", "Use vanilla font for mod text")) {
            String field = label.endsWith("in-game") ? "useVanillaFontInGame" : "useVanillaFontForModText";
            clickText(packScreen, label);
            require(flag(field), "The visible resource pack switch did not enable its font preference: " + label);
            clickText(packScreen, label);
            require(!flag(field), "The visible resource pack switch did not turn off: " + label);
        }
        CHECKS.put("resourcePackSwitchesClicked", true);
    }

    private static void source(boolean mod, int source) throws Exception {
        set(mod ? "useInterForModText" : "useInterInGame", false);
        set(mod ? "useVanillaFontForModText" : "useVanillaFontInGame", source == 1);
        if (source == 2) set(mod ? "useInterForModText" : "useInterInGame", true);
    }

    private static void set(String name, boolean value) throws Exception { invoke(invoke(invoke(config, "getTree"), "getProp", name), "setAs", value); }
    private static boolean flag(String name) throws Exception { return Class.forName(CONFIG).getField(name).getBoolean(null); }
    private static List<String> flags() { return List.of("useInterInGame", "useInterForModText", "useVanillaFontInGame", "useVanillaFontForModText"); }
    private static double gameWidth(Minecraft mc, String text) { return mc.textRenderer.getWidth(text); }
    private static double modWidth(String text) throws Exception { return ((Number) invoke(skia, "measureWidth", text)).doubleValue(); }
    private static Object guiFamily() throws Exception {
        Object theme = staticCall("org.polyfrost.oneconfig.internal.ui.themes.ImplsKt", "getPolyGlassDark");
        return invoke(invoke(theme, "getTypography"), "getFamily");
    }

    private static void createPack(Minecraft mc) throws Exception {
        Path directory = mc.getResourcePacks().getDirectory().toPath().toAbsolutePath().normalize();
        fixture = directory.resolve(PACK_NAME);
        require(fixture.getParent().equals(directory), "Font QA fixture is outside the pack directory");
        BufferedImage image;
        try (var input = mc.getResourcePacks().defaultPack.getResource(new Identifier("minecraft", "textures/font/ascii.png"))) { image = ImageIO.read(input); }
        int cellWidth = image.getWidth() / 16, cellHeight = image.getHeight() / 16;
        int left = ('M' % 16) * cellWidth, top = ('M' / 16) * cellHeight;
        for (int y = 0; y < cellHeight; y++) for (int x = 0; x < cellWidth; x++) image.setRGB(left + x, top + y, x == 0 && y > 0 && y < cellHeight - 1 ? -1 : 0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        if (Files.exists(fixture)) {
            require(mc.getResourcePacks().getApplied().stream().noneMatch(pack -> pack.getName().equals(PACK_NAME)), "A stale font fixture is still selected");
            try (var zip = new ZipFile(fixture.toFile())) {
                require(zip.size() == 2 && zip.getEntry("pack.mcmeta") != null && zip.getEntry("assets/minecraft/textures/font/ascii.png") != null,
                    "An existing pack does not match the owned font fixture");
                try (var metadata = zip.getInputStream(zip.getEntry("pack.mcmeta"));
                        var texture = zip.getInputStream(zip.getEntry("assets/minecraft/textures/font/ascii.png"))) {
                    require(new String(metadata.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).equals(PACK_METADATA)
                        && Arrays.equals(texture.readAllBytes(), bytes.toByteArray()), "An existing font pack differs from the owned fixture");
                }
            }
            closeOwnedEntries(mc);
            Files.delete(fixture);
            CHECKS.put("staleOwnedPackRemoved", true);
        }
        var stream = Files.newOutputStream(fixture, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        fixtureOwned = true;
        try (var zip = new ZipOutputStream(stream)) {
            for (var entry : Map.of("pack.mcmeta", PACK_METADATA.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "assets/minecraft/textures/font/ascii.png", bytes.toByteArray()).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
    }

    private static void restore(Minecraft mc) throws Exception {
        renderPending = false;
        if (config != null) {
            for (String flag : flags()) set(flag, false);
            invoke(config, "save");
        }
        mc.options.forceUnicodeFont = unicode;
        mc.textRenderer.setUnicode(fontUnicode);
        mc.options.chatOpacity = chatOpacity;
        if (chatVisibility != null) mc.options.chatVisibility = chatVisibility;
        mc.gui.getChat().removeMessage(CHAT_ID);
        if (savedPacks != null && !mc.getResourcePacks().getApplied().equals(savedPacks)) {
            mc.getResourcePacks().apply(savedPacks);
            mc.reloadResources();
        }
        if (shell != null && savedSearch != null) invoke(shell, "setSearchQuery", savedSearch);
        if (mc.screen == packScreen || mc.screen != null && mc.screen.getClass().getName().contains("OneConfigUIScreen")) mc.openScreen(savedScreen);
        if (fixtureOwned) {
            closeOwnedEntries(mc);
            Files.deleteIfExists(fixture);
        }
    }

    private static void closeOwnedEntries(Minecraft mc) throws Exception {
        List<ResourcePacks.Entry> entries = new ArrayList<>(ownedPackEntries);
        entries.addAll(mc.getResourcePacks().getAvailable());
        if (packScreen != null) {
            for (Object view : (Collection<?>) invoke(packScreen, "getPacks")) entries.add((ResourcePacks.Entry) invoke(view, "getEntry"));
        }
        var closed = new IdentityHashMap<ResourcePacks.Entry, Boolean>();
        for (ResourcePacks.Entry entry : entries) {
            if (PACK_NAME.equals(entry.getName()) && closed.put(entry, Boolean.TRUE) == null) entry.close();
        }
    }

    private static Object semanticsRoot(Object screen) throws Exception {
        Field scene = field(screen.getClass(), "sceneOrNull");
        Object value = scene.get(screen);
        require(value != null, "The font controls have no rendered Compose scene");
        Object owner = field(value.getClass(), "mainOwner").get(value);
        return invoke(invoke(owner, "getSemanticsOwner"), "getRootSemanticsNode");
    }

    private static Object textNode(Object screen, String text, boolean clickable) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Map<String, Object> properties = properties(node);
            if (clickable && !properties.containsKey("OnClick")) continue;
            if (properties.get("Text") instanceof Collection<?> pieces) {
                for (Object piece : pieces) if (text.equals(invoke(piece, "getText"))) return node;
            }
        }
        return null;
    }

    private static boolean visibleText(Object screen, String text) throws Exception { return textNode(screen, text, false) != null; }
    private static void editSearch(Object screen, String text) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Object entry = properties(node).get("SetText");
            if (entry == null) continue;
            Object value = Class.forName("androidx.compose.ui.text.AnnotatedString")
                .getConstructor(String.class, List.class, List.class).newInstance(text, List.of(), List.of());
            require(Boolean.TRUE.equals(invoke(invoke(entry, "getAction"), "invoke", value)), "The visible Preferences search rejected input");
            CHECKS.put("preferencesSearchEdited", true);
            return;
        }
        throw new IllegalStateException("The visible Preferences search input was not found");
    }
    private static void clickText(Object screen, String text) throws Exception {
        Object node = textNode(screen, text, true);
        require(node != null, "The visible font switch was not found: " + text);
        Object action = invoke(properties(node).get("OnClick"), "getAction");
        require(Boolean.TRUE.equals(invoke(action, "invoke")), "The font switch did not accept a click: " + text);
    }
    private static List<Object> nodes(Object node) throws Exception {
        List<Object> out = new ArrayList<>(); out.add(node);
        for (Object child : (Collection<?>) invoke(node, "getChildren")) out.addAll(nodes(child));
        return out;
    }
    private static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Object value : (Iterable<?>) invoke(node, "getConfig")) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
            values.put((String) invoke(entry.getKey(), "getName"), entry.getValue());
        }
        return values;
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Object staticCall(String type, String method, Object... args) throws Exception { return invoke(Class.forName(type), method, args); }
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Class<?> type = target instanceof Class<?> value ? value : target.getClass();
        List<Method> candidates = new ArrayList<>(Arrays.asList(type.getMethods()));
        for (Class<?> current = type; current != null; current = current.getSuperclass()) candidates.addAll(Arrays.asList(current.getDeclaredMethods()));
        for (Method method : candidates) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean compatible = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> parameter = method.getParameterTypes()[i];
                if (parameter.isPrimitive()) parameter = parameter == boolean.class ? Boolean.class : parameter == int.class ? Integer.class : parameter == float.class ? Float.class : parameter == long.class ? Long.class : parameter;
                if (args[i] != null && !parameter.isInstance(args[i])) compatible = false;
            }
            if (!compatible) continue;
            method.setAccessible(true);
            try { return method.invoke(target instanceof Class<?> ? null : target, args); }
            catch (InvocationTargetException failure) { if (failure.getCause() instanceof Exception cause) throw cause; throw failure; }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + args.length);
    }
    private static void screenshot(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
