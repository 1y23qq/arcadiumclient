// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.migration.GammaProfiles;
import io.github.arcadiumclient.modern.client.migration.LegacyBlurConfig;
import io.github.arcadiumclient.modern.client.migration.LegacyFullbrightConfig;
import io.github.arcadiumclient.modern.client.migration.LegacySmoothScrollingConfig;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;

final class ModernLegacyNativeProfilesQa {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BLUR = "eu.midnightdust.blur.config.BlurConfig";
    private static final String GAMMA = "io.github.sjouwer.gammautils.GammaManager";
    private final Path output;
    private final String previous = ConfigManager.activeProfile();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String first = "Arcadium Legacy Native A " + suffix, second = "Arcadium Legacy Native B " + suffix;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<Path, byte[]> rootFiles = new LinkedHashMap<>();
    private final Object gammaConfig, gammaSettings;
    private Path firstFolder, secondFolder;
    private double savedGammaA;

    static CompletableFuture<Map<String, Object>> run(Path output) {
        try { return new ModernLegacyNativeProfilesQa(output).run(); }
        catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }
    private ModernLegacyNativeProfilesQa(Path output) throws Exception {
        this.output = output;
        gammaConfig = Class.forName("io.github.sjouwer.gammautils.GammaUtils").getMethod("getConfig").invoke(null);
        gammaSettings = gammaConfig.getClass().getField("gamma").get(gammaConfig);
    }
    private CompletableFuture<Map<String, Object>> run() {
        var checks = step(() -> {
            Path isolation = output.getParent().getParent().toAbsolutePath().normalize();
            require(isolation.getFileName().toString().equals("ArcadiumModern050Qa") && FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize().startsWith(isolation), "Legacy native profile QA requires the isolated test profile");
            ConfigManager.openProfile("");
            ConfigManager.createProfile(first); firstFolder = ConfigManager.active().getFolder(); ConfigManager.openProfile("");
            ConfigManager.createProfile(second); secondFolder = ConfigManager.active().getFolder(); ConfigManager.openProfile("");
            GammaProfiles.saveNativeState(); LegacyFullbrightConfig.INSTANCE.save(); LegacyBlurConfig.INSTANCE.save(); LegacySmoothScrollingConfig.INSTANCE.save();
            for (String name : new String[]{"blur.json", "fullbright.json", "smoothscrollingrefurbished.json"}) {
                Path path = ConfigManager.active().getFolder().resolve(name); rootFiles.put(path, Files.readAllBytes(path));
            }
            Path external = FabricLoader.getInstance().getConfigDir().resolve("gammautils.json");
            if (Files.isRegularFile(external)) rootFiles.put(external, Files.readAllBytes(external));
            seed(firstFolder, true); seed(secondFolder, false);
            ConfigManager.openProfile(first);
        }).thenComposeAsync(ignored -> step(() -> {
            verify(true, 9.5, "A");
            var scrolling = new ScrollProbe(); scrolling.setScrollAmount(-12);
            near(0, scrolling.scrollAmount(), "Push-back off left native scroll below zero");
            scrolling.setScrollAmount(scrolling.maxScrollAmount() + 12);
            near(scrolling.maxScrollAmount(), scrolling.scrollAmount(), "Push-back off left native scroll above its limit");
            LegacySmoothScrollingConfig.INSTANCE.getTree().getProp("pushBack").setAs(true);
            near(1.5, scrolling("pushBackStrength"), "Re-enabling push-back discarded its stored strength");
            LegacySmoothScrollingConfig.INSTANCE.getTree().getProp("pushBack").setAs(false);
            nativeField(BLUR, "gradientStart").set(null, "#44AA66"); nativeField(BLUR, "gradientStartAlpha").setInt(null, 137);
            io.github.arcadiumclient.modern.client.migration.ModernLegacyNativeProfiles.captureBlurSave();
            require(LegacyBlurConfig.gradientStartColor.getArgb() == 0x8944AA66, "Native Blur save did not update the canonical Core color");
            startTransition();
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            require(gamma() < 9.5 && gamma() > 1, "The actual native Gamma timer did not begin its smooth transition");
            require(nativeField(GAMMA, "transitionTimer").get(null) != null, "The native transition timer was absent before the cancellation probe");
            report.put("nativeTransitionStarted", gamma());
            LegacyFullbrightConfig.INSTANCE.getTree().getProp("enable").setAs(false);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            near(.75, gamma(), "An old native timer overwrote the Core FullBright off value");
            require(nativeField(GAMMA, "transitionTimer").get(null) == null, "Core FullBright did not clear the native transient timer");
            near(9.5, gammaNumber("getToggledValue"), "Canceling a transition rewrote the native configured toggle choice");
            require((Boolean) gammaSettings.getClass().getMethod("isSmoothTransitionEnabled").invoke(gammaSettings), "Canceling a transition disabled the saved smooth-transition preference");
            report.put("legacyToggleCanceledTransition", true);
            LegacyFullbrightConfig.INSTANCE.getTree().getProp("enable").setAs(true);
            near(9.5, gamma(), "Core FullBright did not restore its full native gamma target");
            startTransition();
            ConfigManager.openProfile(second);
            savedGammaA = gammaState(firstFolder).getAsJsonObject("gamma").get("value").getAsDouble();
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            verify(false, 1.25, "B");
            require(nativeField(GAMMA, "transitionTimer").get(null) == null, "A previous profile retained its transient Gamma timer");
            near(1.25, gamma(), "A previous profile's timer overwrote profileB gamma");
            report.put("profileSwitchCanceledTransition", true);
            gammaSettings.getClass().getMethod("setValue", double.class).invoke(gammaSettings, 3.5);
            Class.forName("io.github.sjouwer.gammautils.GammaUtils").getMethod("saveConfig").invoke(null);
            var saved = read(secondFolder.resolve("fullbright.json"));
            require(saved.get("enable").getAsBoolean(), "Native Gamma save did not mirror its enabled state into legacy schema");
            near(3.5, gammaState(secondFolder).getAsJsonObject("gamma").get("value").getAsDouble(), "Native Gamma save targeted the wrong named profile");
            ConfigManager.openProfile(first);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            verify(true, savedGammaA, "savedA");
            require(LegacyBlurConfig.gradientStartColor.getArgb() == 0x8944AA66, "Returning to profileA lost a native Blur color edit");
            ConfigManager.openProfile(second);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            verify(false, 3.5, "savedB");
            saved(firstFolder, "A"); saved(secondFolder, "B");
            unchangedRoot();
            report.put("nativeConfigIdentityPreserved", true); report.put("rootJsonUnchanged", true); report.put("unknownFieldsPreserved", true);
        }), QaTickScheduler.EXECUTOR);
        return checks.handleAsync((ignored, failure) -> {
            ConfigManager.openProfile(previous);
            return QaTickScheduler.afterTicks(3).thenApplyAsync(restored -> {
                try {
                    report.put("restored", ConfigManager.activeProfile().equals(previous)); report.put("passed", failure == null);
                    if (failure != null) report.put("error", failure.toString());
                    Files.writeString(output.resolve("legacy-native-profiles.json"), GSON.toJson(report));
                    if (failure != null) throw new CompletionException(failure);
                    return report;
                } catch (Exception error) { throw new CompletionException(error); }
            }, QaTickScheduler.EXECUTOR);
        }, QaTickScheduler.EXECUTOR).thenCompose(value -> value);
    }
    private void verify(boolean first, double gamma, String key) throws Exception {
        require(this.gammaConfig == Class.forName("io.github.sjouwer.gammautils.GammaUtils").getMethod("getConfig").invoke(null), "Profile switching replaced the native config instance");
        require(this.gammaSettings == gammaConfig.getClass().getField("gamma").get(gammaConfig), "Profile switching replaced GammaManager's cached settings object");
        int radius = Minecraft.getInstance().options.menuBackgroundBlurriness().get();
        near(first ? 20 : 0, radius, "Native Blur radius did not match the active profile");
        near(first ? 81 : 6, LegacyBlurConfig.radius, "The native Blur cap changed the stored legacy radius");
        require(nativeField(BLUR, "useGradient").getBoolean(null) == first, "Native Blur enabled/gradient state is stale");
        near(first ? .4 : .9, scrolling("scrollSpeed"), "Native scrolling speed is stale");
        near(first ? .03 : .07, scrolling("scrollbarDrag"), "Native scrolling friction is stale");
        near(first ? 0 : .6, scrolling("pushBackStrength"), "Native push-back strength is stale");
        near(first ? 1.8 : .7, scrolling("animationDuration"), "Native scroll animation duration is stale");
        near(gamma, gamma(), "The active native Gamma value did not match its profile");
        near(first ? 9.5 : 12.5, gammaNumber("getToggledValue"), "Native advanced gamma choice was not restored");
        report.put(key, Map.of("blurRadius", radius, "storedLegacyRadius", LegacyBlurConfig.radius,
            "scrollSpeed", scrolling("scrollSpeed"), "scrollbarDrag", scrolling("scrollbarDrag"), "pushBackStrength", scrolling("pushBackStrength"),
            "animationDuration", scrolling("animationDuration"), "gamma", gamma(), "configuredGammaToggle", gammaNumber("getToggledValue")));
    }
    private void seed(Path folder, boolean first) throws Exception {
        var blur = new JsonObject(); blur.addProperty("enabled", first); blur.addProperty("radius", first ? 81 : 6); blur.addProperty("useGradient", true);
        blur.add("gradientStartColor", color(first ? new int[]{58, 160, 255, 127} : new int[]{241, 54, 176, 192}));
        blur.add("gradientEndColor", color(first ? new int[]{54, 241, 176, 191} : new int[]{255, 160, 58, 63}));
        blur.add("blurAnimationCurve", easing(first ? "SINE" : "EXPO")); blur.add("backgroundAnimationCurve", easing(first ? "QUAD" : "CIRC"));
        var scrolling = new JsonObject(); scrolling.addProperty("scrollStrength", first ? .4 : .9); scrolling.addProperty("scrollbarFriction", first ? .03 : .07);
        scrolling.addProperty("pushBack", !first); scrolling.addProperty("pushBackStrength", first ? 1.5 : .6); scrolling.addProperty("animationDuration", first ? 1.8 : .7);
        JsonObject state = new Gson().toJsonTree(gammaConfig).getAsJsonObject();
        var gamma = state.getAsJsonObject("gamma"); gamma.addProperty("value", 3.25); gamma.addProperty("defaultGamma", first ? 75 : 125);
        gamma.addProperty("toggledGamma", first ? 950 : 1250); gamma.getAsJsonObject("dynamic").addProperty("enabled", false);
        gamma.getAsJsonObject("transition").addProperty("smoothTransition", true); gamma.getAsJsonObject("transition").addProperty("transitionSpeed", 100);
        var marker = new JsonObject(); marker.addProperty("enable", !first); marker.addProperty("lightLevel", 15); state.add("_arcadiumLegacy", marker);
        var fullbright = new JsonObject(); fullbright.addProperty("enable", first); fullbright.addProperty("lightLevel", first ? 15 : 8); fullbright.addProperty("gammaNativeState", GSON.toJson(state));
        for (var file : Map.of("blur.json", blur, "smoothscrollingrefurbished.json", scrolling, "fullbright.json", fullbright).entrySet()) {
            var unknown = new JsonObject(); unknown.addProperty("profile", first ? "A" : "B"); unknown.addProperty("retain", 37); file.getValue().add("arcadiumQaUnknown", unknown);
            Files.writeString(folder.resolve(file.getKey()), GSON.toJson(file.getValue()));
        }
    }
    private void startTransition() throws Exception { Class.forName(GAMMA).getMethod("setGamma", double.class, boolean.class, boolean.class).invoke(null, 1.0, true, false); }
    private double gamma() throws Exception { return ((Number) Class.forName(GAMMA).getMethod("getGamma").invoke(null)).doubleValue(); }
    private double gammaNumber(String method) throws Exception { return ((Number) gammaSettings.getClass().getMethod(method).invoke(gammaSettings)).doubleValue(); }
    private static double scrolling(String field) throws Exception { return nativeField("me.wolfii.Config", field).getDouble(null); }
    private static Field nativeField(String type, String name) throws Exception { var field = Class.forName(type).getDeclaredField(name); field.setAccessible(true); return field; }
    private static JsonObject gammaState(Path folder) throws Exception { return JsonParser.parseString(read(folder.resolve("fullbright.json")).get("gammaNativeState").getAsString()).getAsJsonObject(); }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static JsonObject color(int[] channels) { var color = new JsonObject(); color.addProperty("class", "org.polyfrost.compose.render.PolyColor"); var value = new JsonArray(); for (int channel : channels) value.add(channel); color.add("value", value); return color; }
    private static JsonObject easing(String name) { var value = new JsonObject(); value.addProperty("class", "eu.midnightdust.blur.BlurConfig$Easing"); value.addProperty("value", name); return value; }
    private void unchangedRoot() throws Exception { for (var file : rootFiles.entrySet()) require(Arrays.equals(file.getValue(), Files.readAllBytes(file.getKey())), "A named legacy/native save changed root JSON " + file.getKey().getFileName()); }
    private void saved(Path folder, String key) throws Exception {
        for (String name : new String[]{"blur.json", "smoothscrollingrefurbished.json", "fullbright.json"}) {
            var saved = read(folder.resolve(name)); require(saved.getAsJsonObject("arcadiumQaUnknown").get("profile").getAsString().equals(key), "Native profile save discarded unknown legacy data");
            if (name.equals("blur.json")) require(saved.getAsJsonObject("blurAnimationCurve").get("class").getAsString().equals("eu.midnightdust.blur.BlurConfig$Easing"), "Blur save changed the cross-version easing class identity");
            Path destination = output.resolve("legacy-native-fixtures").resolve(key).resolve(name); Files.createDirectories(destination.getParent()); Files.writeString(destination, GSON.toJson(saved));
        }
    }
    private static void near(double expected, double actual, String message) { require(Math.abs(expected - actual) < .0001, message + ": expected=" + expected + ", actual=" + actual); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static CompletableFuture<Void> step(Checked action) { try { action.run(); return QaTickScheduler.afterTicks(5); } catch (Throwable error) { return CompletableFuture.failedFuture(error); } }
    private static final class ScrollProbe extends AbstractScrollArea {
        private ScrollProbe() { super(0, 0, 100, 100, Component.empty()); }
        @Override protected int contentHeight() { return 1000; }
        @Override protected double scrollRate() { return 9; }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) { }
        @Override protected void updateWidgetNarration(NarrationElementOutput narration) { }
    }
}
