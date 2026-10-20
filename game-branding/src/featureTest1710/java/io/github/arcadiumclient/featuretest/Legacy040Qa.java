// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

public final class Legacy040Qa {
    private static final String RUNTIME = "io.github.arcadiumclient.runtime.client.";
    private static final String GRAPH = "org.polyfrost.oneconfig.internal.ui.navigation.graph.";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Object> SHOTS = new LinkedHashMap<>();
    private static final List<Map<String, String>> PROBE_FAILURES = new ArrayList<>();
    private static int ticks, phase, entered;
    private static boolean initialized, done, worldProbesStarted;
    private static Path report;
    private static String capture, pendingCapture;
    private static long pendingSince;
    private static CheckedAction afterCapture;
    private static Screen mainMenu;
    private static Object fpsHud;
    private static UUID owner;

    private Legacy040Qa() {}

    public static void tick() {
        if (!Boolean.getBoolean("arcadium.featureTest040") || done) return;
        try {
            Minecraft client = Minecraft.getInstance();
            if (!initialized) { initialize(client); initialized = true; }
            require(++ticks < 3000, "Legacy QA timed out at phase " + phase);
            if (ticks < 80 || capture != null || pendingCapture != null || ticks - entered < 18) return;
            if ("saturation-only".equals(System.getProperty("arcadium.featureTest040.verificationScope"))) {
                LegacySaturation040Qa.verify(client, RESULT);
                RESULT.put("completed", true); RESULT.put("passed", true); finish(client, 0);
                return;
            }
            RESULT.put("phase", phase);
            switch (phase) {
                case 0 -> {
                    require(client.screen != null && client.screen.getClass().getSimpleName().equals("ArcadiumMainMenu"), "The initial Arcadium menu is missing");
                    require(hasText(client.screen, "Select a theme"), "A new game profile did not show the actual tutorial");
                    mainMenu = client.screen;
                    take("040-1710-onboarding-theme.png");
                    afterCapture = () -> pointer(client, text(client.screen, "Next", true));
                }
                case 1 -> {
                    require(hasText(client.screen, "Toggle Sprint"), "The actual sprint page did not open");
                    take("040-1710-onboarding-sprint.png");
                    afterCapture = () -> click(client.screen, "Next");
                }
                case 2 -> {
                    require(hasText(client.screen, "Block Overlay"), "The native block overlay tutorial page did not open");
                    take("040-1710-onboarding-block-overlay.png");
                    afterCapture = () -> click(client.screen, "Next");
                }
                case 3 -> {
                    require(hasText(client.screen, "All Done!"), "The tutorial completion page did not open");
                    click(client.screen, "Finish");
                }
                case 4 -> {
                    require(hasText(client.screen, "Singleplayer") && Boolean.TRUE.equals(stat(RUNTIME + "ClientPreferences", "getSetupComplete")), "Tutorial completion did not persist");
                    RESULT.put("realOnboardingCompleted", true);
                    take("040-1710-main-menu.png");
                    afterCapture = () -> pointer(client, text(client.screen, "Arcadium Options", true));
                }
                case 5 -> {
                    require(client.screen.getClass().getSimpleName().equals("OneConfigUIScreen"), "Arcadium Options did not open");
                    require(Class.forName("org.polyfrost.oneconfig.internal.OneConfigConfig").getField("guiMode").getInt(null) == 1, "New users did not default to Simple");
                    inspectModules();
                    take("040-1710-modules.png");
                    afterCapture = () -> openOptions("PreferencesRoute");
                }
                case 6 -> {
                    require(hasText(client.screen, "Raw Mouse Input") && hasText(client.screen, "Scroll Speed"), "Current Preferences controls are missing");
                    take("040-1710-preferences.png");
                    afterCapture = () -> {
                    Class.forName("org.polyfrost.oneconfig.internal.OneConfigConfig").getField("scrollSpeed").setFloat(null, 2.25f);
                    call(stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active"), "saveAll");
                    RESULT.put("settingsSave", verifySavedSetting(client));
                    client.openScreen(mainMenu);
                    client.openScreen((Screen) Class.forName(RUNTIME + "gui.ArcadiumResourcePacksScreen").getConstructor(Screen.class).newInstance(mainMenu));
                    };
                }
                case 7 -> {
                    require(hasText(client.screen, "Available") && hasText(client.screen, "Active"), "The native resource pack browser did not open");
                    search(client.screen, "Native Needle");
                }
                case 8 -> {
                    require(hasText(client.screen, "Native Needle"), "Folder resource pack search missed its nested match");
                    if (!LegacyResourceDrag040Qa.tick(client, RESULT)) return;
                }
                case 9 -> {
                    List<?> active = (List<?>) call(client.screen, "getActive");
                    require(active.size() == 1 && active.get(0).toString().contains("Native Needle"), "The pack activation control did not update its real selection");
                    take("040-1710-resource-packs.png");
                    afterCapture = () -> click(client.screen, "Apply");
                }
                case 10 -> {
                    if (Boolean.TRUE.equals(call(client.screen, "getApplying"))) return;
                    require(call(client.screen, "getError") == null, "Resource pack apply reported an error");
                    RESULT.put("nestedResourcePackSearchAndApply", true);
                    client.openScreen(mainMenu);
                    client.openScreen(new VideoOptionsScreen(mainMenu, client.options));
                }
                case 11 -> {
                    require(client.screen.getClass().getSimpleName().equals("ArcadiumVideoSettingsScreen"), "The native video settings hook did not open Arcadium controls");
                    LegacyVideo040Qa.verify(client, RESULT);
                    take("040-1710-video-settings.png");
                    afterCapture = () -> click(client.screen, "Done");
                }
                case 12 -> {
                    require(client.screen == mainMenu, "Video settings did not return to the title menu");
                    client.options.pauseOnUnfocus = false;
                    client.options.vsync = false; client.options.fpsLimit = 260; Display.setVSyncEnabled(false);
                    RESULT.put("uncappedWorldProfile", Map.of("vsync", false, "fpsLimit", 260, "swapIntervalExplicitlyDisabled", true));
                    client.options.viewDistance = 4;
                    client.options.guiScale = 2;
                    LegacyFontParity040Qa.verify(client, RESULT);
                    client.startGame("arcadium040-1710-qa", "Arcadium 0.4.0 QA", new WorldSettings(71, WorldSettings.getGameModeById(1), false, false, WorldGeneratorType.FLAT).enableCommands());
                }
                case 13 -> {
                    if (client.world == null || client.player == null || ticks - entered < 100) return;
                    require(client.isSingleplayer() && !client.getServer().isPublished(), "The QA world is not isolated");
                    owner = client.getSession().getProfile().getId();
                    require(owner.equals(client.player.getUuid()), "The integrated-server player UUID differs from its offline session");
                    if (!worldProbesStarted) {
                        inspectModules(); Registry040Qa.capture(RESULT);
                        probe("Badge", () -> LegacyBadge040Qa.verify(client, RESULT));
                        probe("Hitbox", () -> LegacyHitbox040Qa.verify(client, RESULT));
                        probe("FeetCoordinates", () -> LegacyFeet040Qa.verify(client, RESULT));
                        probe("VisualModules", () -> LegacyVisualModules040Qa.verify(client, RESULT));
                        probe("ColorSaturation", () -> LegacySaturation040Qa.verify(client, RESULT));
                        probe("Particles", () -> LegacyParticles040Qa.tick(client, RESULT));
                        probe("Glint", () -> LegacyGlint040Qa.verify(client, RESULT));
                        worldProbesStarted = true;
                    }
                    if (!probeTick("Overlay", () -> LegacyOverlay040Qa.tick(client, RESULT)) || !probeTick("Blur", () -> LegacyBlur040Qa.tick(client, RESULT))) return;
                    createHud();
                    client.player.yaw = 180f;
                    client.player.pitch = 12f;
                }
                case 14 -> {
                    require(((Number) call(fpsHud, "getRenderedW")).floatValue() > 0 && ((Number) call(fpsHud, "getRenderedH")).floatValue() > 0, "The real FPS HUD did not render");
                    RESULT.put("fpsHudText", call(fpsHud, "getText"));
                    RESULT.put("worldTerrainDebug", client.getRenderChunkDebugInfo());
                    take("040-1710-world-hud.png");
                    afterCapture = () -> openOptions("ModsRoute");
                }
                case 15 -> {
                    require(client.world != null && client.screen.getClass().getSimpleName().equals("OneConfigUIScreen"), "Arcadium Options did not open inside the world");
                    take("040-1710-world-options.png");
                    afterCapture = () -> {
                    client.openScreen(null);
                    stat(RUNTIME + "features.GuiScaleConfig", "setEnabled", true);
                    stat(RUNTIME + "features.GuiScaleConfig", "setInventoryScale", 0);
                    stat(RUNTIME + "features.GuiScaleConfig", "setMiscInventoryScale", 0);
                    client.openScreen(new SurvivalInventoryScreen(client.player));
                    };
                }
                case 16 -> {
                    int expected = ((Number) stat(RUNTIME + "features.GuiScaling", "screenWidth", client.screen)).intValue();
                    require(client.screen.width == expected && expected == client.width, "Native inventory GUI Scale did not apply");
                    RESULT.put("inventoryLogicalWidth", expected);
                    take("040-1710-inventory-scale.png");
                    afterCapture = () -> {
                    client.openScreen(null);
                    stat(RUNTIME + "features.GuiScaleConfig", "setEnabled", false);
                    applySkin(client, "classic", false);
                    client.options.perspective = 2;
                    };
                }
                case 17 -> {
                    require(Boolean.FALSE.equals(stat(RUNTIME + "skins.LegacyPlayerSkins", "slim", client.player)), "The Classic model selection failed");
                    take("040-1710-skin-classic-flat.png");
                    afterCapture = () -> stat(RUNTIME + "features.Skin3DConfig", "setEnabled", true);
                }
                case 18 -> {
                    require(compiledSkinLists() >= 6, "Six actual 3D body-part meshes were not drawn");
                    take("040-1710-skin-classic-3d.png");
                    afterCapture = () -> applySkin(client, "slim", true);
                }
                case 19 -> {
                    require(Boolean.TRUE.equals(stat(RUNTIME + "skins.LegacyPlayerSkins", "slim", client.player)), "The Slim model selection failed");
                    require(compiledSkinLists() >= 6, "The Slim 3D body-part meshes were not drawn");
                    take("040-1710-skin-slim-3d-front.png");
                    afterCapture = () -> client.options.perspective = 1;
                }
                case 20 -> {
                    take("040-1710-skin-slim-3d-back-cape.png");
                    afterCapture = () -> {
                    stat(RUNTIME + "skins.SkinChanger", "refreshCape", owner, (Object) null);
                    require(client.player.getCapeTextureLocation() == null && !client.player.hasCape(), "The explicit No Cape override did not reach native player rendering");
                    client.options.perspective = 0;
                    client.player.inventory.selectedSlot = 8;
                    client.player.inventory.items[8] = null;
                    };
                }
                case 21 -> {
                    take("040-1710-skin-slim-3d-hand.png");
                    afterCapture = () -> Display.setDisplayMode(new DisplayMode(1024, 640));
                }
                case 22 -> {
                    if (client.width != 1024 || client.height != 640) return;
                    require(((Number) call(fpsHud, "getRenderedW")).floatValue() > 0, "The HUD vanished after a native window resize");
                    take("040-1710-resized-hud.png");
                    afterCapture = () -> {
                    stat(RUNTIME + "skins.SkinChanger", "clearCapePreview", owner);
                    stat(RUNTIME + "features.Skin3DConfig", "setEnabled", false);
                    require(((Number) stat(RUNTIME + "skins.SkinUploadClient", "requestCount")).intValue() == 0, "Offline QA sent an account appearance request");
                    RESULT.put("accountAppearanceRequests", 0);
                    RESULT.put("nativeSkinAndCapeHooks", true);
                    RESULT.put("realVoxelBodyParts", true);
                    RESULT.put("windowResizeAndHud", true);
                    };
                }
                case 23 -> {
                    if (!LegacyCrosshair040Qa.tick(client, RESULT, ticks)) return;
                }
                case 24 -> {
                    if (!LegacyTextAlign040Qa.tick(client, RESULT, ticks)) return;
                }
                case 25 -> {
                    if (!LegacyPerformance040Qa.tick(client, RESULT, ticks)) return;
                }
                case 26 -> {
                    require(PROBE_FAILURES.isEmpty(), "Independent native probes failed: " + PROBE_FAILURES);
                    RESULT.put("completed", true);
                    RESULT.put("passed", true);
                    finish(client, 0);
                    return;
                }
                default -> throw new AssertionError("Unknown native QA phase " + phase);
            }
            phase++; entered = ticks;
            flush();
        } catch (Throwable failure) { fail(failure); }
    }

    public static void afterFrame() {
        if (!Boolean.getBoolean("arcadium.featureTest040") || done || capture == null && pendingCapture == null) return;
        try {
            Minecraft client = Minecraft.getInstance();
            if (pendingCapture == null) {
                pendingCapture = capture; capture = null; pendingSince = System.nanoTime();
                Screenshot.take(client.gameDir, pendingCapture, client.width, client.height, client.getRenderTarget());
            }
            String name = pendingCapture;
            Path file = client.gameDir.toPath().resolve("screenshots").resolve(name);
            require(System.nanoTime() - pendingSince < 15_000_000_000L, "The native screenshot save timed out: " + name);
            if (!Files.isRegularFile(file)) return;
            byte[] png = Files.readAllBytes(file);
            if (png.length < 24 || png[png.length - 8] != 'I' || png[png.length - 7] != 'E' || png[png.length - 6] != 'N' || png[png.length - 5] != 'D') return;
            BufferedImage image = ImageIO.read(file.toFile());
            LegacyTextAlign040Qa.afterCapture(client, name, image);
            LegacyGuiBlur040Qa.afterCapture(client, name, image, RESULT);
            Map<String, Object> pixels = pixels(image);
            if (name.contains("world-hud") || name.contains("resized-hud")) require(((Number) pixels.get("greenTerrainPixels")).intValue() > 1000, "The captured terrain is blank");
            if (name.contains("skin-") && !name.contains("hand")) require(((Number) pixels.get("magentaSkinPixels")).intValue() > 12, "The native skin did not appear in its actual rendered frame");
            pixels.put("path", file.toString()); SHOTS.put(name, pixels);
            pendingCapture = null;
            CheckedAction next = afterCapture; afterCapture = null;
            if (next != null) next.run();
            flush();
        } catch (Throwable failure) { fail(failure); }
    }

    private static void initialize(Minecraft client) throws Exception {
        Path game = client.gameDir.toPath().toRealPath();
        require("ArcadiumTest".equals(client.getSession().getUsername()) && "0".equals(client.getSession().getAccessToken()), "Native QA requires its offline test account");
        require(game.equals(Path.of(System.getProperty("arcadium.featureTest040.gameDir")).toRealPath()), "Native QA game directory mismatch");
        require(FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize().startsWith(game), "Native QA config escaped its isolated directory");
        report = game.resolve("legacy040-verification.json");
        var loader = FabricLoader.getInstance();
        Map<String, String> mods = new LinkedHashMap<>();
        loader.getAllMods().forEach(mod -> mods.put(mod.getMetadata().getId(), mod.getMetadata().getVersion().getFriendlyString()));
        for (String forbidden : List.of("polyplus", "oneclient", "crashpatch", "chattweaks", "polysprint", "arcadium_forge_compat")) require(!mods.containsKey(forbidden), "A prohibited or version-incompatible module loaded: " + forbidden);
        List<String> expected = List.of(System.getProperty("arcadium.featureTest040.requiredMods", "").split(","));
        require(!expected.contains(""), "The native fixture has no required provider inventory");
        for (String required : expected) require(mods.containsKey(required), "A required native provider was not loaded: " + required);
        RESULT.put("version", "0.4.0"); RESULT.put("minecraftVersion", "1.7.10");
        RESULT.put("loadedMods", mods); RESULT.put("requiredMods", expected);
        RESULT.put("providerScope", System.getProperty("arcadium.featureTest040.providerScope", "full"));
        RESULT.put("verificationScope", System.getProperty("arcadium.featureTest040.verificationScope", "full"));
        RESULT.put("renderDevice", GL11.glGetString(GL11.GL_RENDERER));
        RESULT.put("windowTitle", Display.getTitle());
        RESULT.put("screenshots", SHOTS);
        require(Display.getTitle().contains("Arcadium Client 1.7.10"), "Native branding title is missing");
        createPacks(game.resolve("resourcepacks"));
        flush();
    }

    private static void inspectModules() throws Exception {
        Collection<?> configs = (Collection<?>) call(instance("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry"), "getConfigs");
        List<String> ids = new ArrayList<>(); for (Object config : configs) ids.add(String.valueOf(call(config, "getId")));
        for (String required : List.of("arcadium-auto-text.json", "polysprint.json", "chattweaks.json", "arcadium-chat-filters.json",
            "arcadium-animations.json", "arcadium-players.json", "arcadium-gui-scale.json", "arcadium-tab-background.json",
            "arcadium-light-level.json", "arcadium-no-render.json", "arcadium-3d-skin.json", "arcadium-kill-effects.json",
            "arcadium-cute-visuals.json", "arcadium-nick-hider.json", "hybedwars.json", "crosshairtweaks.json",
            "overlaytweaks.json", "fovchanger.json", "fullbright.json", "arcadium-menus.json", "soundtweaks.json", "blur.json", "oneconfig.json", "themes.json")) require(ids.contains(required), "A current native settings tree is missing: " + required);
        require(ids.stream().noneMatch(id -> id.contains("bedwars-overlay") || id.contains("no-hit-delay")), "An excluded 1.8.9-only module was registered");
        RESULT.put("registeredConfigurations", ids);
        Registry040Qa.capture(RESULT);
    }

    private static Object verifySavedSetting(Minecraft client) throws Exception {
        try (var files = Files.walk(client.gameDir.toPath().resolve("config"))) {
            Path saved = files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals("oneconfig.json"))
                .filter(path -> { try { return Files.readString(path).contains("2.25"); } catch (Exception ignored) { return false; } }).findFirst().orElseThrow(() -> new AssertionError("Scroll Speed was not written to the real native configuration"));
            return Map.of("path", saved.toString(), "scrollSpeed", 2.25);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void createHud() throws Exception {
        Object manager = instance("org.polyfrost.oneconfig.api.hud.v1.HudManager");
        Object provider = call(manager, "getProvider", Class.forName("org.polyfrost.evergreenhud.client.hud.FpsHud"));
        require(provider != null, "The real native FPS provider is missing");
        fpsHud = call(provider, "make", (Object) null);
        call(manager, "markProviderKnown", fpsHud);
        ((List) call(manager, "getActiveInstances")).add(fpsHud);
        Object revision = call(manager, "getInstancesRevision");
        call(revision, "setIntValue", ((Number) call(revision, "getIntValue")).intValue() + 1);
        call(fpsHud, "setup");
        call(fpsHud, "setSection", Enum.valueOf((Class) Class.forName("org.polyfrost.oneconfig.api.hud.v1.Section"), "TopLeft"));
        call(fpsHud, "setRelativeX", 16f); call(fpsHud, "setRelativeY", 16f);
        call(fpsHud, "setHidden", false); call(fpsHud, "setShowBackground", true);
        stat("org.polyfrost.oneconfig.api.hud.v1.HudManager", "invalidate");
    }

    private static void applySkin(Minecraft client, String variant, boolean cape) throws Exception {
        stat(RUNTIME + "skins.SkinChanger", "refresh", owner, skinPng(), variant);
        require(stat(RUNTIME + "skins.SkinChanger", "textureFor", UUID.randomUUID()) == null, "An uploaded test skin leaked to another UUID");
        if (cape) {
            stat(RUNTIME + "skins.SkinChanger", "refreshCape", owner, capePng());
            require(client.player.hasCape() && client.player.getCapeTextureLocation() != null, "The selected cape did not reach native player rendering");
        }
    }

    private static int compiledSkinLists() throws Exception {
        int lists = 0;
        for (Object skin : ((Map<?, ?>) field(Class.forName(RUNTIME + "skins.LegacyPlayerSkins"), "SKINS")).values()) {
            for (int list : (int[]) field(skin, "lists")) if (list != 0) { require(GL11.glIsList(list), "A voxel GPU mesh was already deleted"); lists++; }
        }
        return lists;
    }

    private static byte[] skinPng() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, (x + y) % 5 == 0 ? 0xFF36C7CE : 0xFFD232C5);
        return png(image);
    }

    private static byte[] capePng() throws Exception {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, x % 4 < 2 ? 0xFFE139D4 : 0xFF36F1B0);
        return png(image);
    }

    private static byte[] png(BufferedImage image) throws Exception { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); require(ImageIO.write(image, "PNG", bytes), "Could not encode a native test PNG"); return bytes.toByteArray(); }

    private static void createPacks(Path directory) throws Exception {
        Path folder = directory.resolve("Arcadium040/Closed/§bNative Needle"); Files.createDirectories(folder);
        Files.writeString(folder.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":1,\"description\":\"§aNative folder fixture\"}}", StandardCharsets.UTF_8);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(directory.resolve("Arcadium040/Archive.zip")))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta")); zip.write("{\"pack\":{\"pack_format\":1,\"description\":\"Native archive fixture\"}}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
    }

    private static void openOptions(String route) throws Exception { stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", instance(GRAPH + route)); }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    @FunctionalInterface private interface CheckedTick { boolean run() throws Exception; }
    private static void probe(String name, CheckedAction action) throws Exception {
        try { action.run(); }
        catch (Throwable failure) {
            if (!Boolean.getBoolean("arcadium.featureTest040.diagnostics") || failure instanceof VirtualMachineError || failure instanceof ThreadDeath) {
                if (failure instanceof Exception exception) throw exception;
                throw (Error) failure;
            }
            failure.printStackTrace();
            PROBE_FAILURES.add(Map.of("probe", name, "error", failure.toString()));
            RESULT.put("probeFailures", PROBE_FAILURES); flush();
        }
    }
    private static boolean probeTick(String name, CheckedTick action) throws Exception {
        boolean[] completed = {true};
        probe(name, () -> completed[0] = action.run());
        return completed[0];
    }
    private static void take(String name) { require(capture == null, "A native screenshot is already pending"); capture = name; }
    static void requestCapture(String name) { take(name); }

    private static Map<String, Object> pixels(BufferedImage image) {
        int green = 0, magenta = 0;
        for (int y = 64; y < image.getHeight() - 64; y++) for (int x = 16; x < image.getWidth() - 16; x++) {
            int color = image.getRGB(x, y), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
            if (g > 30 && g > r * 1.15 && g > b * 1.15) green++;
            if (r > 60 && b > 50 && r > g * 1.6 && b > g * 1.4) magenta++;
        }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("greenTerrainPixels", green); result.put("magentaSkinPixels", magenta);
        result.put("width", image.getWidth()); result.put("height", image.getHeight()); return result;
    }

    private static void flush() throws Exception { if (report != null) Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); }
    private static void fail(Throwable failure) { failure.printStackTrace(); RESULT.put("passed", false); RESULT.put("error", failure.toString()); RESULT.put("phase", phase); finish(Minecraft.getInstance(), 1); }

    private static void finish(Minecraft client, int exitCode) {
        done = true;
        try { flush(); } catch (Exception failure) { failure.printStackTrace(); exitCode = 1; }
        int result = exitCode;
        client.stop();
        Thread shutdown = new Thread(() -> { try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } System.exit(result); }, "Arcadium native QA shutdown");
        shutdown.setDaemon(true); shutdown.start();
    }
}
