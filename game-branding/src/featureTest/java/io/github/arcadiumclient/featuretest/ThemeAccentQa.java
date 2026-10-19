package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

public final class ThemeAccentQa {
    private static final String CONFIG = "org.polyfrost.oneconfig.internal.ThemeConfig";
    private static final String ACCENT = "org.polyfrost.oneconfig.internal.ui.themes.ThemeAccent";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Object> SAVED = new LinkedHashMap<>();
    private static Object tree;
    private static Screen parent;
    private static Path shared;
    private static int stage, ticks, width, height;
    private static boolean begun, done;
    private static int[] pausedFooter, sharpFooter;
    private static int sampleLeft, sampleRight, sampleY;

    private ThemeAccentQa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Theme QA requires the isolated offline session");
        if (!begun) {
            begun = true; checks.put("themeAccent", RESULT); parent = mc.screen;
            Path game = mc.gameDir.toPath().toRealPath();
            Path storage = Path.of(System.getProperty("arcadium.launcherDataDir")).toAbsolutePath().normalize();
            require(storage.startsWith(game), "Theme QA must not access the real launcher profile");
            shared = storage.resolve("theme-accent.json");
            tree = call(object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry"), "findTree", "themes.json");
            require(tree != null, "Themes was not registered");
            for (String key : List.of("accentColor", "gradientAccent", "gradientPreset", "gradientSpeed", "gradientSmoothness", "customGradient", "gradientStart", "gradientEnd", "animations"))
                SAVED.put(key, stat("org.polyfrost.oneconfig.api.config.v1.Config", "copyDefault", field(prop(key), "type"), call(prop(key), "get")));
            require(Boolean.TRUE.equals(SAVED.get("gradientAccent")) && Boolean.FALSE.equals(SAVED.get("customGradient")), "Gradient defaults are incorrect");
            require(((Number) SAVED.get("gradientPreset")).intValue() == 0, "Arcadium was not the default preset");
            require(((Number) call(snapshot(), "start")).intValue() == 0xFF3AA0FF && ((Number) call(snapshot(), "end")).intValue() == 0xFF36F1B0, "Default RGB values did not match the reference");
            width = mc.width; height = mc.height;
            Display.setDisplayMode(new DisplayMode(1600, 900));
            open();
        }
        require(++ticks < 160, "Theme QA timed out at stage " + stage);
        if (ticks < 20) return false;
        switch (stage) {
            case 0 -> {
                require(mc.screen != null && hasText(mc.screen, "Use gradient accent color"), "The gradient toggle was not visible in Themes");
                require(hasText(mc.screen, "Gradient Speed") && hasText(mc.screen, "Gradient Smoothness"), "Gradient motion controls were not visible in Themes");
                require("HIDDEN".equals(call(prop("gradientStart"), "getDisplay").toString()), "Custom colors were visible by default");
                capture(mc, "themes-arcadium.png");
                List<?> presets = (List<?>) stat("org.polyfrost.oneconfig.internal.ui.themes.GradientPresets", "all");
                require(presets.size() == 256, "The preset catalog is incomplete");
                for (int i = 0; i < presets.size(); i++) {
                    set("gradientPreset", i);
                    Object expected = presets.get(i), actual = snapshot();
                    require(call(expected, "name").equals(call(actual, "preset"))
                        && call(expected, "start").equals(call(actual, "start"))
                        && call(expected, "end").equals(call(actual, "end")), "Preset did not update the live accent: " + i);
                }
                RESULT.put("all256PresetsApplied", true);
                set("gradientPreset", 0);
            }
            case 1 -> {
                click(mc.screen, "Arcadium");
                RESULT.put("presetPickerOpened", true);
            }
            case 2 -> {
                capture(mc, "themes-presets.png");
                mc.openScreen(parent);
                set("customGradient", true);
                require("SHOWN".equals(call(prop("gradientStart"), "getDisplay").toString()), "Enabling custom colors did not reveal its controls");
                set("gradientStart", 0xFFF3A563); set("gradientEnd", 0xFFF077A1);
                require(((Number) call(snapshot(), "start")).intValue() == 0xFFF3A563
                    && ((Number) call(snapshot(), "end")).intValue() == 0xFFF077A1, "Custom RGB values were not applied");
                open();
            }
            case 3 -> {
                capture(mc, "themes-custom.png");
                require(Files.isRegularFile(shared), "The launcher shared accent was not written");
                var json = new JsonParser().parse(Files.readString(shared)).getAsJsonObject();
                require(json.get("custom").getAsBoolean() && "#F3A563".equals(json.get("start").getAsString())
                    && "#F077A1".equals(json.get("end").getAsString()), "The launcher received stale custom colors");
                set("gradientAccent", false);
                require(!((Boolean) call(snapshot(), "gradient")), "Solid mode did not disable gradients");
                require("HIDDEN".equals(call(prop("customGradient"), "getDisplay").toString()), "Custom gradient controls remained visible in solid mode");
            }
            case 4 -> {
                capture(mc, "themes-solid.png");
                set("gradientAccent", true); set("customGradient", false); set("gradientPreset", 193); set("animations", false);
                set("gradientSpeed", 2.5f); set("gradientSmoothness", 35f);
                Object active = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
                require(Boolean.TRUE.equals(call(active, "save", tree)), "Theme preferences could not be saved");
                String expected = (String) call(snapshot(), "preset");
                staticField(CONFIG, "gradientPreset").setInt(null, 0);
                require(Boolean.TRUE.equals(call(field(active, "backend"), "load", tree)), "Theme preferences could not be reloaded");
                stat("org.polyfrost.oneconfig.internal.ui.themes.ProviderKt", "updateAccent");
                require(expected.equals(call(snapshot(), "preset")), "The selected preset did not survive persistence");
                require(Boolean.FALSE.equals(call(snapshot(), "animate")), "Reduced motion was not retained");
                require(((Number) call(snapshot(), "speed")).floatValue() == 2.5f
                    && ((Number) call(snapshot(), "smoothness")).floatValue() == 35f, "Gradient motion controls did not survive persistence");
                RESULT.put("persistenceAndReducedMotion", true);
                Object overlay = call(object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry"), "findTree", "arcadium-bedwars-overlay.json");
                require(call(overlay, "getProp", "prefixStart") == null && call(overlay, "getProp", "prefixEnd") == null, "Bedwars Overlay kept separate accent colors");
            }
            case 5 -> {
                var sharedJson = new JsonParser().parse(Files.readString(shared)).getAsJsonObject();
                require(sharedJson.get("speed").getAsFloat() == 2.5f && sharedJson.get("smoothness").getAsFloat() == 35f,
                    "The launcher did not receive the new gradient controls");
                set("gradientAccent", true); set("customGradient", true); set("animations", true);
                set("gradientStart", 0xFFFF4000); set("gradientEnd", 0xFF00BFFF);
                set("gradientSpeed", 0f); set("gradientSmoothness", 100f);
                mc.openScreen(parent);
                stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", object("org.polyfrost.oneconfig.internal.ui.navigation.graph.ModsRoute"));
            }
            case 6 -> {
                Object card = null;
                for (String name : List.of("NoRender", "Arcadium Players", "GUI Scale", "Nick Hider", "AutoText", "Toggle Sprint", "FPS")) {
                    try { card = textNode(mc.screen, name, true); break; }
                    catch (IllegalStateException absent) { }
                }
                if (card == null) capture(mc, "mods-gradient-diagnostic.png");
                require(card != null, "No production module card is visible");
                Object bounds = call(card, "getBoundsInRoot");
                float left = ((Number) call(bounds, "getLeft")).floatValue();
                float right = ((Number) call(bounds, "getRight")).floatValue();
                sampleLeft = Math.round(left + (right - left) * 0.2f);
                sampleRight = Math.round(left + (right - left) * 0.8f);
                sampleY = Math.round(((Number) call(bounds, "getBottom")).floatValue()) - 6;
                RESULT.put("footerSample", List.of(sampleLeft, sampleY, sampleRight));
                capture(mc, "mods-gradient-paused-a.png");
            }
            case 7 -> {
                capture(mc, "mods-gradient-paused-b.png");
            }
            case 8 -> {
                pausedFooter = footer(mc, "mods-gradient-paused-a.png");
                require(spread(pausedFooter) > 30, "The module button was still a solid color");
                require(Arrays.equals(pausedFooter, footer(mc, "mods-gradient-paused-b.png")), "Speed 0 did not freeze the module gradient");
                set("gradientSpeed", 1f);
            }
            case 9 -> {
                capture(mc, "mods-gradient-flow.png");
            }
            case 10 -> {
                double expectedPhase = ((Number) stat(ACCENT, "phaseAt", System.nanoTime())).doubleValue();
                double paintedPhase = ((Number) call(staticField("org.polyfrost.oneconfig.internal.ui.themes.ProviderKt", "_phase$delegate").get(null), "getFloatValue")).doubleValue();
                double phaseError = Math.abs(expectedPhase - paintedPhase);
                phaseError = Math.min(phaseError, 1 - phaseError);
                require(phaseError < 0.025, "Compose prewarming shifted the gradient animation clock");
                RESULT.put("sharedGradientClockError", phaseError);
                int motion = difference(pausedFooter, footer(mc, "mods-gradient-flow.png"));
                require(motion > 10, "The actual module button gradient did not flow");
                RESULT.put("moduleFooterMotion", motion);
                set("gradientSpeed", 0f); set("gradientSmoothness", 0f);
            }
            case 11 -> {
                capture(mc, "mods-gradient-sharp.png");
            }
            case 12 -> {
                sharpFooter = footer(mc, "mods-gradient-sharp.png");
                set("gradientSmoothness", 100f);
            }
            case 13 -> {
                capture(mc, "mods-gradient-soft.png");
            }
            case 14 -> {
                int softness = difference(sharpFooter, footer(mc, "mods-gradient-soft.png"));
                require(softness > 10, "Smoothness did not change the actual module button blend");
                RESULT.put("moduleFooterSoftness", softness);
                set("gradientAccent", false);
            }
            case 15 -> {
                capture(mc, "mods-accent-solid.png");
            }
            case 16 -> {
                require(spread(footer(mc, "mods-accent-solid.png")) < 20, "Solid accent mode still drew a module gradient");
                set("gradientAccent", true); set("gradientSmoothness", 100f); set("gradientSpeed", 1f);
                mc.openScreen(new net.minecraft.client.gui.screen.VideoOptionsScreen(parent, mc.options));
            }
            case 17 -> {
                PlayMenusQa.useScreen(mc.screen);
                Object slider = null;
                for (Object node : PlayMenusQa.allNodes()) {
                    var values = PlayMenusQa.properties(node);
                    if (values.get("ContentDescription") instanceof java.util.Collection<?> names && names.contains("Max Framerate") && values.containsKey("SetProgress")) slider = node;
                }
                require(slider != null, "The production video slider was not found");
                var values = PlayMenusQa.properties(slider);
                Object range = call(values.get("ProgressBarRangeInfo"), "getRange");
                Object action = call(values.get("SetProgress"), "getAction");
                call(action, "invoke", ((Number) call(range, "getEndInclusive")).floatValue());
                Object bounds = call(slider, "getBoundsInRoot");
                float left = ((Number) call(bounds, "getLeft")).floatValue(), right = ((Number) call(bounds, "getRight")).floatValue();
                float bottom = ((Number) call(bounds, "getBottom")).floatValue(), top = ((Number) call(bounds, "getTop")).floatValue();
                sampleLeft = Math.round(left + (right-left)*.2f); sampleRight = Math.round(left + (right-left)*.8f);
                sampleY = Math.round(bottom - (bottom-top)*11f/48f);
                RESULT.put("videoSliderSample", List.of(sampleLeft, sampleY, sampleRight));
            }
            case 18 -> capture(mc, "video-gradient-flow-a.png");
            case 19 -> capture(mc, "video-gradient-flow-b.png");
            case 20 -> {
                int[] first = footer(mc, "video-gradient-flow-a.png"), second = footer(mc, "video-gradient-flow-b.png");
                require(spread(first) > 30 && difference(first, second) > 10, "The video slider accent did not draw a flowing spatial gradient");
                RESULT.put("videoSliderGradientAndMotion", true);
                set("gradientAccent", false);
            }
            case 21 -> capture(mc, "video-gradient-solid.png");
            case 22 -> {
                require(spread(footer(mc, "video-gradient-solid.png")) < 20, "The video slider did not return to a solid color");
                for (var entry : SAVED.entrySet()) set(entry.getKey(), entry.getValue());
                mc.openScreen(parent); Display.setDisplayMode(new DisplayMode(width, height));
            }
            case 23 -> {
                if (mc.width != width || mc.height != height) return false;
                RESULT.put("completed", true); done = true; return true;
            }
            default -> throw new IllegalStateException("Unknown theme QA stage");
        }
        stage++; ticks = 0; return false;
    }

    private static Object prop(String id) throws Exception { return call(tree, "getProp", id); }
    private static int[] footer(Minecraft mc, String file) throws Exception {
        var image = ImageIO.read(mc.gameDir.toPath().resolve("screenshots").resolve(file).toFile());
        require(sampleLeft >= 0 && sampleRight < image.getWidth() && sampleY >= 0 && sampleY < image.getHeight(), "The module footer sample is outside the framebuffer");
        return image.getRGB(sampleLeft, sampleY, sampleRight - sampleLeft, 1, null, 0, sampleRight - sampleLeft);
    }
    private static int colorDifference(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }
    private static int spread(int[] pixels) { int maximum = 0; for (int color : pixels) maximum = Math.max(maximum, colorDifference(color, pixels[0])); return maximum; }
    private static int difference(int[] a, int[] b) { int total = 0; for (int i = 0; i < a.length; i++) total += colorDifference(a[i], b[i]); return total / a.length; }
    private static void set(String id, Object value) throws Exception { call(prop(id), "setAsReferential", value); }
    private static Object snapshot() throws Exception { return stat(ACCENT, "snapshot"); }
    private static void open() throws Exception { stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", object("org.polyfrost.oneconfig.internal.ui.navigation.graph.ThemesRoute")); }
    private static void capture(Minecraft mc, String file) { Screenshot.take(mc.gameDir, file, mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
