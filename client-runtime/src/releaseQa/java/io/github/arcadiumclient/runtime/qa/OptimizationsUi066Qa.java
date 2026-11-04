// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
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
import java.util.Objects;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.oneconfig.internal.ui.api.BetaOptimizationPage;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModsRoute;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.OptimizationsGraph;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.OptimizationsRoute;
import org.polyfrost.oneconfig.internal.ui.shell.LocalNavController;
import org.polyfrost.oneconfig.internal.ui.shell.ShellState;

public final class OptimizationsUi066Qa {
    private static final String ID = "arcadium-optimizations-beta.json";
    private static final List<String> CATEGORIES = List.of("Hypixel", "Players", "Display");
    private static final List<String> OLD_IDS = List.of("cachedNameMasking", "cachedFontMetrics", "batchedVanillaFont", "gpuNameTags", "retainedNameTagScenes", "fastInvisibleHolograms",
        "retainedScoreboardPreparation", "gpuParticles", "retainedParticleRecipes", "retainedHudBatches", "minimumGpuLabels",
        "minimumGpuParticles", "gpuModelTransforms", "gpuPlayerPipeline", "gpuEquipmentPipeline", "minimumPlayers",
        "directFramebufferPresentation", "worldRenderScale", "worldResolution");
    private static final Map<String, Object> report = new LinkedHashMap<>(), originalValues = new LinkedHashMap<>();
    private static final List<Map<String, Object>> modeReports = new ArrayList<>();
    private static final Map<String, String> originalCategories = new LinkedHashMap<>();
    private static Function0<? extends List<String>> provider;
    private static List<String> latestDiagnostics = List.of();
    private static int ticks, wait, phase, mode, reads, mainReads;
    private static int originalMode;
    private static boolean originalLimitFps, originalVsync, initialized, done;
    private static String originalQuery;
    private static Screen parent, options;
    private static Map<String, Object> modeReport;

    private OptimizationsUi066Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.optimizationsUi066.output") != null; }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
                "Optimizations UI QA requires the isolated offline fixture");
            require(mc.world == null, "Optimizations UI QA must not join a server or world");
            require(++ticks < 2200, "Optimizations UI QA timed out at mode " + mode + ", phase " + phase);
            if (!initialized) {
                if (ticks < 65 || mc.screen == null) return;
                initialize(mc);
                return;
            }
            if (++wait < 14) return;
            switch (phase) {
                case 0 -> {
                    if (!LocalNavController.INSTANCE.isReady() || !ready("Optimizations (Beta)")) return;
                    if (options == null) options = mc.screen;
                    require(mc.screen == options && "OneConfigUIScreen".equals(options.getClass().getSimpleName()),
                        "The normal Mods entry did not open Arcadium Options");
                    capture("mods"); click(text("Optimizations (Beta)")); next();
                }
                case 1 -> {
                    requireBeta();
                    if (!ready("GPU Name Tags") || !ready("Hypixel")) return;
                    requireCategories();
                    require(ShellState.INSTANCE.getSelectedCategories().get(ID) == null, "A fresh page wrote a forced saved category");
                    require(textOrNull("GPU Model Transforms") == null && textOrNull("Fast Frame Presentation") == null,
                        "A fresh page did not show Hypixel settings first");
                    require(reads == mainReads, "The settings page still polls inline diagnostics");
                    requireNoDiagnostics(); capture("hypixel");
                    modeReport.put("freshCategory", "Hypixel"); modeReport.put("categories", CATEGORIES);
                    modeReport.put("mainPageDiagnosticReads", reads - mainReads);
                    click(text("Players")); next();
                }
                case 2 -> {
                    if (!ready("GPU Model Transforms")) return;
                    require("Players".equals(ShellState.INSTANCE.getSelectedCategories().get(ID)), "Players tab selection was not retained under the original config ID");
                    require(textOrNull("GPU Name Tags") == null, "Hypixel controls remained on the Players tab");
                    click(text("Display")); next();
                }
                case 3 -> {
                    if (!ready("Fast Frame Presentation")) return;
                    require("Display".equals(ShellState.INSTANCE.getSelectedCategories().get(ID)), "Display tab selection was not retained");
                    click(text("Players")); next();
                }
                case 4 -> {
                    if (!ready("GPU Model Transforms")) return;
                    search("Minimum Visible Players"); next();
                }
                case 5 -> {
                    if (!ready("Minimum Visible Players")) return;
                    require("Players".equals(ShellState.INSTANCE.getSelectedCategories().get(ID)), "Search replaced the saved category");
                    require(reads == mainReads, "Settings search started polling diagnostics");
                    requireNoDiagnostics(); capture("settings-search");
                    modeReport.put("searchUsesOriginalConfigId", true);
                    click(description("Open optimization diagnostics")); next();
                }
                case 6 -> {
                    if (!diagnosticsReady()) return;
                    require(mc.screen == options, "Diagnostics opened a separate Minecraft screen");
                    require("OptimizationDiagnosticsRoute".equals(LocalNavController.INSTANCE.getWrapper().getCurrentRoute().getClass().getSimpleName()),
                        "The title information button did not open the diagnostics route");
                    require(ShellState.INSTANCE.getSearchQuery().isEmpty(), "Diagnostics inherited the settings search");
                    require(textOrNull("Hypixel") == null && textOrNull("Players") == null && textOrNull("Display") == null,
                        "Diagnostics still contains the settings tabs");
                    require(descriptionOrNull("Open optimization diagnostics") == null, "Diagnostics contains a recursive information button");
                    capture("diagnostics");
                    modeReport.put("diagnosticsLiveReads", reads - mainReads);
                    modeReport.put("diagnosticLines", latestDiagnostics);
                    search("particle"); next();
                }
                case 7 -> {
                    var shown = displayedDiagnosticLines();
                    if (shown.isEmpty()) { require(wait < 120, "Diagnostics search returned no particle status"); return; }
                    require(shown.stream().allMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("particle")),
                        "Diagnostics search left unrelated status lines visible");
                    modeReport.put("diagnosticsSearch", shown); search("no-diagnostics-match-066"); next();
                }
                case 8 -> {
                    if (!ready("No matching diagnostics.")) return;
                    require(displayedDiagnosticLines().isEmpty(), "Unmatched diagnostics were still visible");
                    click(description("Go back")); next();
                }
                case 9 -> {
                    requireBeta();
                    if (!ready("Minimum Visible Players")) return;
                    require("Minimum Visible Players".equals(ShellState.INSTANCE.getSearchQuery()), "Back did not restore the settings search");
                    require("Players".equals(ShellState.INSTANCE.getSelectedCategories().get(ID)), "Back did not restore the Players category");
                    modeReport.put("backRestoresCategoryAndSearch", true); search(""); next();
                }
                case 10 -> {
                    if (!ready("GPU Model Transforms")) return;
                    requireCategories(); requireNoDiagnostics();
                    mainReads = reads; click(text("Mods")); next();
                }
                case 11 -> {
                    if (!ready("Optimizations (Beta)")) return;
                    require(reads == mainReads, "Diagnostics kept polling after leaving the page");
                    click(text("Optimizations (Beta)")); next();
                }
                case 12 -> {
                    requireBeta();
                    if (!ready("GPU Model Transforms")) return;
                    require("Players".equals(ShellState.INSTANCE.getSelectedCategories().get(ID)), "Reopening Beta lost the chosen category");
                    require(reads == mainReads, "Reopening Beta restarted inline diagnostics");
                    requireUnchangedValues(); modeReport.put("reopenRetainsCategory", true);
                    modeReport.put("oldSettingIdsAndValuesRetained", true); modeReports.add(modeReport);
                    if (mode == 0) {
                        mode = 1; startMode();
                        LocalNavController.INSTANCE.getWrapper().navigate(ModsRoute.INSTANCE, true);
                        phase = 0; wait = 0;
                    } else finish(null);
                }
                default -> throw new IllegalStateException("Unknown optimizations UI QA phase");
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private static void initialize(Minecraft mc) throws Exception {
        parent = mc.screen;
        originalMode = OneConfigConfig.guiMode;
        originalLimitFps = OneConfigConfig.limitUnfocusedFps;
        originalVsync = mc.options.vsync;
        originalQuery = ShellState.INSTANCE.getSearchQuery();
        originalCategories.putAll(ShellState.INSTANCE.getSelectedCategories());
        provider = BetaOptimizationPage.INSTANCE.getDiagnostics();
        require(ID.equals(BetaOptimizationPage.INSTANCE.getConfigId()), "The Beta config ID changed");
        var tree = BetaOptimizationsConfig.INSTANCE.getTree();
        for (String id : OLD_IDS) {
            var prop = tree.getProp(id);
            require(prop != null && prop.getMetadata("visualizer") != null, "An existing setting lost its control or ID: " + id);
            originalValues.put(id, prop.get());
        }
        List<String> categories = new ArrayList<>();
        for (var node : tree.map.values()) {
            String category = node.getMetadata("category");
            if (category != null && !categories.contains(category)) categories.add(category);
        }
        require(CATEGORIES.equals(categories), "The native config does not have exactly the three intended categories in order: " + categories);
        BetaOptimizationPage.INSTANCE.register(ID, () -> {
            reads++;
            latestDiagnostics = List.copyOf(provider.invoke());
            return latestDiagnostics;
        });
        OneConfigConfig.limitUnfocusedFps = false;
        mc.options.vsync = false;
        Display.setVSyncEnabled(false);
        ClientPreferences.setSetupComplete(true);
        initialized = true;
        startMode();
        OneConfigUI.open(ModsRoute.INSTANCE);
    }

    private static void startMode() {
        OneConfigConfig.INSTANCE.getTree().getProp("guiMode").setAs(mode);
        require(ShellState.INSTANCE.getSimpleMode() == (mode == 1), "The actual GUI mode preference did not update the shell");
        ShellState.INSTANCE.getSelectedCategories().remove(ID);
        ShellState.INSTANCE.setSearchQuery("");
        ShellState.INSTANCE.getScrollAnchors().remove(ID);
        mainReads = reads;
        modeReport = new LinkedHashMap<>();
        modeReport.put("mode", mode == 0 ? "Modern" : "Simple");
    }

    private static void requireBeta() {
        Object route = LocalNavController.INSTANCE.getWrapper().getCurrentRoute();
        require(route instanceof OptimizationsRoute || route instanceof OptimizationsGraph, "The normal Beta sidebar entry did not open its settings");
        require(Minecraft.getInstance().screen == options, "Beta opened a separate Minecraft screen");
    }

    private static boolean diagnosticsReady() throws Exception {
        if (reads - mainReads >= 3 && displayedDiagnosticLines().size() >= 3) return true;
        require(wait < 180, "The diagnostics page did not display repeated live production status reads"); return false;
    }

    private static void requireCategories() throws Exception {
        double previousX = -1;
        for (String category : CATEGORIES) {
            Object rect = call(text(category), "getBoundsInRoot");
            double x = number(rect, "getLeft");
            require(x > previousX, "The actual category chips were not ordered Hypixel, Players, Display"); previousX = x;
        }
    }

    private static List<String> displayedDiagnosticLines() throws Exception {
        List<String> result = new ArrayList<>();
        for (Object node : nodes()) for (String value : textValues(node)) {
            for (String status : latestDiagnostics) {
                int colon = status.indexOf(':');
                String prefix = colon < 0 ? status : status.substring(0, colon + 1);
                if (value.startsWith(prefix)) { result.add(value); break; }
            }
        }
        return result;
    }

    private static void requireNoDiagnostics() throws Exception {
        require(displayedDiagnosticLines().isEmpty(), "Detailed diagnostics remained inline above settings");
    }

    private static void requireUnchangedValues() {
        for (var entry : originalValues.entrySet()) require(Objects.equals(entry.getValue(), BetaOptimizationsConfig.INSTANCE.getTree().getProp(entry.getKey()).get()),
            "UI navigation changed an existing setting value: " + entry.getKey());
    }

    private static void next() { phase++; wait = 0; }
    private static boolean ready(String value) throws Exception {
        if (textOrNull(value) != null) return true;
        require(wait < 150, "The native GUI did not display: " + value); return false;
    }
    private static void search(String query) throws Exception {
        Object target = null; double top = Double.POSITIVE_INFINITY;
        for (Object node : nodes()) if (properties(node).containsKey("SetText")) {
            double y = number(call(node, "getBoundsInRoot"), "getTop");
            if (y < top) { target = node; top = y; }
        }
        require(target != null, "The header search field has no editable semantics");
        Object annotated = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class)
            .newInstance(query, List.of(), List.of());
        action(target, "SetText", annotated);
    }
    private static Object text(String value) throws Exception { Object node = textOrNull(value); require(node != null, "GUI text missing: " + value); return node; }
    private static Object textOrNull(String value) throws Exception {
        for (Object node : nodes()) if (textValues(node).contains(value)) return node; return null;
    }
    private static Object description(String value) throws Exception {
        Object node = descriptionOrNull(value); require(node != null, "GUI button missing: " + value); return node;
    }
    private static Object descriptionOrNull(String value) throws Exception {
        for (Object node : nodes()) if (properties(node).get("ContentDescription") instanceof Collection<?> labels && labels.contains(value)) return node;
        return null;
    }
    private static List<String> textValues(Object node) throws Exception {
        List<String> values = new ArrayList<>();
        if (properties(node).get("Text") instanceof Collection<?> texts) for (Object text : texts) values.add((String) call(text, "getText"));
        return values;
    }
    private static void action(Object node, String key, Object... args) throws Exception {
        require(Boolean.TRUE.equals(call(call(properties(node).get(key), "getAction"), "invoke", args)), "GUI action rejected: " + key);
    }
    private static List<Object> nodes() throws Exception {
        Object scene = field(Minecraft.getInstance().screen, "sceneOrNull");
        List<Object> owners = new ArrayList<>(); owners.add(field(scene, "mainOwner"));
        try { for (Object layer : (Collection<?>) field(scene, "layers")) owners.add(field(layer, "owner")); }
        catch (NoSuchFieldException ignored) { }
        List<Object> result = new ArrayList<>();
        for (Object owner : owners) descendants(call(call(owner, "getSemanticsOwner"), "getRootSemanticsNode"), result);
        return result;
    }
    private static void descendants(Object node, List<Object> result) throws Exception {
        result.add(node);
        for (Object child : (Collection<?>) call(node, "getChildren")) descendants(child, result);
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
        double ratio = Platform.screen().pixelRatio();
        Mouse.setCursorPosition((int) Math.round(x * ratio), mc.height - (int) Math.round(y * ratio) - 1);
        int guiX = (int) Math.round(x * screen.width / (mc.width / ratio)), guiY = (int) Math.round(y * screen.height / (mc.height / ratio));
        ((MouseInputAccess) screen).release$press(guiX, guiY, 0); ((MouseInputAccess) screen).release$release(guiX, guiY, 0);
    }
    private static double number(Object object, String name) throws Exception { return ((Number) call(object, name)).doubleValue(); }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object object, String name, Object... args) throws Exception {
        for (Method method : object.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            try { method.setAccessible(true); return method.invoke(object, args); }
            catch (IllegalArgumentException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    private static void capture(String state) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.take(mc.gameDir, "optimizations066-" + (mode == 0 ? "modern" : "simple") + "-" + state + ".png", mc.width, mc.height, mc.getRenderTarget());
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void restore() {
        if (provider != null) BetaOptimizationPage.INSTANCE.register(ID, provider);
        if (initialized) {
            OneConfigConfig.INSTANCE.getTree().getProp("guiMode").setAs(originalMode);
            OneConfigConfig.limitUnfocusedFps = originalLimitFps;
            Minecraft.getInstance().options.vsync = originalVsync;
            Display.setVSyncEnabled(originalVsync);
            ShellState.INSTANCE.setSearchQuery(originalQuery);
            ShellState.INSTANCE.getSelectedCategories().clear();
            ShellState.INSTANCE.getSelectedCategories().putAll(originalCategories);
        }
        requireUnchangedValues();
    }
    private static void finish(Throwable failure) {
        done = true;
        try { restore(); report.put("originalValuesPreserved", true); } catch (Throwable error) { if (failure == null) failure = error; }
        report.put("passed", failure == null); report.put("mode", mode); report.put("phase", phase);
        report.put("syntheticOfflineFixture", true); report.put("configId", ID);
        report.put("originalValues", originalValues); report.put("modes", modeReports);
        if (failure != null) {
            report.put("error", failure.toString()); failure.printStackTrace();
            try { capture("failure"); } catch (Throwable ignored) { }
        }
        try {
            Path output = Path.of(System.getProperty("arcadium.optimizationsUi066.output"));
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().openScreen(parent);
        Minecraft.getInstance().stop();
    }
}
