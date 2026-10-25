// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.loader.api.FabricLoader;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Tree;

final class ModernAnimationProfilesQa {
    private static final String OVERFLOW = "overflowanimations.json";
    private final Path output;
    private final String previous = ConfigManager.activeProfile();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String first = "Arcadium Animation A " + suffix, second = "Arcadium Animation B " + suffix;
    private final int previousMode = AnimationsConfig.getMode();
    private final boolean previousEnabled = AnimationsConfig.getEnabled();
    private final float previousLive;
    private final Map<Path, byte[]> rootFiles = new LinkedHashMap<>();
    private final Map<String, Object> categories = new LinkedHashMap<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private Path firstFolder, secondFolder;
    private ModernAnimationProfilesQa(Path output) throws Exception {
        this.output = output; previousLive = liveScale();
        Object config = nativeConfig();
        for (String key : new String[]{"items", "movement", "screen", "fixes", "other"}) categories.put(key, config.getClass().getField(key).get(config));
    }
    static CompletableFuture<Map<String, Object>> run(Path output) {
        try { return new ModernAnimationProfilesQa(output).run(); }
        catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }
    private CompletableFuture<Map<String, Object>> run() {
        var checks = step(() -> {
            Path isolation = output.getParent().getParent().toAbsolutePath().normalize();
            require(isolation.getFileName().toString().equals("ArcadiumModern050Qa") && FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize().startsWith(isolation), "Animation profile QA requires the isolated test profile");
            ConfigManager.openProfile("");
            AnimationsConfig.INSTANCE.save(); require(ConfigManager.active().save(OVERFLOW), "Could not normalize the native animation root");
            for (String id : new String[]{OVERFLOW, "arcadium-animations.json"}) {
                Path file = ConfigManager.active().getFolder().resolve(id); rootFiles.put(file, Files.readAllBytes(file));
            }
            ConfigManager.createProfile(first); firstFolder = ConfigManager.active().getFolder();
            ConfigManager.openProfile("");
            ConfigManager.createProfile(second); secondFolder = ConfigManager.active().getFolder();
            ConfigManager.openProfile(first);
        }).thenComposeAsync(ignored -> step(() -> {
            configure(1.73f, .42f);
            saveAndVerify(firstFolder, 1.73f, .42f, "A");
            ConfigManager.openProfile(second);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            configure(2.14f, .53f);
            saveAndVerify(secondFolder, 2.14f, .53f, "B");
            ConfigManager.openProfile(first);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            require(AnimationsConfig.getMode() == 1 && Math.abs(liveScale() - .42f) < .0001f, "ProfileA did not restore the active Badlion runtime transform");
            near(1.73f, savedScale(firstFolder), "Profile saving persisted a temporary Badlion override inA");
            own().getProp("mode").setAs(0);
            near(1.73f, liveScale(), "Switching to Overflow lost profileA's original user transform");
            report.put("restoredA", Map.of("overflowScale", liveScale(), "storedScale", savedScale(firstFolder)));
            ConfigManager.openProfile(second);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            require(AnimationsConfig.getMode() == 1 && Math.abs(liveScale() - .53f) < .0001f, "ProfileB did not restore the active Badlion runtime transform");
            near(2.14f, savedScale(secondFolder), "Profile saving persisted a temporary Badlion override inB");
            own().getProp("mode").setAs(0);
            near(2.14f, liveScale(), "Switching to Overflow lost profileB's original user transform");
            report.put("restoredB", Map.of("overflowScale", liveScale(), "storedScale", savedScale(secondFolder)));
            ConfigManager.openProfile("");
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            unchangedRoot(); verifyCategoryIdentity(); report.put("rootJsonUnchanged", true); report.put("sameFinalNativeCategoryObjects", true);
        }), QaTickScheduler.EXECUTOR);
        return checks.handleAsync((ignored, failure) -> {
            ConfigManager.openProfile(previous);
            return QaTickScheduler.afterTicks(3).thenApplyAsync(restored -> {
                try {
                    require(AnimationsConfig.getMode() == previousMode && AnimationsConfig.getEnabled() == previousEnabled && Math.abs(liveScale() - previousLive) < .0001f, "Animation profile QA did not restore the previous live mode");
                    report.put("restored", true); report.put("passed", failure == null);
                    if (failure != null) report.put("error", failure.toString());
                    Files.writeString(output.resolve("animation-profiles.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
                    if (failure != null) throw new CompletionException(failure);
                    return report;
                } catch (Exception writing) { throw new CompletionException(writing); }
            }, QaTickScheduler.EXECUTOR);
        }, QaTickScheduler.EXECUTOR).thenCompose(value -> value);
    }
    private void configure(float overflow, float badlion) throws Exception {
        own().getProp("enabled").setAs(true); own().getProp("mode").setAs(0);
        nativeTree().getProp("itemScaleX").setAs(overflow);
        near(overflow, liveScale(), "Native Overflow setting did not update the actual category");
        own().getProp("itemScaleX").setAs(badlion); own().getProp("mode").setAs(1);
        near(badlion, liveScale(), "Badlion mode did not apply to the actual native category");
    }
    private void saveAndVerify(Path folder, float overflow, float badlion, String label) throws Exception {
        require(ConfigManager.active().save(OVERFLOW), "Native animation save failed");
        near(overflow, savedScale(folder), "Native save wrote temporary Badlion transforms in " + label);
        near(badlion, liveScale(), "Native save failed to restore active Badlion transforms in " + label);
        verifyCategoryIdentity();
        report.put("saved" + label, Map.of("storedOverflowScale", savedScale(folder), "liveBadlionScale", liveScale()));
        unchangedRoot();
    }
    private static Tree own() { return AnimationsConfig.INSTANCE.getTree(); }
    private static Tree nativeTree() { return ConfigManager.active().get(OVERFLOW); }
    private static float liveScale() throws Exception {
        Object config = nativeConfig(), items = config.getClass().getField("items").get(config);
        return items.getClass().getField("itemScaleX").getFloat(items);
    }
    private static Object nativeConfig() throws Exception { return Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig").getMethod("instance").invoke(null); }
    private void verifyCategoryIdentity() throws Exception {
        Object config = nativeConfig();
        for (var entry : categories.entrySet()) require(config.getClass().getField(entry.getKey()).get(config) == entry.getValue(), "Saving replaced a final native animation category " + entry.getKey());
    }
    private static float savedScale(Path folder) throws Exception { return JsonParser.parseString(Files.readString(folder.resolve(OVERFLOW))).getAsJsonObject().get("itemScaleX").getAsFloat(); }
    private void unchangedRoot() throws Exception { for (var file : rootFiles.entrySet()) require(Arrays.equals(file.getValue(), Files.readAllBytes(file.getKey())), "A named animation save changed root " + file.getKey().getFileName()); }
    private static void near(float expected, float actual, String message) { require(Math.abs(expected - actual) < .0001f, message + ": " + expected + " vs " + actual); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static CompletableFuture<Void> step(Checked action) { try { action.run(); return QaTickScheduler.afterTicks(3); } catch (Throwable failure) { return CompletableFuture.failedFuture(failure); } }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
