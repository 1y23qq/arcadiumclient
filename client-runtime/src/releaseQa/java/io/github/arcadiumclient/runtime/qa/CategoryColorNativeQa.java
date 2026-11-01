// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorOptions;
import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorProfile;
import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorRenderer;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import io.github.arcadiumclient.runtime.client.skins.UploadedSkinTexture;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.render.PostPass;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute;
import org.polyfrost.oneconfig.internal.ui.screens.ModsKt;
import org.polyfrost.oneconfig.internal.ui.shell.LocalNavController;
import org.polyfrost.oneconfig.internal.ui.shell.ShellState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.IntPredicate;

public final class CategoryColorNativeQa {
    private static final String ID = "colorsaturation.json";
    private static final Identifier SKIN = new Identifier("arcadium", "color-category-qa-skin");
    private static final boolean LINKED_CONTROL = Boolean.getBoolean("arcadium.colorCategories.linkedControl");
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, Object> cases = new LinkedHashMap<>();
    private static int ticks, phase, wait, frames, phaseFrames, stateChecks, particleTick, resizedWidth, resizedHeight;
    private static long phaseStarted;
    private static boolean done, particles;
    private static Config config;
    private static Tree tree;
    private static Object moduleScreen;
    private static Future<?> fixture;
    private static Image baseline;
    private static int[] entityMask, blockMask, chestMask, handMask, hudMask;
    private static long splitBefore, layersBefore, playersBefore, namesBefore, particlesBefore, scaleBefore;

    private CategoryColorNativeQa() { }
    public static boolean enabled() { return System.getProperty("arcadium.colorCategories.output") != null; }
    private static void require(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(++ticks < 4000, "Category color QA timed out at phase " + phase);
            wait++;
            if (mc.world != null && mc.player != null && phase >= 4) freeze(mc);
            if (particles && mc.world != null && ++particleTick % 2 == 0) spawnParticles(mc);
            if (phase == 0 && ticks > 65 && mc.screen != null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                require("0".equals(mc.getSession().getAccessToken())
                        && System.getProperty("arcadium.colorCategories.user", "ArcadiumTest").equals(mc.getSession().getUsername()),
                        "Use an isolated offline synthetic account");
                ClientPreferences.setSetupComplete(true);
                config = (Config) Class.forName("org.polyfrost.colorsaturation.client.ColorSaturationConfig").getField("INSTANCE").get(null);
                require(config instanceof CategoryColorOptions, "Official module config did not receive the category mixin");
                tree = config.getTree();
                for (String key : List.of("isEnabled", "strength", "contrast", "brightness", "hue", "arcadiumSeparateEntityColors",
                        "arcadiumEntityStrength", "arcadiumEntityContrast", "arcadiumEntityBrightness", "arcadiumEntityHue")) {
                    require(tree.getProp(key) != null && tree.getProp(key).getMetadata("visualizer") != null,
                            "Module GUI property missing: " + key);
                }
                require(!(boolean) tree.getProp("arcadiumSeparateEntityColors").get(), "Separate colors were enabled by default");
                for (String key : List.of("arcadiumEntityStrength", "arcadiumEntityContrast", "arcadiumEntityBrightness"))
                    require(Math.abs(((Number) tree.getProp(key).get()).floatValue() - 1) < 1e-4, "New field default is incorrect: " + key);
                require(Math.abs(((Number) tree.getProp("arcadiumEntityHue").get()).floatValue()) < 1e-4, "New hue default is incorrect");
                report.put("officialModuleConfigTransformed", true);
                report.put("newDefaultsPreserveLinkedColors", true);
                report.put("moduleVisualizers", 10);
                OneConfigUI.open();
                advance(1);
            } else if (phase == 1 && wait > 30 && LocalNavController.INSTANCE.isReady()) {
                var card = ConfigRegistry.INSTANCE.findById(ID);
                require(card != null && card.getOnOpen() == null, "Color Saturation card bypasses Arcadium Options");
                ModsKt.openModCard(card);
                advance(2);
            } else if (phase == 2 && wait > 25) {
                require(LocalNavController.INSTANCE.getWrapper().getCurrentRoute() instanceof ModConfigRoute route && ID.equals(route.getId()),
                        "Module card did not open its settings page");
                require(mc.screen != null && "OneConfigUIScreen".equals(mc.screen.getClass().getSimpleName()), "Module settings left Arcadium Options");
                moduleScreen = mc.screen;
                capture("saturation-module-card-settings.png");
                Object oldStrength = tree.getProp("strength").get();
                tree.getProp("arcadiumSeparateEntityColors").setAs(true);
                tree.getProp("arcadiumEntityBrightness").setAs(.75f);
                CategoryColorProfile world = new CategoryColorProfile(), entities = new CategoryColorProfile();
                ((CategoryColorOptions) config).arcadium$readColors(world, entities);
                require(Math.abs(entities.brightness - .75f) < 1e-4, "GUI entity property did not update runtime settings");
                require(tree.getProp("strength").get().equals(oldStrength), "Entity property changed the original world setting");
                ShellState.INSTANCE.setSearchQuery("Entity");
                report.put("guiPropertiesEditRuntime", true);
                advance(3);
            } else if (phase == 3 && wait > 25) {
                require(mc.screen == moduleScreen, "Entity settings opened a separate screen");
                capture("saturation-entity-settings.png");
                report.put("moduleCardEntry", true);
                report.put("allSettingsInsideModulePage", true);
                ShellState.INSTANCE.setSearchQuery("");
                mc.openScreen(null);
                prepareOptions(mc);
                profile(1, 1);
                mc.startGame("arcadium-category-colors-065", "Category Colors QA",
                        new WorldSettings(65013, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                advance(4);
            } else if (phase == 4 && mc.world != null && mc.player != null && wait > 80) {
                mc.openScreen(null);
                seed(mc);
                advance(5);
            } else if (phase == 5 && wait > 110 && fixture != null && fixture.isDone()) {
                fixture.get();
                require(mc.world.getBlockState(new BlockPos(3, 4, 3)).getBlock() == Blocks.CHEST, "Server fixture chest was not received");
                baseline = capture("saturation-linked-baseline.png");
                buildMasks(baseline);
                report.put("nativeRenderer", Class.forName("dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer").getName());
                report.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
                report.put("resolution", mc.width + "x" + mc.height);
                report.put("fixtureBlockEntity", mc.world.getBlockEntity(new BlockPos(3, 4, 3)).getClass().getName());
                report.put("mainAttachments", attachments());
                require(!CategoryColorRenderer.skipLegacy(), "Identical identity settings started category rendering");
                if (LINKED_CONTROL) {
                    report.put("diagnosticLinkedControl", true);
                    enableGpu();
                    playersBefore = GpuPlayerPipeline.playersDrawn;
                    namesBefore = GpuNameTags.packetsDrawn;
                    particlesBefore = GpuParticles.particlesDrawn;
                    particles = true;
                    advance(9);
                } else {
                    profile(0, 1);
                    advance(6);
                }
            } else if (phase == 6 && wait > 40) {
                Image image = capture("saturation-blocks-gray.png");
                compare("world-only", image, true, false);
                profile(1, 0);
                advance(7);
            } else if (phase == 7 && wait > 40) {
                Image image = capture("saturation-entities-gray.png");
                compare("entities-only", image, false, true);
                splitBefore = CategoryColorRenderer.splitFrames;
                layersBefore = CategoryColorRenderer.capturedLayers;
                profile(1.3f, 1.3f);
                advance(8);
            } else if (phase == 8 && wait > 35) {
                capture("saturation-identical-settings.png");
                require(CategoryColorRenderer.splitFrames == splitBefore && CategoryColorRenderer.capturedLayers == layersBefore,
                        "Identical settings added category passes");
                require(!CategoryColorRenderer.skipLegacy(), "Identical settings skipped the original postchain");
                require(field(Class.forName("org.polyfrost.colorsaturation.client.SaturationHandler"), "postChain", null) != null,
                        "Identical non-identity settings did not use the original shader");
                report.put("identicalSettingsOriginalFastPath", true);
                profile(0, 1.4f);
                enableGpu();
                playersBefore = GpuPlayerPipeline.playersDrawn;
                namesBefore = GpuNameTags.packetsDrawn;
                particlesBefore = GpuParticles.particlesDrawn;
                particles = true;
                advance(9);
            } else if (phase == 9 && wait > 65) {
                capture("saturation-gpu-players-tags-particles.png");
                require(GpuPlayerPipeline.playersDrawn > playersBefore, "Compact GPU players were not drawn: " + GpuPlayerPipeline.status());
                require(GpuNameTags.packetsDrawn > namesBefore, "GPU name tags were not drawn: " + GpuNameTags.status());
                require(GpuParticles.particlesDrawn > particlesBefore, "GPU particles were not drawn: " + GpuParticles.status());
                report.put("gpuCategoryDraws", Map.of("players", GpuPlayerPipeline.playersDrawn - playersBefore,
                        "nameTags", GpuNameTags.packetsDrawn - namesBefore, "particles", GpuParticles.particlesDrawn - particlesBefore));
                scaleBefore = WorldRenderScale.scaledFrames;
                BetaOptimizationsConfig.setWorldResolution(70);
                BetaOptimizationsConfig.setWorldRenderScale(true);
                advance(10);
            } else if (phase == 10 && wait > 40) {
                capture("saturation-world-scale-70.png");
                require(WorldRenderScale.scaledFrames > scaleBefore, "World Render Scale was not used");
                if (!LINKED_CONTROL) verifySize(mc.width * 70 / 100, mc.height * 70 / 100);
                report.put("worldRenderScale", WorldRenderScale.status());
                if (Boolean.getBoolean("arcadium.colorCategories.guiNoScale")) {
                    BetaOptimizationsConfig.setWorldRenderScale(false);
                    report.put("guiDiagnosticWithoutScale", true);
                }
                if (Boolean.getBoolean("arcadium.colorCategories.guiNoParticles")) {
                    BetaOptimizationsConfig.setGpuParticles(false);
                    report.put("guiDiagnosticWithoutGpuParticles", true);
                }
                configureBlur();
                mc.openScreen(new GameMenuScreen());
                advance(11);
            } else if (phase == 11 && wait > 30) {
                finishGuiStage(mc);
            } else if (phase == 12 && wait > 40) {
                require(mc.width == resizedWidth && mc.height == resizedHeight, "Native window resize was not propagated to Minecraft");
                capture("saturation-resized.png");
                if (!LINKED_CONTROL) verifySize(worldWidth(mc), worldHeight(mc));
                report.put("resize", mc.width + "x" + mc.height);
                mc.reloadResources();
                advance(13);
            } else if (phase == 13 && wait > 65) {
                capture("saturation-after-resource-reload.png");
                if (LINKED_CONTROL) {
                    require(!CategoryColorRenderer.skipLegacy() && CategoryColorRenderer.splitFrames == 0,
                            "Linked control entered category rendering");
                    report.put("linkedControlResizeReload", true);
                    report.put("frameStateChecks", stateChecks);
                    finish(null);
                    return;
                }
                require(CategoryColorRenderer.skipLegacy(), "Category colors did not recover after resource reload");
                verifySize(worldWidth(mc), worldHeight(mc));
                report.put("resourceReload", true);
                setRendererField("failed", true);
                splitBefore = CategoryColorRenderer.splitFrames;
                advance(14);
            } else if (phase == 14 && wait > 25) {
                capture("saturation-safe-linked-fallback.png");
                require(!CategoryColorRenderer.skipLegacy() && splitBefore == CategoryColorRenderer.splitFrames,
                        "Renderer failure did not use linked fallback");
                CategoryColorRenderer.free();
                report.put("failureFallback", true);
                advance(15);
            } else if (phase == 15 && wait > 30) {
                capture("saturation-recovered.png");
                require(CategoryColorRenderer.skipLegacy(), "Separate rendering did not recover after retry");
                verifyFrameState();
                report.put("cases", cases);
                report.put("splitFrames", CategoryColorRenderer.splitFrames);
                report.put("capturedLayers", CategoryColorRenderer.capturedLayers);
                report.put("frameStateChecks", stateChecks);
                report.put("renderedFrames", frames);
                finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }

    public static void frame() {
        if (done) return;
        frames++;
        phaseFrames++;
        if (phase >= 5 && Minecraft.getInstance().world != null) {
            try {
                verifyFrameState();
                if (phase == 11 && phaseFrames >= 3 && System.nanoTime() - phaseStarted > 1_500_000_000L)
                    finishGuiStage(Minecraft.getInstance());
            }
            catch (Throwable failure) { finish(failure); }
        }
    }

    public static void postPassStart(PostPass pass) throws Exception {
        if (phase < 10 || !pass.getEffect().getFragmentProgram().getName().equals("fade_in_blur")) return;
        float radius = uniform(pass, "Radius"), progress = uniform(pass, "Progress");
        System.out.println("CATEGORY_BLUR_BEGIN phase=" + phase + " frame=" + frames + " input=" + pass.inTarget.frameBufferId
                + "/" + pass.inTarget.colorTextureId + " output=" + pass.outTarget.frameBufferId + "/" + pass.outTarget.colorTextureId
                + " expectedProgram=" + pass.getEffect().getId() + " radius=" + radius + " progress=" + progress);
        require(Float.isFinite(radius) && Float.isFinite(progress), "GUI blur received non-finite uniforms");
    }

    public static void postPassDraw(PostPass pass) {
        if (phase < 10 || !pass.getEffect().getFragmentProgram().getName().equals("fade_in_blur")) return;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int input, other;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        input = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        other = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(active);
        int attachment = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int error = GL11.glGetError();
        String state = "phase=" + phase + " frame=" + frames + " draw=" + framebuffer + " expectedDraw=" + pass.outTarget.frameBufferId
                + " attached=" + attachment + " texture0=" + input + " expectedInput=" + pass.inTarget.colorTextureId + " texture1=" + other
                + " active=" + active + " program=" + program + " expectedProgram=" + pass.getEffect().getId()
                + " vao=" + vao + " buffer=" + buffer + " error=" + error;
        System.out.println("CATEGORY_BLUR_DRAW " + state);
        require(input != attachment, "GUI blur texture feedback: " + state);
        require(input == pass.inTarget.colorTextureId && framebuffer == pass.outTarget.frameBufferId
                && program == pass.getEffect().getId() && error == GL11.GL_NO_ERROR, "GUI blur state/cache mismatch: " + state);
    }

    private static float uniform(PostPass pass, String name) throws Exception {
        Object uniform = pass.getEffect().getUniform(name);
        require(uniform != null, "GUI blur uniform absent: " + name);
        for (Field field : uniform.getClass().getDeclaredFields()) if (field.getType() == FloatBuffer.class) {
            field.setAccessible(true);
            return ((FloatBuffer) field.get(uniform)).get(0);
        }
        throw new IllegalStateException("GUI blur uniform buffer absent: " + name);
    }

    private static void finishGuiStage(Minecraft mc) throws Exception {
        capture("saturation-gui-blur.png");
        require(field(Class.forName("eu.midnightdust.blur.BlurRenderer"), "shader", null) != null, "GUI blur shader was not active");
        report.put("guiBlurWithCategoryColors", true);
        report.put("guiBlurFrames", phaseFrames);
        mc.openScreen(null);
        particles = false;
        resizedWidth = Math.max(640, mc.width - 160);
        resizedHeight = Math.max(360, mc.height - 90);
        Display.setDisplayMode(new DisplayMode(resizedWidth, resizedHeight));
        advance(12);
    }

    private static void prepareOptions(Minecraft mc) throws Exception {
        mc.options.pauseOnUnfocus = false; mc.options.vsync = false; mc.options.viewDistance = 4;
        mc.options.fpsLimit = 180; mc.options.cloudRenderMode = 0; mc.options.perspective = 0;
        mc.options.fov = 70; mc.options.viewBobbing = false; mc.options.debugEnabled = false;
        Display.setTitle("Arcadium Category Colors QA (offline)");
        Display.setVSyncEnabled(false);
        org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
        Skin3DConfig.INSTANCE.chooseEnabled(false);
        BetaOptimizationsConfig.setGpuPlayerPipeline(false); BetaOptimizationsConfig.setGpuNameTags(false);
        BetaOptimizationsConfig.setGpuParticles(false); BetaOptimizationsConfig.setGpuModelTransforms(false);
        BetaOptimizationsConfig.setRetainedNameTagScenes(false); BetaOptimizationsConfig.setWorldRenderScale(false);
        Object nativeConfig = Class.forName("dev.rdh.argentum.impl.Argentum").getField("CONFIG").get(null);
        for (String name : List.of("entityInstancing", "nameTagBatching", "fontBatching"))
            nativeConfig.getClass().getField(name).setBoolean(nativeConfig, true);
        nativeConfig.getClass().getField("entityCulling").setBoolean(nativeConfig, false);
    }

    private static void seed(Minecraft mc) {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[4096]; Arrays.fill(pixels, 0xFFEB1728); skin.setRGB(0, 0, 64, 64, pixels, 0, 64);
        mc.getTextureManager().register(SKIN, new UploadedSkinTexture(skin));
        for (int i = 0; i < 12; i++) {
            Frozen player = new Frozen(mc.world, new GameProfile(new UUID(0x650C010000000000L, i + 1), "ColorQA" + i));
            double x = i == 0 ? 0 : (i % 3 - 1) * 1.05, z = i == 0 ? 2 : 5 + i / 3 * 1.35;
            player.updatePositionAndAngles(x, 4, z, 180, 0);
            player.lastYaw = player.bodyYaw = player.lastBodyYaw = player.headYaw = player.lastHeadYaw = 180;
            player.lastPitch = 0; player.ticks = 100;
            mc.world.forceEntity(65000 + i, player);
        }
        var board = mc.world.getScoreboard();
        var objective = board.createObjective("colors", ScoreboardCriterion.DUMMY);
        objective.setDisplayName("§cColor QA"); board.setDisplayObjective(1, objective);
        board.getScore("§aFixed HUD", objective).set(1);
        fixture = mc.getServer().executeTask(() -> {
            var serverWorld = mc.getServer().getWorld(0);
            serverWorld.getGameRules().set("doDaylightCycle", "false");
            serverWorld.getGameRules().set("doMobSpawning", "false");
            serverWorld.getGameRules().set("randomTickSpeed", "0");
            serverWorld.setTimeOfDay(6000);
            for (int x = -6; x <= -4; x++) for (int y = 4; y <= 7; y++)
                serverWorld.setBlockState(new BlockPos(x, y, 4), Blocks.WOOL.getStateFromMetadata(11), 3);
            serverWorld.setBlockState(new BlockPos(3, 4, 3), Blocks.CHEST.defaultState().set(ChestBlock.FACING, Direction.NORTH), 3);
            for (int y = 4; y < 7; y++)
                serverWorld.setBlockState(new BlockPos(5, y, 2), Blocks.STAINED_GLASS.getStateFromMetadata(5), 3);
        });
        freeze(mc);
    }

    private static void freeze(Minecraft mc) {
        mc.player.abilities.flying = true;
        mc.player.updatePositionAndAngles(0, 4, -8, 0, 0);
        mc.player.lastX = mc.player.prevX = mc.player.x;
        mc.player.lastY = mc.player.prevY = mc.player.y;
        mc.player.lastZ = mc.player.prevZ = mc.player.z;
        mc.player.lastYaw = mc.player.yaw; mc.player.lastPitch = mc.player.pitch;
        mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0;
        mc.world.setTimeOfDay(6000);
    }

    private static void spawnParticles(Minecraft mc) {
        for (int i = 0; i < 64; i++) {
            double x = (i % 8 - 3.5) * .65, y = 4.2 + (i / 8) * .22, z = 4 + (i % 3) * .4;
            var particle = mc.particleManager.addParticle(ParticleType.REDSTONE.getId(), x, y, z, .3, .1, .8);
            if (particle != null) particle.setColor(.3f, .1f, .8f);
        }
    }

    private static void enableGpu() {
        BetaOptimizationsConfig.setMinimumPlayers(1); 
        BetaOptimizationsConfig.setMinimumGpuLabels(1); BetaOptimizationsConfig.setMinimumGpuParticles(1);
        BetaOptimizationsConfig.setGpuPlayerPipeline(true); BetaOptimizationsConfig.setGpuNameTags(true);
        BetaOptimizationsConfig.setGpuParticles(true);
    }

    private static void profile(float world, float entity) {
        tree.getProp("isEnabled").setAs(true); tree.getProp("arcadiumSeparateEntityColors").setAs(true);
        tree.getProp("strength").setAs(world); tree.getProp("contrast").setAs(1f);
        tree.getProp("brightness").setAs(1f); tree.getProp("hue").setAs(0f);
        tree.getProp("arcadiumEntityStrength").setAs(entity); tree.getProp("arcadiumEntityContrast").setAs(1f);
        tree.getProp("arcadiumEntityBrightness").setAs(1f); tree.getProp("arcadiumEntityHue").setAs(0f);
    }

    private static void configureBlur() throws Exception {
        Class<?> blur = Class.forName("eu.midnightdust.blur.BlurConfig");
        blur.getField("enabled").setBoolean(null, true);
        blur.getField("radius").setInt(null, 6);
        int duration = Boolean.getBoolean("arcadium.colorCategories.zeroFadeProbe") ? 0 : 150;
        blur.getField("fadeTimeMillis").setInt(null, duration);
        blur.getField("fadeOutTimeMillis").setInt(null, duration);
        report.put("guiFadeMillis", duration);
    }

    private static void buildMasks(Image image) {
        entityMask = mask(image, .37, .63, .40, .70, c -> red(c) > green(c) * 1.9 + 15 && red(c) > blue(c) * 1.6 + 10, 2);
        blockMask = mask(image, .55, .82, .30, .70, c -> blue(c) > red(c) * 1.6 + 10 && blue(c) > green(c) * 1.15 + 10, 2);
        chestMask = mask(image, .25, .47, .48, .74, c -> red(c) > green(c) * 1.12 + 8 && green(c) > blue(c) * 1.35 + 8, 1);
        handMask = mask(image, .70, 1, .75, 1, c -> red(c) > green(c) + 5 && green(c) > blue(c) + 5, 2);
        hudMask = mask(image, .70, 1, .20, .80,
                c -> red(c) > 225 && green(c) > 55 && green(c) < 130 && Math.abs(green(c) - blue(c)) < 8, 0);
        report.put("pixelMasks", Map.of("entity", entityMask.length, "blocks", blockMask.length,
                "blockEntity", chestMask.length, "heldHand", handMask.length, "hud", hudMask.length));
        require(entityMask.length > 40 && blockMask.length > 40 && chestMask.length > 15 && handMask.length > 40 && hudMask.length > 5,
                "Native fixture did not produce sufficient colored pixels: " + report.get("pixelMasks"));
    }

    private static int[] mask(Image image, double minX, double maxX, double minY, double maxY, IntPredicate predicate, int radius) {
        List<Integer> indices = new ArrayList<>();
        for (int y = (int) (image.height * minY) + radius; y < image.height * maxY - radius; y++)
            for (int x = (int) (image.width * minX) + radius; x < image.width * maxX - radius; x++) {
                boolean accept = true;
                for (int dy = -radius; dy <= radius && accept; dy++) for (int dx = -radius; dx <= radius; dx++)
                    if (!predicate.test(image.pixels[(y + dy) * image.width + x + dx])) { accept = false; break; }
                if (accept) indices.add(y * image.width + x);
            }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void compare(String name, Image image, boolean worldChanges, boolean entitiesChange) {
        require(CategoryColorRenderer.skipLegacy(), "Category stage did not apply in " + name + ": " + CategoryColorRenderer.status());
        double entityDelta = delta(image, entityMask), blockDelta = delta(image, blockMask), chestDelta = delta(image, chestMask),
                handDelta = delta(image, handMask), hudDelta = delta(image, hudMask);
        cases.put(name, Map.of("entityMeanDelta", entityDelta, "blockMeanDelta", blockDelta,
                "blockEntityMeanDelta", chestDelta, "heldHandMeanDelta", handDelta, "hudMeanDelta", hudDelta,
                "entityChroma", chroma(image, entityMask), "blockChroma", chroma(image, blockMask), "blockEntityChroma", chroma(image, chestMask)));
        require(worldChanges ? blockDelta > 12 && chestDelta > 8 : blockDelta < 4 && chestDelta < 4,
                "Blocks/block entities did not retain their own settings in " + name + ": " + cases.get(name));
        require(entitiesChange ? entityDelta > 12 : entityDelta < 4, "Entities did not retain their own settings in " + name + ": " + cases.get(name));
        require(entitiesChange ? handDelta > 12 : handDelta < 4, "Held hand did not retain entity settings in " + name + ": " + handDelta);
        require(hudDelta < 2, "HUD changed with category settings: " + hudDelta);
        if (worldChanges) require(chroma(image, blockMask) < 4 && chroma(image, chestMask) < 4, "World/block entity desaturation is incomplete");
        if (entitiesChange) require(chroma(image, entityMask) < 4 && chroma(image, handMask) < 4, "Entity/held hand desaturation is incomplete");
    }

    private static double delta(Image image, int[] mask) {
        require(image.width == baseline.width && image.height == baseline.height, "Pixel comparison resolution changed");
        double sum = 0;
        for (int p : mask) sum += Math.abs(red(image.pixels[p]) - red(baseline.pixels[p]))
                + Math.abs(green(image.pixels[p]) - green(baseline.pixels[p])) + Math.abs(blue(image.pixels[p]) - blue(baseline.pixels[p]));
        return sum / Math.max(1, mask.length * 3);
    }

    private static double chroma(Image image, int[] mask) {
        double sum = 0;
        for (int p : mask) {
            int c = image.pixels[p], max = Math.max(red(c), Math.max(green(c), blue(c))), min = Math.min(red(c), Math.min(green(c), blue(c)));
            sum += max - min;
        }
        return sum / Math.max(1, mask.length);
    }

    private static void verifySize(int width, int height) throws Exception {
        require((int) field(CategoryColorRenderer.class, "width", null) == width
                && (int) field(CategoryColorRenderer.class, "height", null) == height, "Category framebuffer did not follow the world render target size");
    }

    private static int worldWidth(Minecraft mc) {
        return mc.width * (BetaOptimizationsConfig.getWorldRenderScale() ? BetaOptimizationsConfig.getWorldResolution() : 100) / 100;
    }
    private static int worldHeight(Minecraft mc) {
        return mc.height * (BetaOptimizationsConfig.getWorldRenderScale() ? BetaOptimizationsConfig.getWorldResolution() : 100) / 100;
    }

    private static void verifyFrameState() throws Exception {
        require(!(boolean) field(CategoryColorRenderer.class, "inWorld", null)
                && !(boolean) field(CategoryColorRenderer.class, "capturing", null)
                && !(boolean) field(CategoryColorRenderer.class, "depthAttached", null), "Category scope leaked outside world rendering");
        var target = Minecraft.getInstance().getRenderTarget();
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            require(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Main framebuffer became incomplete");
            require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == target.depthBufferId, "Main depth attachment was not restored");
        } finally { GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); }
        int error = GL11.glGetError();
        require(error == GL11.GL_NO_ERROR, "OpenGL error during native category QA: " + error);
        stateChecks++;
    }

    private static Map<String, Integer> attachments() {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, Minecraft.getInstance().getRenderTarget().frameBufferId);
            return Map.of("depthType", GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE),
                    "depth", GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME),
                    "stencilType", GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
        } finally { GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); }
    }

    private static Image capture(String name) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.width, height = mc.height, previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getRenderTarget().frameBufferId);
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        } finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous); }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] output = new int[width * height], row = new int[width];
        for (int y = 0; y < height; y++) {
            pixels.position((height - y - 1) * width); pixels.get(row);
            System.arraycopy(row, 0, output, y * width, width);
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        Path path = output().getParent().resolve(name); Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
        return new Image(width, height, output);
    }

    private static Object field(Class<?> type, String name, Object instance) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private static void setRendererField(String name, Object value) throws Exception {
        Field field = CategoryColorRenderer.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    private static int red(int c) { return c >>> 16 & 255; }
    private static int green(int c) { return c >>> 8 & 255; }
    private static int blue(int c) { return c & 255; }
    private static void advance(int next) {
        phase = next; wait = phaseFrames = 0; phaseStarted = System.nanoTime();
        System.out.println("Category color QA phase=" + phase + " ticks=" + ticks + " frames=" + frames);
    }
    private static Path output() { return Path.of(System.getProperty("arcadium.colorCategories.output")).toAbsolutePath(); }

    private static void finish(Throwable failure) {
        if (done) return;
        done = true;
        report.put("passed", failure == null);
        report.put("phase", phase);
        report.put("cases", cases);
        report.put("categoryStatus", CategoryColorRenderer.status());
        if (failure != null) {
            report.put("error", failure.toString()); failure.printStackTrace();
            try { capture("saturation-failure.png"); } catch (Exception ignored) { }
        }
        try {
            Files.createDirectories(output().getParent());
            Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }

    private record Image(int width, int height, int[] pixels) { }
    private static final class Frozen extends RemoteClientPlayerEntity {
        Frozen(net.minecraft.world.World world, GameProfile profile) { super(world, profile); }
        @Override public void tick() { ticks = 100; }
        @Override public boolean hasSkinTexture() { return true; }
        @Override public Identifier getSkinTextureLocation() { return SKIN; }
        @Override public String getModelType() { return "default"; }
    }
}
