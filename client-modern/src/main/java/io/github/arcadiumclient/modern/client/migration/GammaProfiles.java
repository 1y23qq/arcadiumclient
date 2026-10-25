// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;

public final class GammaProfiles {
    private static final Gson GSON = new Gson();
    private static Object config, gamma;
    private static double lastGamma;
    private static boolean applying;
    private static JsonObject preserved = new JsonObject();
    private static JsonObject external = new JsonObject();
    private static JsonObject nativeBaseline;
    private static String owner;
    private GammaProfiles() { }
    public static boolean ready() { return gamma != null; }

    public static void loadLegacy(boolean legacy) {
        try { GammaTransitionGuard.replace(GammaProfiles::cancelNativeTransition, () -> loadLegacy0(legacy)); }
        catch (Exception error) { org.slf4j.LoggerFactory.getLogger("Arcadium/FullBright").error("Could not restore native gamma settings", error); }
    }

    private static void loadLegacy0(boolean legacy) throws Exception {
            config = Class.forName("io.github.sjouwer.gammautils.GammaUtils").getMethod("getConfig").invoke(null);
            gamma = config.getClass().getField("gamma").get(config);
            if (nativeBaseline == null) nativeBaseline = GSON.toJsonTree(config).getAsJsonObject();
            var file = FabricLoader.getInstance().getConfigDir().resolve("gammautils.json");
            external = Files.isRegularFile(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
            boolean applyLegacy = legacy;
            boolean importExternal = false;
            if (!LegacyFullbrightConfig.gammaNativeState.isBlank()) {
                preserved = JsonParser.parseString(LegacyFullbrightConfig.gammaNativeState).getAsJsonObject();
                applyLegacy = GammaProfileState.legacyChanged(preserved, LegacyFullbrightConfig.enable, LegacyFullbrightConfig.lightLevel);
                importExternal = !external.isEmpty() && preserved.has(GammaProfileState.NATIVE_MARKER) && !external.equals(preserved.get(GammaProfileState.NATIVE_MARKER));
                GammaProfileState.overwrite(config, GSON.fromJson(importExternal ? GammaProfileState.merge(preserved, external) : preserved, config.getClass()));
            } else {
                preserved = GammaProfileState.merge(nativeBaseline, external);
                GammaProfileState.overwrite(config, GSON.fromJson(preserved, config.getClass()));
            }
            gamma = config.getClass().getField("gamma").get(config);
            lastGamma = number("getValue");
            if (!legacy || importExternal) LegacyFullbrightConfig.enable = Double.compare(lastGamma, number("getDefaultValue")) != 0;
            else if (applyLegacy) applyLegacy();
            capture();
            owner = ConfigManager.activeProfile();
    }

    public static void applyLegacy() {
        if (!ready() || applying) return;
        applying = true;
        try { GammaTransitionGuard.replace(GammaProfiles::cancelNativeTransition, () -> {
            double target = GammaProfileState.target(LegacyFullbrightConfig.enable, LegacyFullbrightConfig.lightLevel, number("getDefaultValue"), number("getToggledValue"));
            if ((Boolean) gamma.getClass().getMethod("isLimiterEnabled").invoke(gamma)) {
                double minimum = number("getMinimumStrength"), maximum = number("getMaximumStrength");
                if (maximum > minimum) target = Math.clamp(target, minimum, maximum);
            }
            gamma.getClass().getMethod("setValue", double.class).invoke(gamma, target);
            lastGamma = target;
            Class.forName("io.github.sjouwer.gammautils.statuseffect.StatusEffectManager").getMethod("updateGammaStatusEffect").invoke(null);
            capture();
        }); } catch (Exception error) { org.slf4j.LoggerFactory.getLogger("Arcadium/FullBright").error("Could not apply full brightness", error); }
        finally { applying = false; }
    }

    private static void cancelNativeTransition() throws Exception {
        Class<?> type = Class.forName("io.github.sjouwer.gammautils.GammaManager");
        var timer = type.getDeclaredField("transitionTimer"); timer.setAccessible(true);
        if (timer.get(null) instanceof java.util.Timer active) active.cancel();
        timer.set(null, null);
        var target = type.getDeclaredField("dynamicGammaTarget"); target.setAccessible(true); target.setDouble(null, Double.NaN);
    }

    public static void observeNativeState() {
        if (!ready() || applying) return;
        try {
            double value = number("getValue");
            if (Double.compare(value, lastGamma) != 0) {
                LegacyFullbrightConfig.enable = Double.compare(value, number("getDefaultValue")) != 0;
                lastGamma = value;
            }
        } catch (Exception error) { org.slf4j.LoggerFactory.getLogger("Arcadium/FullBright").warn("Could not observe native gamma", error); }
    }

    public static void saveNativeState() {
        if (!ready() || !ConfigManager.activeProfile().equals(owner)) return;
        try {
            String previous = LegacyFullbrightConfig.gammaNativeState;
            observeNativeState(); capture();
            if (!previous.equals(LegacyFullbrightConfig.gammaNativeState) || !Files.isRegularFile(ConfigManager.active().getFolder().resolve("fullbright.json"))) LegacyFullbrightConfig.INSTANCE.save();
        }
        catch (Exception error) { org.slf4j.LoggerFactory.getLogger("Arcadium/FullBright").error("Could not save native gamma choices", error); }
    }

    private static void capture() {
        preserved = GammaProfileState.merge(preserved, GSON.toJsonTree(config).getAsJsonObject());
        preserved = GammaProfileState.remember(preserved, external, LegacyFullbrightConfig.enable, LegacyFullbrightConfig.lightLevel);
        LegacyFullbrightConfig.gammaNativeState = GSON.toJson(preserved);
    }

    private static double number(String method) throws ReflectiveOperationException { return ((Number) gamma.getClass().getMethod(method).invoke(gamma)).doubleValue(); }

    public static void openNativeOptions() {
        try {
            var client = Minecraft.getInstance();
            var modmenu = Class.forName("com.terraformersmc.modmenu.ModMenu");
            var screen = modmenu.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screens.Screen.class).invoke(null, "gammautils", client.screen);
            client.setScreen((net.minecraft.client.gui.screens.Screen) screen);
        } catch (Exception error) { org.slf4j.LoggerFactory.getLogger("Arcadium/FullBright").error("Could not open native gamma settings", error); }
    }
}
