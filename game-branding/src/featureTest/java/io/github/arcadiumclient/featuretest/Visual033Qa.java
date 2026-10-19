package io.github.arcadiumclient.featuretest;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class Visual033Qa {
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String SKINS = "io.github.arcadiumclient.runtime.client.skins.";
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final Map<Field, Object> STATIC_STATE = new LinkedHashMap<>();
    private static final Map<Method, Object> CONFIG_STATE = new LinkedHashMap<>();
    private static final Map<Path, byte[]> FILES = new LinkedHashMap<>();
    private static final Map<String, Object> CANVAS_BOUNDS = new LinkedHashMap<>();
    private static final List<RemoteClientPlayerEntity> PEERS = new ArrayList<>();
    private static Object custom, library, skinChanger, editor, skinScreen, firstSkin, secondSkin;
    private static Object originalDocument, originalMesh, originalIndex;
    private static Screen parent, mainMenu, pause;
    private static net.minecraft.client.world.ClientWorld world;
    private static int stage, ticks, width, height, guiScale, perspective, customDraws, meshBuilds, killDraws, kills, apiRequests, originalSkins, canvasStep;
    private static boolean begun, done, hideGui, canvasChosen;
    private static String probe;
    private static Throwable renderFailure;
    private static byte[] skinPng;

    private Visual033Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.release033Qa") || done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
                "Visual QA requires the isolated offline account");
        if (!begun) begin(mc, checks);
        if (renderFailure != null) throw new IllegalStateException("The visual render-state probe failed", renderFailure);
        require(++ticks < 160, "Visual QA timed out at stage " + stage);
        switch (stage) {
            case 0 -> {
                if (ticks < 10 || probe != null) return false;
                require(number(FEATURES + "CustomCrosshairRenderer", "renderedCount") > customDraws + 1,
                        "The real HUD did not render the custom crosshair");
                require(number(FEATURES + "CustomCrosshair", "meshBuildCount") == meshBuilds,
                        "The custom crosshair rebuilt its mesh during unchanged world frames");
                RESULTS.put("actualCrosshairDraws", number(FEATURES + "CustomCrosshairRenderer", "renderedCount") - customDraws);
                RESULTS.put("meshUnchangedAcrossWorldFrames", true);
                openCrosshairEditor();
            }
            case 1 -> {
                if (ticks < 10) return false;
                editor = mc.screen;
                require(editor != null && editor.getClass().getSimpleName().equals("CustomCrosshairScreen"), "Crosshair Tweaks did not open its editor");
                RESULTS.put("customEditorBounds", bounds(mc, List.of("Custom", "Presets", "Canvas", "Save profile", "Done")));
                capture(mc, "visual033-crosshair-custom.png");
                click(editor, "Presets");
            }
            case 2 -> {
                if (ticks < 6) return false;
                List<?> presets = (List<?>) stat(FEATURES + "CrosshairDesign", "presets");
                require(presets.size() >= 6, "The crosshair presets are missing");
                for (Object preset : presets) require(!Boolean.TRUE.equals(call(call(preset, "mesh"), "isEmpty")), "A preset has no drawable geometry");
                require(hasText(editor, "Classic") && hasText(editor, "Compact") && hasText(editor, "Ring"), "The preset controls did not render");
                RESULTS.put("presetCount", presets.size());
                capture(mc, "visual033-crosshair-presets.png");
                click(editor, "Canvas");
            }
            case 3 -> {
                if (ticks < 6) return false;
                require(hasDescription(editor, "Pixel crosshair canvas."), "The pixel canvas is not exposed as an editable control");
                if (canvasStep < 3) {
                    int dimension = new int[]{16, 32, 64}[canvasStep];
                    if (!canvasChosen) {
                        click(editor, dimension + "px");
                        canvasChosen = true;
                        ticks = 0;
                        return false;
                    }
                    Object design = call(editor, "getDraft");
                    require(field(design, "canvasSize").equals(dimension), "The canvas size control did not update the design");
                    call(design, "paint", dimension / 2 - 1, dimension / 2 - 1, 0xFFFFFFFF, true, true);
                    int[] pixels = (int[]) field(design, "pixels");
                    require(pixels.length == dimension * dimension && pixels[(dimension / 2) * dimension + dimension / 2] == 0xFFFFFFFF,
                            "The canvas size or mirrored center is incorrect");
                    Object mesh = call(design, "mesh");
                    require(!Boolean.TRUE.equals(call(mesh, "isEmpty")) && ((Collection<?>) call(mesh, "quads")).size() < 9000,
                            "The canvas did not create bounded geometry");
                    CANVAS_BOUNDS.put(dimension + "px", rectangle(descriptionNode(editor, "Pixel crosshair canvas."), mc, "Canvas " + dimension));
                    canvasStep++;
                    canvasChosen = false;
                    ticks = 0;
                    return false;
                }
                click(editor, "32px");
                click(editor, "Mirror X");
                click(editor, "Mirror Y");
                require(Boolean.TRUE.equals(call(editor, "getMirrorX")) && Boolean.TRUE.equals(call(editor, "getMirrorY")), "The mirror controls did not toggle");
                call(editor, "checkpoint");
                Object design = call(call(editor, "getDraft"), "copy");
                Arrays.fill((int[]) field(design, "pixels"), 0);
                for (int i = 5; i <= 11; i++) {
                    call(design, "paint", 15, i, 0xFFFFFFFF, true, true);
                    call(design, "paint", i, 15, 0xFFFFFFFF, true, true);
                }
                call(editor, "setDraft", design);
                call(custom, "update", design);
                setText(editor, "V033 Crosshair");
                click(editor, "Save profile");
                RESULTS.put("canvasDimensions", List.of(16, 32, 64));
                RESULTS.put("canvasModeBounds", CANVAS_BOUNDS);
                RESULTS.put("mirroredCanvasPixels", true);
            }
            case 4 -> {
                if (ticks < 6) return false;
                require(hasText(editor, "V033 Crosshair"), "The saved crosshair did not appear in the editor");
                capture(mc, "visual033-crosshair-canvas.png");
                click(editor, "New");
                click(editor, "V033 Crosshair");
                require(((Number) field(call(editor, "getDraft"), "mode")).intValue() == 1, "Loading a saved canvas selected the wrong mode");
                click(editor, "Delete");
                require(((Collection<?>) call(custom, "profiles")).stream().noneMatch(value -> "V033 Crosshair".equals(uncheckedField(value, "name"))),
                        "Deleting a saved crosshair failed");
                Object empty = Class.forName(FEATURES + "CrosshairDesign").getConstructor().newInstance();
                set(empty, "mode", 1);
                call(custom, "update", empty);
                require(Boolean.FALSE.equals(stat(FEATURES + "CustomCrosshair", "isActive")) &&
                        Boolean.FALSE.equals(stat(FEATURES + "CustomCrosshairRenderer", "render")), "An empty custom canvas hid the original crosshair");
                Object preset = ((List<?>) stat(FEATURES + "CrosshairDesign", "presets")).get(0);
                call(custom, "update", preset);
                call(custom, "setEnabled", true);
                staticField("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig", "disableCrosshair").setBoolean(null, true);
                mc.openScreen(null);
                customDraws = number(FEATURES + "CustomCrosshairRenderer", "renderedCount");
                RESULTS.put("profileSaveLoadDelete", true);
                RESULTS.put("emptyCanvasVanillaFallback", true);
            }
            case 5 -> {
                if (ticks < 6) return false;
                require(number(FEATURES + "CustomCrosshairRenderer", "renderedCount") == customDraws, "The original Disable Crosshair option was bypassed");
                staticField("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig", "disableCrosshair").setBoolean(null, false);
                stat(FEATURES + "GuiScaleConfig", "setCrosshairScale", 2);
                stat(FEATURES + "GuiScaleConfig", "setCrosshairAdjustment", 0.25f);
                mc.options.guiScale = 1;
                probe = "crosshair-scaled";
                RESULTS.put("existingCrosshairVisibilityPreserved", true);
            }
            case 6 -> {
                if (ticks < 8 || probe != null) return false;
                require(number(FEATURES + "CustomCrosshairRenderer", "renderedCount") > customDraws + 1, "GUI scaling suppressed the custom crosshair");
                Display.setDisplayMode(new DisplayMode(854, 480));
            }
            case 7 -> {
                if (ticks < 8 || mc.width != 854 || mc.height != 480) return false;
                openCrosshairEditor();
            }
            case 8 -> {
                if (ticks < 10) return false;
                editor = mc.screen;
                RESULTS.put("smallEditorBounds", bounds(mc, List.of("Custom", "Presets", "Canvas", "Save profile", "Done")));
                capture(mc, "visual033-crosshair-small.png");
                mc.openScreen(null);
                Display.setDisplayMode(new DisplayMode(width, height));
                mc.options.guiScale = guiScale;
                call(custom, "setEnabled", false);
            }
            case 9 -> {
                if (ticks < 8 || mc.width != width || mc.height != height) return false;
                call(skinChanger, "refresh", mc.getSession().getProfile().getId(), skinPng, "slim");
                stat(FEATURES + "Skin3DConfig", "chooseEnabled", true);
                mc.options.perspective = 1;
            }
            case 10 -> {
                if (ticks < 14) return false;
                Object texture = stat(SKINS + "SkinChanger", "textureFor", mc.getSession().getProfile().getId());
                require(texture != null && texture.equals(mc.player.getSkinTextureLocation()) && "slim".equals(mc.player.getModelType()),
                        "The applied local skin texture or slim model did not reach the real player");
                RESULTS.put("actualVoxelParts", actualVoxelParts(texture));
                RESULTS.put("localTextureRefreshSimulatedWithoutUpload", true);
                capture(mc, "visual033-skin-3d.png");
                mc.options.perspective = 0;
                stat(FEATURES + "KillEffectsConfig", "setEnabled", true);
                stat(FEATURES + "KillEffectsConfig", "setPlayersOnly", true);
                stat(FEATURES + "KillEffectsConfig", "setSound", 0);
                stat(FEATURES + "KillEffectsConfig", "setLifetime", 1f);
                stat(FEATURES + "KillEffectsConfig", "setCount", 16);
                kills = number(FEATURES + "KillEffects", "confirmedCount");
                killDraws = number(FEATURES + "KillEffects", "renderedCount");
                for (int effect = 0; effect < 5; effect++) {
                    stat(FEATURES + "KillEffectsConfig", "setEffect", effect);
                    RemoteClientPlayerEntity peer = peer(mc, effect);
                    stat(FEATURES + "KillEffects", "attack", mc.player, peer);
                    peer.doEvent((byte) 3);
                    peer.doEvent((byte) 3);
                }
                require(number(FEATURES + "KillEffects", "confirmedCount") == kills + 5, "Confirmed kills were missing or repeated");
                require(number(FEATURES + "KillEffects", "activeCount") == 5, "The five configured kill effects were not created");
                probe = "kill-effects";
            }
            case 11 -> {
                if (ticks < 5 || probe != null) return false;
                require(number(FEATURES + "KillEffects", "renderedCount") > killDraws + 1, "The actual world renderer did not draw kill effects");
                capture(mc, "visual033-kill-effects.png");
                RESULTS.put("confirmedKillEffects", 5);
                RESULTS.put("duplicateDeathSuppressed", true);
                RESULTS.put("actualKillEffectDraws", number(FEATURES + "KillEffects", "renderedCount") - killDraws);
            }
            case 12 -> {
                if (ticks < 24) return false;
                require(number(FEATURES + "KillEffects", "activeCount") == 0, "Expired kill effects remained active");
                for (int target = 20; target < 60; target++) stat(FEATURES + "KillEffects", "attack", mc.player, peer(mc, target));
                require(number(FEATURES + "KillEffects", "trackedCount") == 32, "Tracked kill targets did not remain bounded");
                RESULTS.put("boundedTrackedTargets", 32);
                stat(FEATURES + "KillEffects", "synchronizeWorld", (Object) null);
                require(number(FEATURES + "KillEffects", "trackedCount") == 0 && number(FEATURES + "KillEffects", "activeCount") == 0,
                        "A world change retained targets or effects");
                stat(FEATURES + "KillEffectsConfig", "setEnabled", false);
                for (RemoteClientPlayerEntity peer : PEERS) mc.world.removeEntity(peer);
                RESULTS.put("effectExpiryAndWorldCleanup", true);
                mainMenu = (Screen) Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu").getConstructor().newInstance();
                mc.openScreen(mainMenu);
            }
            case 13 -> {
                if (ticks < 10) return false;
                require(hasText(mainMenu, "Skins"), "The Arcadium main menu has no Skins entry");
                RESULTS.put("mainMenuSkinsBounds", bounds(mc, List.of("Skins")));
                capture(mc, "visual033-skins-menu-entry.png");
                click(mainMenu, "Skins");
            }
            case 14 -> {
                if (ticks < 8 || mc.screen == mainMenu) return false;
                skinScreen = mc.screen;
                require(skinScreen.getClass().getSimpleName().equals("SkinChangerScreen"), "The menu did not open Skin Changer");
                if (call(skinScreen, "getBusy") != null) return false;
                if (!canClick(skinScreen, "V033 Classic") || !hasText(skinScreen, "V033 Slim")) return false;
                RESULTS.put("skinUiBounds", bounds(mc, List.of("Skins", "Import PNG", "Classic", "Slim", "Save", "Delete", "Apply to ArcadiumTest", "Done")));
                click(skinScreen, "V033 Classic");
            }
            case 15 -> {
                if (ticks < 4) return false;
                require(field(firstSkin, "id").equals(call(skinScreen, "getSelected")), "The saved skin selector did not select the requested skin");
                setText(skinScreen, "V033 Renamed");
                click(skinScreen, "Slim");
                click(skinScreen, "Save");
            }
            case 16 -> {
                if (ticks < 5 || call(skinScreen, "getBusy") != null) return false;
                if (!canClick(skinScreen, "Apply to ArcadiumTest")) return false;
                Object saved = ((Collection<?>) call(library, "list")).stream()
                        .filter(value -> uncheckedField(firstSkin, "id").equals(uncheckedField(value, "id"))).findFirst().orElseThrow();
                require("V033 Renamed".equals(field(saved, "name")) && "slim".equals(field(saved, "variant")), "Skin rename or model persistence failed");
                capture(mc, "visual033-skins-manager.png");
                click(skinScreen, "Apply to ArcadiumTest");
                Object error = call(skinScreen, "getError");
                require(error != null && error.toString().contains("Sign in"), "An offline skin apply was not rejected before HTTP");
                require(number(SKINS + "SkinUploadClient", "requestCount") == apiRequests, "Offline UI QA attempted a real Minecraft API call");
                RESULTS.put("offlineApplyBlockedBeforeHttp", true);
                RESULTS.put("skinSelectionRenameModel", true);
                click(skinScreen, "Delete");
            }
            case 17 -> {
                if (ticks < 5 || call(skinScreen, "getBusy") != null) return false;
                if (!canClick(skinScreen, "V033 Slim") || !canClick(skinScreen, "Delete")) return false;
                require(((Collection<?>) call(library, "list")).size() == originalSkins + 1, "The skin Delete action did not update the library");
                click(skinScreen, "V033 Slim");
                click(skinScreen, "Delete");
            }
            case 18 -> {
                if (ticks < 5 || call(skinScreen, "getBusy") != null) return false;
                require(((Collection<?>) call(library, "list")).size() == originalSkins, "The skin library did not return to its original size");
                RESULTS.put("savedSkinCrud", true);
                pause = new GameMenuScreen();
                mc.openScreen(pause);
            }
            case 19 -> {
                if (ticks < 6) return false;
                var access = (ScreenProbeAccess) pause;
                var button = access.arcadium$buttons().stream().filter(value -> "Skins".equals(value.message)).findFirst().orElseThrow();
                require(button.visible && button.active && button.y >= 0 && button.y + 20 <= pause.height, "The pause-menu Skins entry is clipped or disabled");
                capture(mc, "visual033-skins-pause-entry.png");
                access.arcadium$click(button.x + button.getWidth() / 2, button.y + 10, 0);
            }
            case 20 -> {
                if (ticks < 8 || mc.screen == pause) return false;
                skinScreen = mc.screen;
                require(skinScreen.getClass().getSimpleName().equals("SkinChangerScreen"), "The pause entry did not open Skin Changer");
                if (call(skinScreen, "getBusy") != null) return false;
                require(mc.world == world, "Opening Skin Changer detached the fixture world");
                click(skinScreen, "Done");
                RESULTS.put("pauseMenuEntry", true);
            }
            case 21 -> {
                if (ticks < 4 || mc.screen != pause) return false;
                require(number(SKINS + "SkinUploadClient", "requestCount") == apiRequests, "Skin UI QA made an external API request");
                restore(mc);
                RESULTS.put("zeroExternalSkinRequests", true);
                RESULTS.put("fixtureSettingsRestored", true);
                RESULTS.put("completed", true);
                done = true;
                return true;
            }
            default -> throw new IllegalStateException("Unknown visual QA stage");
        }
        stage++;
        ticks = 0;
        return false;
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        Path game = mc.gameDir.toPath().toRealPath();
        require(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath().equals(game), "Visual QA must use the owned fixture directory");
        require(mc.world != null && mc.player != null, "Visual QA requires the loaded fixture world");
        begun = true;
        checks.put("visual033", RESULTS);
        RESULTS.put("screenshots", CAPTURES);
        RESULTS.put("hudImplementation", mc.gui.getClass().getName());
        width = mc.width; height = mc.height; guiScale = mc.options.guiScale; perspective = mc.options.perspective; hideGui = mc.options.hideGui;
        parent = mc.screen; world = mc.world;
        Object manager = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
        Path configFolder = ((Path) call(manager, "getFolder")).toAbsolutePath().normalize();
        require(configFolder.startsWith(game), "Visual QA would modify a config outside the fixture");
        for (String name : List.of("Skin3DConfig", "KillEffectsConfig", "GuiScaleConfig")) snapshotConfig(FEATURES + name, configFolder);
        snapshotConfig("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig", configFolder);
        require(Boolean.FALSE.equals(stat(FEATURES + "Skin3DConfig", "getEnabled")), "3D Skin did not default to off");
        Object skinConfig = call(object("org.polyfrost.skinlayers.SkinLayers"), "getConfig");
        backup(game.resolve("config/skinlayers.json"), game);
        for (String part : List.of("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg"))
            require(Boolean.TRUE.equals(stat(FEATURES + "Skin3DConfig", "get" + part)), "A 3D Skin body part did not default to selected");
        for (String part : List.of("Hat", "Jacket", "LeftSleeve", "RightSleeve", "LeftPants", "RightPants"))
            require(Boolean.FALSE.equals(call(skinConfig, "getEnable" + part)), "The real 3D renderer remained enabled by default");
        stat(FEATURES + "Skin3DConfig", "chooseEnabled", true);
        for (String part : List.of("Hat", "Jacket", "LeftSleeve", "RightSleeve", "LeftPants", "RightPants"))
            require(Boolean.TRUE.equals(call(skinConfig, "getEnable" + part)), "A body part was not enabled in the real 3D renderer");
        stat(FEATURES + "Skin3DConfig", "setBody", false);
        stat(FEATURES + "Skin3DConfig", "chooseEnabled", true);
        require(Boolean.FALSE.equals(call(skinConfig, "getEnableJacket")) && Boolean.TRUE.equals(call(skinConfig, "getEnableHat")), "3D Skin body controls are not independent");
        stat(FEATURES + "Skin3DConfig", "setBody", true);
        stat(FEATURES + "Skin3DConfig", "chooseEnabled", true);
        RESULTS.put("skin3dDefaultOff", true);
        RESULTS.put("actualSkinPartSwitches", 6);
        RESULTS.put("singlePixelVoxel", voxelGeometry());
        custom = object(FEATURES + "CustomCrosshair");
        originalDocument = staticField(FEATURES + "CustomCrosshair", "data").get(null);
        originalMesh = staticField(FEATURES + "CustomCrosshair", "cachedMesh").get(null);
        Object scratch = new Gson().fromJson(new Gson().toJson(originalDocument), originalDocument.getClass());
        staticField(FEATURES + "CustomCrosshair", "data").set(null, scratch);
        backup(((Path) staticField(FEATURES + "CustomCrosshair", "file").get(null)).toAbsolutePath().normalize(), game);
        Object design = ((List<?>) stat(FEATURES + "CrosshairDesign", "presets")).get(1);
        call(custom, "update", design);
        call(custom, "setEnabled", true);
        stat(FEATURES + "GuiScaleConfig", "setEnabled", true);
        stat(FEATURES + "GuiScaleConfig", "setCrosshairScale", 1);
        staticField("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig", "disableCrosshair").setBoolean(null, false);
        staticField("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig", "crosshairOpacityFirstPerson").setFloat(null, 100f);
        customDraws = number(FEATURES + "CustomCrosshairRenderer", "renderedCount");
        meshBuilds = number(FEATURES + "CustomCrosshair", "meshBuildCount");
        skinChanger = object(SKINS + "SkinChanger");
        for (String field : List.of("texture", "account", "slim")) saveStatic(SKINS + "SkinChanger", field);
        staticField(SKINS + "SkinChanger", "texture").set(null, null);
        library = call(skinChanger, "getLibrary");
        Path skinRoot = ((Path) field(library, "root")).toAbsolutePath().normalize();
        require(skinRoot.startsWith(game) && skinRoot.equals(FabricLoader.getInstance().getConfigDir().resolve("arcadium/skins").toAbsolutePath().normalize()),
                "The skin library is outside its fixture config directory");
        originalSkins = ((Collection<?>) call(library, "list")).size();
        originalIndex = field(library, "index");
        set(library, "index", new Gson().fromJson(new Gson().toJson(originalIndex), originalIndex.getClass()));
        backup(skinRoot.resolve("index.json"), game);
        skinPng = skinImage();
        Path png = game.resolve("forge-qa/visual033-owned-skin.png");
        Files.createDirectories(png.getParent());
        require(!Files.exists(png), "The visual skin fixture already exists");
        Files.write(png, skinPng);
        firstSkin = call(library, "add", png, "V033 Classic", "classic");
        secondSkin = call(library, "add", png, "V033 Slim", "slim");
        RESULTS.put("ownedPngImports", 2);
        apiRequests = number(SKINS + "SkinUploadClient", "requestCount");
        mc.options.hideGui = false;
        mc.options.perspective = 0;
        mc.openScreen(null);
        probe = "crosshair";
    }

    public static void afterRender() {
        if (!begun || done || probe == null || renderFailure != null) return;
        GlSnapshot original = GlSnapshot.read();
        String requested = probe;
        try {
            GlStateManager.color4f(0.35f, 0.55f, 0.75f, 0.65f);
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlSnapshot before = GlSnapshot.read();
            if (requested.startsWith("crosshair")) stat(FEATURES + "CustomCrosshairRenderer", "render");
            else stat(FEATURES + "KillEffects", "render", 0f);
            GlSnapshot after = GlSnapshot.read();
            require(before.same(after), "GL state leaked from " + requested + ": " + before.differences(after));
            RESULTS.put(requested + "GlStateRestored", true);
        } catch (Throwable failure) { renderFailure = failure; }
        finally { original.restore(); probe = null; }
    }

    private static final class GlSnapshot {
        final int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), modelDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH), projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
        final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        final float[] color = floats(GL11.GL_CURRENT_COLOR, 4), model = floats(GL11.GL_MODELVIEW_MATRIX, 16), projection = floats(GL11.GL_PROJECTION_MATRIX, 16);
        static GlSnapshot read() { return new GlSnapshot(); }
        boolean same(GlSnapshot other) { return differences(other).isEmpty(); }
        List<String> differences(GlSnapshot other) {
            ArrayList<String> result = new ArrayList<>();
            if (mode != other.mode || modelDepth != other.modelDepth || projectionDepth != other.projectionDepth || !near(model, other.model) || !near(projection, other.projection)) result.add("matrices");
            if (src != other.src || dst != other.dst || srcAlpha != other.srcAlpha || dstAlpha != other.dstAlpha || blend != other.blend) result.add("blending");
            if (texture != other.texture || binding != other.binding || lighting != other.lighting || cull != other.cull || alpha != other.alpha || mask != other.mask) result.add("flags");
            if (!near(color, other.color)) result.add("color");
            if (Math.abs(lineWidth - other.lineWidth) > 0.001f) result.add("lineWidth");
            return result;
        }
        void restore() {
            GlStateManager.color4f(color[0], color[1], color[2], color[3]);
            GlStateManager.blendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        }
        private static float[] floats(int name, int count) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(name, buffer);
            float[] result = new float[count];
            for (int i = 0; i < count; i++) result[i] = buffer.get(i);
            return result;
        }
        private static boolean near(float[] first, float[] second) {
            for (int i = 0; i < first.length; i++) if (Math.abs(first[i] - second[i]) > 0.0001f) return false;
            return true;
        }
    }

    private static Map<String, Object> voxelGeometry() throws Exception {
        Class<?> pixels = Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels"), box = Class.forName("org.polyfrost.skinlayers.mesh.BoxLayout");
        int[] argb = new int[4096]; argb[11 * 64 + 43] = 0xFFFFFFFF;
        Object image = pixels.getConstructor(int.class, int.class, int[].class).newInstance(64, 64, argb);
        Object head = box.getConstructor(int.class, int.class, int.class, int.class, int.class).newInstance(8, 8, 8, 32, 0);
        Object mesher = Class.forName("org.polyfrost.skinlayers.mesh.LayerMesher").getConstructor(pixels, box, boolean.class).newInstance(image, head, false);
        float[] quads = (float[]) call(mesher, "build", false, 0f);
        require(quads != null && quads.length >= 5 * 23, "The bundled outer pixel is not voxelized");
        java.util.Set<String> normals = new java.util.HashSet<>();
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int q = 0; q < quads.length; q += 23) {
            normals.add(quads[q] + "," + quads[q + 1] + "," + quads[q + 2]);
            for (int v = 0; v < 4; v++) { min = Math.min(min, quads[q + 5 + v * 5]); max = Math.max(max, quads[q + 5 + v * 5]); }
        }
        require(normals.size() >= 5 && max - min > 0.1f, "The bundled skin layer lacks side faces or depth");
        return Map.of("faces", quads.length / 23, "normalDirections", normals.size(), "depth", max - min);
    }

    private static Map<String, Integer> actualVoxelParts(Object texture) throws Exception {
        Map<?, ?> cache = (Map<?, ?>) staticField("org.polyfrost.skinlayers.skin.SkinMeshCache", "players").get(null);
        Object meshes = null;
        for (var entry : cache.entrySet()) if (texture.equals(call(entry.getKey(), "getSkin"))) meshes = entry.getValue();
        require(meshes != null, "The real player renderer did not request the uploaded skin's voxel mesh");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String part : List.of("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg")) {
            Object mesh = call(meshes, "get" + part);
            require(mesh != null, "The real voxel renderer omitted " + part);
            float[] quads = (float[]) field(mesh, "quads");
            require(quads.length >= 23, "The real voxel renderer has no geometry for " + part);
            result.put(part, quads.length / 23);
        }
        return result;
    }

    private static byte[] skinImage() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < (y < 16 ? 32 : 64); x++) image.setRGB(x, y, 0xFF53798E);
        for (int y = 48; y < 64; y++) for (int x = 16; x < 48; x++) image.setRGB(x, y, 0xFF53798E);
        for (int[] pixel : new int[][]{{43, 11}, {22, 40}, {46, 40}, {54, 56}, {6, 40}, {6, 56}}) image.setRGB(pixel[0], pixel[1], 0xFFFF714D);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "PNG", out); return out.toByteArray();
    }

    private static RemoteClientPlayerEntity peer(Minecraft mc, int index) {
        RemoteClientPlayerEntity peer = new RemoteClientPlayerEntity(mc.world, new GameProfile(new UUID(33, index + 1), "V033Peer" + index));
        peer.setNetworkId(-33300 - index);
        peer.setPositionAndAngles(8.5 + (index % 5 - 2) * 0.35, 4, 10.5, 180, 0);
        peer.setHealth(20f);
        mc.world.addEntity(peer);
        PEERS.add(peer);
        return peer;
    }

    private static void openCrosshairEditor() throws Exception {
        Object config = object("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig");
        Object tree = call(config, "getTree");
        require(call(tree, "get", "arcadiumCustomCrosshair") != null, "Crosshair Tweaks has no custom crosshair option");
        Object button = call(tree, "get", "arcadiumCrosshairEditor");
        require(button != null, "Crosshair Tweaks has no editor entry");
        ((Runnable) call(button, "getMetadata", "runnable")).run();
    }

    private static void snapshotConfig(String name, Path folder) throws Exception {
        Class<?> type = Class.forName(name);
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()) && field.getType().isPrimitive()) {
                field.setAccessible(true); STATIC_STATE.putIfAbsent(field, field.get(null));
            }
        }
        for (Method getter : type.getDeclaredMethods()) {
            if (!Modifier.isStatic(getter.getModifiers()) || getter.getParameterCount() != 0 || !getter.getName().startsWith("get") || !getter.getReturnType().isPrimitive()) continue;
            try { CONFIG_STATE.put(type.getMethod("set" + getter.getName().substring(3), getter.getReturnType()), getter.invoke(null)); }
            catch (NoSuchMethodException ignored) { }
        }
        Object instance = object(name);
        backup(folder.resolve((String) instance.getClass().getField("id").get(instance)), Minecraft.getInstance().gameDir.toPath().toRealPath());
    }

    private static void saveStatic(String type, String name) throws Exception { Field field = staticField(type, name); STATIC_STATE.putIfAbsent(field, field.get(null)); }
    private static void backup(Path file, Path game) throws Exception {
        file = file.toAbsolutePath().normalize();
        require(file.startsWith(game), "Visual QA would write outside the owned game fixture");
        Path existing = file;
        while (!Files.exists(existing, java.nio.file.LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        require(existing.toRealPath().startsWith(game), "Visual QA encountered a config link outside the owned game fixture");
        if (!FILES.containsKey(file)) FILES.put(file, Files.isRegularFile(file) ? Files.readAllBytes(file) : null);
    }

    private static void restore(Minecraft mc) throws Exception {
        mc.openScreen(null);
        Object uploaded = staticField(SKINS + "SkinChanger", "texture").get(null);
        for (var entry : CONFIG_STATE.entrySet()) entry.getKey().invoke(null, entry.getValue());
        for (var entry : STATIC_STATE.entrySet()) entry.getKey().set(null, entry.getValue());
        if (uploaded != null && uploaded != staticField(SKINS + "SkinChanger", "texture").get(null)) {
            stat(FEATURES + "SkinLayersBridge", "discardTexture", uploaded);
            mc.getTextureManager().close((Identifier) uploaded);
        }
        staticField(FEATURES + "CustomCrosshair", "data").set(null, originalDocument);
        staticField(FEATURES + "CustomCrosshair", "cachedMesh").set(null, originalMesh);
        set(library, "index", originalIndex);
        stat(FEATURES + "Skin3DConfig", "chooseEnabled", stat(FEATURES + "Skin3DConfig", "getEnabled"));
        stat(FEATURES + "KillEffects", "tick");
        for (var entry : FILES.entrySet()) {
            if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
            else { Files.createDirectories(entry.getKey().getParent()); Files.write(entry.getKey(), entry.getValue()); }
        }
        mc.options.guiScale = guiScale; mc.options.perspective = perspective; mc.options.hideGui = hideGui;
        mc.openScreen(parent);
    }

    static Object semanticsRoot(Object screen) throws Exception {
        Object scene = field(screen, "sceneOrNull");
        require(scene != null, "The visual screen has no rendered Compose scene");
        return call(call(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode");
    }
    static List<Object> nodes(Object node) throws Exception {
        List<Object> result = new ArrayList<>(); result.add(node);
        for (Object child : (Collection<?>) call(node, "getChildren")) result.addAll(nodes(child));
        return result;
    }
    static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : (Iterable<?>) call(node, "getConfig")) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
            result.put((String) call(entry.getKey(), "getName"), entry.getValue());
        }
        return result;
    }
    static Object textNode(Object screen, String text, boolean clickable) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Map<String, Object> props = properties(node);
            if (clickable && !props.containsKey("OnClick")) continue;
            if (props.get("Text") instanceof Collection<?> values)
                for (Object value : values) if (text.equals(call(value, "getText"))) return node;
        }
        throw new IllegalStateException("Visual QA control was not found: " + text);
    }
    static boolean hasText(Object screen, String text) throws Exception {
        try { textNode(screen, text, false); return true; } catch (IllegalStateException missing) { return false; }
    }
    private static boolean hasDescription(Object screen, String prefix) throws Exception {
        return descriptionNode(screen, prefix) != null;
    }
    private static Object descriptionNode(Object screen, String prefix) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Object value = properties(node).get("ContentDescription");
            if (value != null && value.toString().contains(prefix)) return node;
        }
        return null;
    }
    static void click(Object screen, String text) throws Exception {
        Object node = textNode(screen, text, true);
        Object action = call(properties(node).get("OnClick"), "getAction");
        require(Boolean.TRUE.equals(call(action, "invoke")), "The visual control rejected a click: " + text);
    }
    private static boolean canClick(Object screen, String text) throws Exception {
        try { return !properties(textNode(screen, text, true)).containsKey("Disabled"); }
        catch (IllegalStateException missing) { return false; }
    }
    private static void setText(Object screen, String text) throws Exception {
        String expected = (String) call(screen, screen.getClass().getSimpleName().equals("CustomCrosshairScreen") ? "getProfileName" : "getName");
        for (Object node : nodes(semanticsRoot(screen))) {
            Map<String, Object> props = properties(node);
            Object value = props.get("SetText");
            if (value == null) continue;
            Object editable = props.get("EditableText");
            if (editable == null || !expected.equals(call(editable, "getText"))) continue;
            Object annotated = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class).newInstance(text, List.of(), List.of());
            require(Boolean.TRUE.equals(call(call(value, "getAction"), "invoke", annotated)), "The visual name input rejected text");
            return;
        }
        throw new IllegalStateException("The visual name input was not found");
    }
    private static Map<String, Object> bounds(Minecraft mc, List<String> labels) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String label : labels) {
            result.put(label, rectangle(textNode(mc.screen, label, false), mc, label));
        }
        return result;
    }
    static List<Float> rectangle(Object node, Minecraft mc, String label) throws Exception {
        require(node != null, "The visual control is missing: " + label);
        Object rect = call(node, "getBoundsInRoot");
        float left = ((Number) call(rect, "getLeft")).floatValue(), top = ((Number) call(rect, "getTop")).floatValue();
        float right = ((Number) call(rect, "getRight")).floatValue(), bottom = ((Number) call(rect, "getBottom")).floatValue();
        require(right > left && bottom > top && left >= 0 && top >= 0 && right <= mc.width && bottom <= mc.height,
                "The visual control is clipped: " + label + " " + rect);
        return List.of(left, top, right, bottom);
    }
    private static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); CAPTURES.add(name); }

    private static int number(String type, String method) throws Exception { return ((Number) stat(type, method)).intValue(); }
    static Object object(String type) throws Exception { return Class.forName(type).getField("INSTANCE").get(null); }
    static Object stat(String type, String method, Object... args) throws Exception {
        try { return invoke(Class.forName(type), null, method, args); }
        catch (NoSuchMethodException missing) { return call(object(type), method, args); }
    }
    static Object call(Object target, String method, Object... args) throws Exception { return invoke(target.getClass(), target, method, args); }
    private static Object invoke(Class<?> type, Object owner, String name, Object... args) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length || (owner == null && !Modifier.isStatic(method.getModifiers()))) continue;
                boolean matches = true;
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < parameters.length; i++) {
                    Class<?> parameter = wrap(parameters[i]);
                    if (args[i] != null && !parameter.isInstance(args[i])) matches = false;
                    if (args[i] == null && parameters[i].isPrimitive()) matches = false;
                }
                if (!matches) continue;
                method.setAccessible(true);
                try { return method.invoke(owner, args); }
                catch (InvocationTargetException failure) { if (failure.getCause() instanceof Exception cause) throw cause; throw failure; }
            }
        }
        for (Class<?> iface : type.getInterfaces()) try { return invoke(iface, owner, name, args); } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static Class<?> wrap(Class<?> type) {
        if (type == int.class) return Integer.class; if (type == boolean.class) return Boolean.class; if (type == float.class) return Float.class;
        if (type == double.class) return Double.class; if (type == long.class) return Long.class; if (type == byte.class) return Byte.class; return type;
    }
    static Field staticField(String type, String name) throws Exception { return findField(Class.forName(type), name); }
    static Field findField(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field field = current.getDeclaredField(name); field.setAccessible(true); return field;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    static Object field(Object target, String name) throws Exception { return findField(target.getClass(), name).get(target); }
    private static Object uncheckedField(Object target, String name) { try { return field(target, name); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private static void set(Object target, String name, Object value) throws Exception { findField(target.getClass(), name).set(target, value); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
