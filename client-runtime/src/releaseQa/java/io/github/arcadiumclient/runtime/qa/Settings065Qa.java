// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsOverlay;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsOverlayConfig;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsStats;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsTabLineFormatter;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.qa.mixin.MouseInputAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModsRoute;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.OptimizationsRoute;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.OptimizationsGraph;
import org.polyfrost.oneconfig.internal.ui.shell.LocalNavController;
import org.polyfrost.oneconfig.internal.ui.shell.ShellState;
import org.polyfrost.oneconfig.api.platform.v1.Platform;

public final class Settings065Qa {
    private static final String BEDWARS = "arcadium-bedwars-overlay.json";
    private static final String BETA = "arcadium-optimizations-beta.json";
    private static final String FORMAT = "%STAR% %RANK% %USERNAME% : %FKDR% | %WLR%";
    private static final String HOSTS = "proxy065.test.invalid, *.edge065.test.invalid";
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, Object> bedwarsOriginal = new LinkedHashMap<>(), betaOriginal = new LinkedHashMap<>();
    private static int ticks, phase, wait;
    private static boolean done;
    private static Screen parent, settings;
    private static String customLine, metricLine;

    private Settings065Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.settings065.output") != null; }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Settings QA requires the isolated offline fixture");
            require(mc.world == null, "Settings QA must not join a server or world");
            require(++ticks < 2400, "Settings QA timed out at phase " + phase);
            if (phase == 0) {
                if (ticks < 65 || mc.screen == null) return;
                ClientPreferences.setSetupComplete(true);
                parent = mc.screen;
                var bedwars = BedwarsOverlayConfig.INSTANCE.getTree();
                for (String key : List.of("showRank", "displayedStat", "useCustomFormat", "tabFormat", "additionalHypixelAddresses")) {
                    var prop = bedwars.getProp(key);
                    require(prop != null && prop.getMetadata("visualizer") != null, "Bedwars setting has no module GUI control: " + key);
                    bedwarsOriginal.put(key, prop.get());
                }
                var beta = BetaOptimizationsConfig.INSTANCE.getTree();
                for (String key : List.of("minimumPlayers", "gpuParticles", "minimumGpuParticles", "retainedParticleRecipes", "fastInvisibleHolograms", "retainedScoreboardPreparation", "directFramebufferPresentation")) {
                    var prop = beta.getProp(key);
                    require(prop != null && prop.getMetadata("visualizer") != null, "Beta setting has no module GUI control: " + key);
                    betaOriginal.put(key, prop.get());
                }
                require(!BedwarsOverlayConfig.INSTANCE.useCustomFormat, "The fresh fixture must start with Custom Format off");
                require(!BetaOptimizationsConfig.getGpuParticles() && BetaOptimizationsConfig.getMinimumGpuParticles() == 64
                    && BetaOptimizationsConfig.getMinimumPlayers() == 24, "The new Beta defaults changed");
                report.put("newBetaDefaults", Map.of("gpuParticles", false, "minimumGpuParticles", 64, "minimumPlayers", 24));
                OneConfigUI.open(ModsRoute.INSTANCE);
                next(); return;
            }
            if (++wait < 25) return;
            switch (phase) {
                case 1 -> {
                    if (!LocalNavController.INSTANCE.isReady()) return;
                    search("Bedwars Overlay"); next();
                }
                case 2 -> {
                    if (!readyCard("Bedwars Overlay")) return;
                    require(ConfigRegistry.INSTANCE.findById(BEDWARS).getOnOpen() == null, "Bedwars module card bypasses Arcadium Options");
                    capture("settings065-bedwars-card.png"); click(card("Bedwars Overlay")); next();
                }
                case 3 -> {
                    requireMod(BEDWARS); settings = mc.screen;
                    report.put("bedwarsCardEntry", true);
                    search("Tab Format"); next();
                }
                case 4 -> {
                    if (!hiddenSetting("tabFormat", "Tab Format")) return;
                    report.put("customFormatHiddenByDefault", true);
                    search("Show Rank"); next();
                }
                case 5 -> { if (!ready("Show Rank")) return; clickSwitch("Show Rank"); next(); }
                case 6 -> {
                    require(!BedwarsOverlayConfig.INSTANCE.showRank, "The Show Rank GUI control did not update the live setting");
                    report.put("showRankGuiEdit", true); search("Displayed Stat"); next();
                }
                case 7 -> { if (!ready("Displayed Stat")) return; click(text("FKDR")); next(); }
                case 8 -> { if (!ready("WLR")) return; semanticClick(text("WLR")); next(); }
                case 9 -> {
                    require(BedwarsOverlayConfig.INSTANCE.displayedStat == 3, "The Displayed Stat dropdown did not select WLR");
                    report.put("displayedStatGuiSelection", "WLR"); capture("settings065-bedwars-stat.png");
                    search("Custom Format"); next();
                }
                case 10 -> { if (!ready("Custom Format")) return; clickSwitch("Custom Format"); next(); }
                case 11 -> {
                    require(BedwarsOverlayConfig.INSTANCE.useCustomFormat, "The Custom Format GUI switch did not update the live setting");
                    search("Displayed Stat"); next();
                }
                case 12 -> {
                    if (!hiddenSetting("displayedStat", "Displayed Stat")) return;
                    report.put("displayedStatHiddenWithCustomFormat", true); search("Tab Format"); next();
                }
                case 13 -> { if (!ready("Tab Format")) return; editInput("Tab Format", "&b" + FORMAT); next(); }
                case 14 -> {
                    require(BedwarsOverlayConfig.INSTANCE.tabFormat.equals("&b" + FORMAT), "The Tab Format text control did not update the setting");
                    editInput("Tab Format", FORMAT); next();
                }
                case 15 -> {
                    require(BedwarsOverlayConfig.INSTANCE.tabFormat.equals(FORMAT), "The multi-value Tab Format was not retained");
                    report.put("tabFormatGuiEdit", FORMAT); capture("settings065-bedwars-format.png");
                    search("Show Rank"); next();
                }
                case 16 -> { if (!ready("Show Rank")) return; clickSwitch("Show Rank"); next(); }
                case 17 -> {
                    require(BedwarsOverlayConfig.INSTANCE.showRank, "Show Rank could not be restored through its control");
                    search("Additional Hypixel Addresses"); next();
                }
                case 18 -> { if (!ready("Additional Hypixel Addresses")) return; editInput("Additional Hypixel Addresses", HOSTS); next(); }
                case 19 -> {
                    require(BedwarsOverlayConfig.INSTANCE.additionalHypixelAddresses.equals(HOSTS), "The proxy host text control did not update the setting");
                    require(BedwarsOverlay.onHypixel("proxy065.test.invalid:25565"), "An edited exact proxy host did not apply immediately");
                    require(BedwarsOverlay.onHypixel("lobby.edge065.test.invalid") && BedwarsOverlay.onHypixel("edge065.test.invalid"), "An edited wildcard proxy rule did not apply immediately");
                    require(!BedwarsOverlay.onHypixel("proxy065.test.invalid.attacker.invalid"), "Proxy rules matched outside the configured domain");
                    capture("settings065-bedwars-proxies.png"); editInput("Additional Hypixel Addresses", "other065.test.invalid"); next();
                }
                case 20 -> {
                    require(!BedwarsOverlay.onHypixel("proxy065.test.invalid") && !BedwarsOverlay.onHypixel("lobby.edge065.test.invalid")
                        && BedwarsOverlay.onHypixel("other065.test.invalid") && BedwarsOverlay.onHypixel("mc.hypixel.net"), "Changing proxy rules did not replace the live host policy");
                    report.put("proxyRulesApplyImmediately", true);
                    LocalNavController.INSTANCE.getWrapper().navigate(ModsRoute.INSTANCE, true);
                    next();
                }
                case 21 -> {
                    if (!ready("Optimizations (Beta)")) return;
                    click(text("Optimizations (Beta)")); next();
                }
                case 22 -> {
                    Object route = LocalNavController.INSTANCE.getWrapper().getCurrentRoute();
                    require(route instanceof OptimizationsRoute || route instanceof OptimizationsGraph, "The Beta sidebar link did not open its settings page");
                    report.put("betaDedicatedSidebarEntry", true);
                    search("Minimum Visible Players"); next();
                }
                case 23 -> {
                    require(mc.screen == settings, "The Beta settings opened a separate Minecraft screen"); next();
                }
                case 24 -> { if (!ready("Minimum Visible Players")) return; editInput("Minimum Visible Players", "7"); next(); }
                case 25 -> {
                    require(BetaOptimizationsConfig.getMinimumPlayers() == 7, "The player pipeline threshold control did not update the runtime value");
                    report.put("minimumPlayersGuiEdit", 7); capture("settings065-beta-player-threshold.png");
                    search("GPU Particle Batches"); next();
                }
                case 26 -> { if (!ready("GPU Particle Batches")) return; clickSwitch("GPU Particle Batches"); next(); }
                case 27 -> {
                    require(BetaOptimizationsConfig.getGpuParticles(), "The GPU Particle Batches switch did not update the runtime value");
                    report.put("gpuParticleBatchesGuiEdit", true); capture("settings065-beta-particles.png");
                    search("Minimum Visible Particles"); next();
                }
                case 28 -> { if (!ready("Minimum Visible Particles")) return; editInput("Minimum Visible Particles", "96"); next(); }
                case 29 -> {
                    require(BetaOptimizationsConfig.getMinimumGpuParticles() == 96, "The particle threshold control did not update the runtime value");
                    report.put("minimumVisibleParticlesGuiEdit", 96); capture("settings065-beta-particle-threshold.png");
                    search("Retained Particle Recipes"); next();
                }
                case 30 -> { if (!ready("Retained Particle Recipes")) return; clickSwitch("Retained Particle Recipes"); next(); }
                case 31 -> {
                    require(BetaOptimizationsConfig.getRetainedParticleRecipes(), "The retained particle control did not update the live value");
                    report.put("retainedParticleRecipesGuiEdit", true); search("Invisible Hologram Fast Path"); next();
                }
                case 32 -> { if (!ready("Invisible Hologram Fast Path")) return; clickSwitch("Invisible Hologram Fast Path"); next(); }
                case 33 -> {
                    require(BetaOptimizationsConfig.getFastInvisibleHolograms(), "The hologram control did not update the live value");
                    report.put("invisibleHologramsGuiEdit", true); capture("settings065-beta-holograms.png");
                    search("Retained Scoreboard Preparation"); next();
                }
                case 34 -> { if (!ready("Retained Scoreboard Preparation")) return; clickSwitch("Retained Scoreboard Preparation"); next(); }
                case 35 -> {
                    require(BetaOptimizationsConfig.getRetainedScoreboardPreparation(), "The scoreboard preparation control did not update the live value");
                    report.put("retainedScoreboardPreparationGuiEdit", true); search("Fast Frame Presentation"); next();
                }
                case 36 -> { if (!ready("Fast Frame Presentation")) return; clickSwitch("Fast Frame Presentation"); next(); }
                case 37 -> {
                    require(BetaOptimizationsConfig.getDirectFramebufferPresentation(), "The frame presentation control did not update the live value");
                    report.put("fastFramePresentationGuiEdit", true); capture("settings065-beta-presentation.png");
                    LocalNavController.INSTANCE.getWrapper().navigate(ModsRoute.INSTANCE, true); search("Minimum Visible Particles"); next();
                }
                case 38 -> {
                    if (!ready("Minimum Visible Particles")) return;
                    require(LocalNavController.INSTANCE.getWrapper().getCurrentRoute() instanceof ModsRoute, "The Mods search page did not open");
                    require(mc.screen == settings, "The Mods search page opened a separate Minecraft screen");
                    Object value = properties(input("Minimum Visible Particles")).get("EditableText");
                    require(value != null && "96".equals(call(value, "getText")), "Mods search did not retain the value edited from the Beta sidebar");
                    report.put("betaSettingsAlsoAccessibleFromModsSearch", true); capture("settings065-beta-mods-search.png"); next();
                }
                case 39 -> {
                    var stats = new BedwarsStats(new UUID(0, 65), "Settings065", 252, 3.75, 150, 40, false, false,
                        "MVP_PLUS", "MVP_PLUS", "SUPERSTAR", "RED", "GOLD", null, 127, 1.5, 300, 2.25);
                    var config = BedwarsOverlayConfig.INSTANCE;
                    customLine = BedwarsTabLineFormatter.format(stats, "Settings065", "§f", config.showRank, config.displayedStat, config.tabFormat);
                    metricLine = BedwarsTabLineFormatter.format(stats, "Settings065", "§f", false, config.displayedStat, null);
                    String plain = customLine.replaceAll("(?i)§[0-9a-fk-or]", "");
                    require(plain.contains("Settings065") && plain.contains("MVP") && plain.contains("3.75") && plain.contains("2.25")
                        && !plain.contains("%"), "The production formatter did not resolve the configured multi-value format");
                    require(!metricLine.contains("MVP") && metricLine.contains("2.25"), "Rank visibility or selected WLR stat was not applied by the formatter");
                    report.put("productionFormattedMockLine", customLine); report.put("productionRankHiddenWlrLine", metricLine);
                    mc.openScreen(new FormatPreview()); next();
                }
                case 40 -> {
                    capture("settings065-native-format-preview.png");
                    report.put("nativeFormatterPreview", true);
                    mc.openScreen(parent); finish(null);
                }
                default -> throw new IllegalStateException("Unknown settings QA phase");
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private static void next() { phase++; wait = 0; }
    private static void search(String query) { ShellState.INSTANCE.setSearchQuery(query); }
    private static boolean ready(String label) throws Exception {
        if (textOrNull(label) != null) return true;
        require(wait < 200, "The actual GUI control did not appear: " + label);
        return false;
    }
    private static Object card(String label) throws Exception {
        for (Object node : nodes()) if (textValues(node).contains(label)) {
            Object bounds = call(node, "getBoundsInRoot");
            if (number(bounds, "getLeft") > 200 && number(bounds, "getTop") > 100) return node;
        }
        return null;
    }
    private static boolean readyCard(String label) throws Exception {
        if (card(label) != null) return true;
        require(wait < 200, "The Mods card did not appear: " + label); return false;
    }
    private static boolean hiddenSetting(String key, String title) throws Exception {
        require(BedwarsOverlayConfig.INSTANCE.getTree().getProp(key).getDisplay() == Property.Display.HIDDEN, "The setting dependency was not applied: " + key);
        if (textOrNull(title) == null && textOrNull("Searching...") == null) return true;
        require(wait < 200, "A hidden dependent setting remained visible in search"); return false;
    }
    private static void requireMod(String id) {
        require(Minecraft.getInstance().screen.getClass().getSimpleName().equals("OneConfigUIScreen"), "A module opened a separate Minecraft screen");
        require(LocalNavController.INSTANCE.getWrapper().getCurrentRoute() instanceof ModConfigRoute route && route.getId().equals(id), "The module card did not open its settings route: " + id);
    }
    private static void clickSwitch(String title) throws Exception {
        Object label = text(title), bounds = call(label, "getBoundsInRoot");
        double top = number(bounds, "getTop"), left = number(bounds, "getLeft");
        Object target = null; double rightmost = -1;
        for (Object node : nodes()) if (properties(node).containsKey("OnClick")) {
            Object rect = call(node, "getBoundsInRoot");
            double x = (number(rect, "getLeft") + number(rect, "getRight")) / 2;
            double y = (number(rect, "getTop") + number(rect, "getBottom")) / 2;
            if (x > left + 100 && y >= top - 12 && y < top + 150 && x > rightmost) { target = node; rightmost = x; }
        }
        require(target != null, "The GUI switch target is missing: " + title); click(target);
    }
    private static void editInput(String title, String value) throws Exception {
        Object target = input(title);
        Object annotated = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class)
            .newInstance(value, List.of(), List.of());
        action(target, "SetText", annotated);
    }
    private static Object input(String title) throws Exception {
        Object label = call(text(title), "getBoundsInRoot");
        double labelY = (number(label, "getTop") + number(label, "getBottom")) / 2;
        Object target = null; double nearest = Double.POSITIVE_INFINITY;
        for (Object node : nodes()) if (properties(node).containsKey("SetText")) {
            Object rect = call(node, "getBoundsInRoot");
            double y = (number(rect, "getTop") + number(rect, "getBottom")) / 2;
            double distance = Math.abs(y - labelY);
            if (y > 100 && distance < nearest) { nearest = distance; target = node; }
        }
        require(target != null && nearest < 150, "The filtered setting has no editable GUI field: " + title);
        return target;
    }
    private static void semanticClick(Object node) throws Exception {
        while (node != null) {
            if (properties(node).containsKey("OnClick")) { action(node, "OnClick"); return; }
            node = call(node, "getParent");
        }
        throw new IllegalStateException("The selected menu item has no GUI click action");
    }
    private static void action(Object node, String key, Object... args) throws Exception {
        require(Boolean.TRUE.equals(call(call(properties(node).get(key), "getAction"), "invoke", args)), "GUI action rejected: " + key);
    }
    private static Object text(String value) throws Exception { Object result = textOrNull(value); require(result != null, "GUI text is missing: " + value); return result; }
    private static Object textOrNull(String value) throws Exception { for (Object node : nodes()) if (textValues(node).contains(value)) return node; return null; }
    private static List<String> textValues(Object node) throws Exception {
        List<String> result = new ArrayList<>();
        if (properties(node).get("Text") instanceof Collection<?> values) for (Object value : values) result.add((String) call(value, "getText"));
        return result;
    }
    private static List<Object> nodes() throws Exception {
        Object scene = field(Minecraft.getInstance().screen, "sceneOrNull");
        List<Object> owners = new ArrayList<>(); owners.add(field(scene, "mainOwner"));
        try { for (Object layer : (Collection<?>) field(scene, "layers")) owners.add(field(layer, "owner")); }
        catch (NoSuchFieldException ignored) { }
        List<Object> result = new ArrayList<>();
        for (Object owner : owners) result.addAll(descendants(call(call(owner, "getSemanticsOwner"), "getRootSemanticsNode")));
        return result;
    }
    private static List<Object> descendants(Object node) throws Exception {
        List<Object> result = new ArrayList<>(); result.add(node);
        for (Object child : (Collection<?>) call(node, "getChildren")) result.addAll(descendants(child));
        return result;
    }
    private static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object item : (Iterable<?>) call(node, "getConfig")) {
            var entry = (Map.Entry<?, ?>) item; result.put((String) call(entry.getKey(), "getName"), entry.getValue());
        }
        return result;
    }
    private static void click(Object node) throws Exception {
        Minecraft mc = Minecraft.getInstance(); Screen screen = mc.screen; Object rect = call(node, "getBoundsInRoot");
        double x = (number(rect, "getLeft") + number(rect, "getRight")) / 2, y = (number(rect, "getTop") + number(rect, "getBottom")) / 2;
        double ratio = Platform.screen().pixelRatio(); Mouse.setCursorPosition((int) Math.round(x * ratio), mc.height - (int) Math.round(y * ratio) - 1);
        int guiX = (int) Math.round(x * screen.width / (mc.width / ratio)), guiY = (int) Math.round(y * screen.height / (mc.height / ratio));
        ((MouseInputAccess) screen).release$press(guiX, guiY, 0); ((MouseInputAccess) screen).release$release(guiX, guiY, 0);
    }
    private static double number(Object target, String name) throws Exception { return ((Number) call(target, name)).doubleValue(); }
    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            try { method.setAccessible(true); return method.invoke(target, args); }
            catch (IllegalArgumentException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    private static void capture(String name) { Minecraft mc = Minecraft.getInstance(); Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }
    private static void restore() {
        for (var entry : bedwarsOriginal.entrySet()) BedwarsOverlayConfig.INSTANCE.getTree().getProp(entry.getKey()).setAs(entry.getValue());
        for (var entry : betaOriginal.entrySet()) BetaOptimizationsConfig.INSTANCE.getTree().getProp(entry.getKey()).setAs(entry.getValue());
        for (var entry : bedwarsOriginal.entrySet()) require(java.util.Objects.equals(entry.getValue(), BedwarsOverlayConfig.INSTANCE.getTree().getProp(entry.getKey()).get()), "A Bedwars setting was not restored: " + entry.getKey());
        for (var entry : betaOriginal.entrySet()) require(java.util.Objects.equals(entry.getValue(), BetaOptimizationsConfig.INSTANCE.getTree().getProp(entry.getKey()).get()), "A Beta setting was not restored: " + entry.getKey());
        BedwarsOverlayConfig.INSTANCE.save(); BetaOptimizationsConfig.INSTANCE.save();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(Throwable failure) {
        done = true;
        try { restore(); report.put("settingsRestored", true); } catch (Throwable restoreError) { if (failure == null) failure = restoreError; }
        report.put("passed", failure == null); report.put("phase", phase); report.put("syntheticOfflineFixture", true);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); capture("settings065-failure.png"); }
        try {
            Path output = Path.of(System.getProperty("arcadium.settings065.output")); Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static final class FormatPreview extends Screen {
        @Override public void render(int mouseX, int mouseY, float delta) {
            renderBackground();
            textRenderer.drawWithShadow("Bedwars Overlay: production formatter, synthetic stats", 24, 28, 0xFFFFFF);
            textRenderer.drawWithShadow("Configured multi-value format", 24, 60, 0xAAAAAA);
            textRenderer.drawWithShadow(customLine, 24, 80, 0xFFFFFF);
            textRenderer.drawWithShadow("Selected WLR with rank hidden", 24, 116, 0xAAAAAA);
            textRenderer.drawWithShadow(metricLine, 24, 136, 0xFFFFFF);
        }
    }
}
