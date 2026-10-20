// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.PlayMenusQa.capture;
import static io.github.arcadiumclient.featuretest.PlayMenusQa.click;
import static io.github.arcadiumclient.featuretest.PlayMenusQa.hasText;
import static io.github.arcadiumclient.featuretest.PlayMenusQa.textNode;
import static io.github.arcadiumclient.featuretest.PlayMenusQa.useScreen;
import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.object;
import static io.github.arcadiumclient.featuretest.Visual033Qa.properties;
import static io.github.arcadiumclient.featuretest.Visual033Qa.rectangle;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;

import com.mojang.authlib.GameProfile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

public final class Cape038Qa {
    private static final String CHANGER = "io.github.arcadiumclient.runtime.client.skins.SkinChanger";
    private static final String UPLOAD = "io.github.arcadiumclient.runtime.client.skins.SkinUploadClient";
    private static final String OFFLINE_ERROR = "Sign in to your Minecraft account to manage your capes.";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static Screen parent, screen;
    private static DisplayMode displayMode;
    private static int stage, ticks, totalTicks, width, height, requests;
    private static boolean begun, done, restored, fullscreen;

    private Cape038Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        isolated(mc);
        require(mc.world == null, "Cape menu QA must stay outside a world");
        try {
            if (!begun) begin(mc, checks);
            require(++totalTicks < 1200 && ++ticks < 180, "Cape menu QA timed out at stage " + stage);
            if (ticks < 12) return false;
            if (screen != null && mc.screen == screen) useScreen(screen);
            switch (stage) {
                case 0 -> {
                    if (mc.width != 1280 || mc.height != 720) return false;
                    require(mc.screen == parent, "Resizing replaced the Cape QA main menu");
                    useScreen(parent);
                    click("Skins");
                }
                case 1 -> {
                    if (mc.screen == parent) return false;
                    require(mc.screen != null && mc.screen.getClass().getSimpleName().equals("SkinChangerScreen"),
                            "The main-menu Skins control did not open the real Skin Changer");
                    screen = mc.screen;
                    if (call(screen, "getBusy") != null) return false;
                    useScreen(screen);
                    require(Boolean.FALSE.equals(call(screen, "getCapeTab")), "Skin Changer did not open its Skins tab");
                    RESULT.put("normalSkinsBounds", bounds(mc, List.of("Skins", "Capes", "Import PNG", "Classic", "Slim", "Save", "Delete", "Apply to ArcadiumTest", "Done")));
                    screenshot(mc, "cape038-skins-normal.png");
                }
                case 2 -> click("Capes");
                case 3 -> {
                    verifyOffline(mc);
                    require(disabled("Apply cape"), "An offline account can apply a cape before loading owned capes");
                    click("Refresh capes");
                }
                case 4 -> {
                    verifyOffline(mc);
                    RESULT.put("normalCapeBounds", bounds(mc, List.of("Skins", "Capes", "Refresh capes", "Apply cape", OFFLINE_ERROR, "Done")));
                    RESULT.put("offlineGuardAndRefresh", true);
                    screenshot(mc, "cape038-capes-normal.png");
                }
                case 5 -> requestSize(854, 480);
                case 6 -> {
                    if (mc.width != 854 || mc.height != 480) return false;
                    verifyOffline(mc);
                    RESULT.put("smallCapeBounds", bounds(mc, List.of("Skins", "Capes", "Refresh capes", "Apply cape", OFFLINE_ERROR, "Done")));
                    screenshot(mc, "cape038-capes-small.png");
                }
                case 7 -> click("Skins");
                case 8 -> {
                    require(Boolean.FALSE.equals(call(screen, "getCapeTab")), "Switching back did not restore the Skins tab");
                    require(call(screen, "getError") == null && hasText("Import PNG") && !hasText("Refresh capes"),
                            "The Cape tab left its error or controls on the Skins tab");
                    RESULT.put("smallSkinsBounds", bounds(mc, List.of("Skins", "Capes", "Import PNG", "Classic", "Slim", "Save", "Delete", "Apply to ArcadiumTest", "Done")));
                    screenshot(mc, "cape038-skins-small.png");
                }
                case 9 -> click("Capes");
                case 10 -> {
                    verifyOffline(mc);
                    RESULT.put("tabRoundTrip", true);
                    click("Done");
                }
                case 11 -> {
                    if (mc.screen != parent) return false;
                    restore(mc);
                }
                case 12 -> {
                    if (mc.width != width || mc.height != height || mc.isFullscreen() != fullscreen) return false;
                    require(mc.screen == parent, "Done did not restore the original main menu");
                    require(requestCount() == requests, "Offline Cape UI started a Minecraft appearance request");
                    RESULT.put("appearanceRequests", 0);
                    RESULT.put("originalPixels", List.of(width, height));
                    RESULT.put("restored", true);
                    RESULT.put("passed", true);
                    done = true;
                    return true;
                }
                default -> throw new IllegalStateException("Unknown Cape menu QA stage");
            }
            stage++; ticks = 0;
            return false;
        } catch (Exception failure) {
            if (begun) try { restore(mc); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        require(mc.screen != null && mc.screen.getClass().getSimpleName().equals("ArcadiumMainMenu"),
                "Cape QA must start at the actual Arcadium main menu");
        parent = mc.screen;
        width = mc.width; height = mc.height; fullscreen = mc.isFullscreen(); displayMode = Display.getDisplayMode();
        requests = requestCount();
        checks.put("cape038Menu", RESULT);
        RESULT.put("screenshots", CAPTURES);
        begun = true;
        if (fullscreen) mc.toggleFullscreen();
        requestSize(1280, 720);
    }

    private static void verifyOffline(Minecraft mc) throws Exception {
        require(mc.screen == screen && Boolean.TRUE.equals(call(screen, "getCapeTab")), "The real Cape tab is not active");
        require(OFFLINE_ERROR.equals(call(screen, "getError")) && hasText(OFFLINE_ERROR), "Cape UI did not display its friendly offline guard");
        require(Boolean.FALSE.equals(call(screen, "getCapesLoaded")) && ((List<?>) call(screen, "getCapes")).isEmpty(),
                "Offline Cape UI fabricated owned capes");
        require(requestCount() == requests, "An offline Cape action sent an appearance request");
    }

    private static boolean disabled(String label) throws Exception {
        try { return properties(textNode(label, true)).containsKey("Disabled"); }
        catch (IllegalStateException missingAction) { return hasText(label); }
    }

    private static Map<String, Object> bounds(Minecraft mc, List<String> labels) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String label : labels) result.put(label, rectangle(textNode(label, false), mc, label));
        return result;
    }

    private static void screenshot(Minecraft mc, String name) {
        capture(mc, name); CAPTURES.add(name);
    }

    private static void requestSize(int targetWidth, int targetHeight) throws Exception {
        Display.setDisplayMode(new DisplayMode(targetWidth, targetHeight));
        try {
            Class<?> display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
            long handle = (long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
            Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_SetWindowSize", long.class, int.class, int.class)
                    .invoke(null, handle, targetWidth, targetHeight);
        } catch (ClassNotFoundException ignored) { }
    }

    private static void restore(Minecraft mc) throws Exception {
        if (restored) return;
        if (mc.screen != parent) mc.openScreen(parent);
        if (mc.isFullscreen() != fullscreen) mc.toggleFullscreen();
        if (fullscreen) Display.setDisplayMode(displayMode);
        else requestSize(width, height);
        restored = true;
    }

    public static void verifyWorld(Minecraft mc, Map<String, Object> checks) throws Exception {
        isolated(mc);
        require(mc.isSingleplayer() && mc.world != null && mc.player != null, "Cape texture QA requires its isolated local world");
        Object changer = object(CHANGER);
        require(field(changer, "cape") == null && field(changer, "capeOwner") == null && Boolean.FALSE.equals(field(changer, "capeOverride")),
                "Cape QA must not replace an existing local cape preview");
        UUID uuid = mc.getSession().getProfile().getId();
        require(uuid != null, "The isolated Cape account is missing its UUID");
        UUID other = UUID.fromString("c7bf3ba6-56f3-4df8-a767-365bf9763563");
        require(!uuid.equals(other), "The other-account Cape fixture must have a different UUID");
        int before = requestCount();
        PlayerInfo info = new QuietPlayerInfo(new GameProfile(uuid, "ArcadiumTest"));
        PlayerInfo unrelated = new QuietPlayerInfo(new GameProfile(other, "CapeFixtureOther"));
        require(info.getCapeTexture() == null && unrelated.getCapeTexture() == null, "Detached Cape probes did not start without textures");
        byte[] png = png();
        boolean currentPlayer = uuid.equals(mc.player.getUuid());
        Identifier uploaded;
        try {
            call(changer, "refreshCape", uuid, png);
            require(Boolean.TRUE.equals(stat(CHANGER, "hasCapeOverride", uuid)), "Cape upload did not enable its local override");
            uploaded = (Identifier) stat(CHANGER, "capeTextureFor", uuid);
            require(uploaded != null && uploaded.toString().startsWith("arcadium:capes/"), "Cape upload did not register an Arcadium texture");
            require(uploaded.equals(info.getCapeTexture()), "The real PlayerInfo mixin did not return the uploaded cape");
            if (currentPlayer) require(uploaded.equals(mc.player.getCapeTextureLocation()), "The real client-player mixin did not return the uploaded cape");
            require(Boolean.FALSE.equals(stat(CHANGER, "hasCapeOverride", other)) && unrelated.getCapeTexture() == null,
                    "Cape override leaked to another UUID");
            call(changer, "refreshCape", other, png);
            call(changer, "clearCapePreview", other);
            require(uploaded.equals(stat(CHANGER, "capeTextureFor", uuid)), "An inactive account replaced or cleared the active cape");

            call(changer, "refreshCape", uuid, null);
            require(Boolean.TRUE.equals(stat(CHANGER, "hasCapeOverride", uuid)) && stat(CHANGER, "capeTextureFor", uuid) == null,
                    "Selecting no cape did not retain an intentional null override");
            require(info.getCapeTexture() == null, "PlayerInfo ignored the intentional no-cape selection");
            if (currentPlayer) require(mc.player.getCapeTextureLocation() == null, "The client player ignored the intentional no-cape selection");

            call(changer, "clearCapePreview", uuid);
            require(Boolean.FALSE.equals(stat(CHANGER, "hasCapeOverride", uuid)) && stat(CHANGER, "capeTextureFor", uuid) == null,
                    "Clearing the Cape preview did not restore normal texture lookup");
            require(info.getCapeTexture() == null, "Clearing the Cape preview left a stale PlayerInfo texture");
            require(requestCount() == before, "Local Cape texture probes sent an appearance API request");
            checks.put("cape038Textures", Map.of("passed", true, "generatedPngDimensions", List.of(64, 32),
                    "playerInfoMixin", true, "currentPlayerUuidMatched", currentPlayer, "otherUuidExcluded", true,
                    "intentionalNoCape", true, "normalLookupRestored", true, "appearanceRequests", 0));
        } finally {
            call(changer, "clearCapePreview", uuid);
        }
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y <= 16; y++) for (int x = 1; x <= 10; x++)
            image.setRGB(x, y, x < 6 ? 0xFF3AA0FF : 0xFF36F1B0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        require(ImageIO.write(image, "PNG", bytes), "Cape QA could not encode its owned PNG fixture");
        return bytes.toByteArray();
    }

    private static void isolated(Minecraft mc) throws Exception {
        require(Boolean.getBoolean("arcadium.forgeQa") && "ArcadiumTest".equals(mc.getSession().getUsername())
                && "0".equals(mc.getSession().getAccessToken()), "Cape QA requires the isolated offline fixture");
        Path game = mc.gameDir.toPath().toRealPath();
        require(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath().equals(game), "Cape QA must use its exact isolated game directory");
        require(FabricLoader.getInstance().getConfigDir().toRealPath().startsWith(game), "Cape QA configuration escaped its game fixture");
    }

    private static int requestCount() throws Exception { return ((Number) stat(UPLOAD, "requestCount")).intValue(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class QuietPlayerInfo extends PlayerInfo {
        private QuietPlayerInfo(GameProfile profile) { super(profile); }
        @Override protected void registerTextures() { }
    }
}
