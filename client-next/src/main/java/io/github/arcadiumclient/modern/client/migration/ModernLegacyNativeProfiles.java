// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.slf4j.LoggerFactory;

public final class ModernLegacyNativeProfiles {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("Arcadium/Legacy native settings");
    private static final String BLUR = "eu.midnightdust.blur.config.BlurConfig";
    private static final Map<String, Object> APPLIED = new HashMap<>();
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Map<String, Object> BLUR_BASELINE = new HashMap<>();
    private static boolean initialized, applying, hasBlur, hasScrolling, blurReady, failed;
    private static int appliedRadius = -1;
    private ModernLegacyNativeProfiles() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        hasBlur = FabricLoader.getInstance().isModLoaded("blur");
        hasScrolling = FabricLoader.getInstance().isModLoaded("smoothscrollingrefurbished");
        org.polyfrost.oneconfig.internal.compat.CompatLoader.INSTANCE.getNativeLoadedConfigs().add("gammautils");
        if (hasScrolling) LegacySmoothScrollingConfig.INSTANCE.preload();
        applyScrolling();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            initializeBlur();
            if (FabricLoader.getInstance().isModLoaded("gammautils")) LegacyFullbrightConfig.INSTANCE.preload();
        });
        ConfigManager.addProfileChangeListener(new ConfigManager.ProfileChangeListener() {
            public void onProfileSaving(String profile) { if (GammaProfiles.ready() && profile.equals(ConfigManager.activeProfile())) GammaProfiles.saveNativeState(); }
            public void onProfileChanged(String profile) { }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (hasBlur && (client.gui.screen() == null || !client.gui.screen().getClass().getName().equals("eu.midnightdust.lib.config.MidnightConfigScreen"))) applyBlur();
            if (GammaProfiles.ready()) GammaProfiles.observeNativeState();
        });
    }

    private static void initializeBlur() {
        if (!hasBlur || blurReady) return;
        try {
            for (String name : commonBlurFields()) BLUR_BASELINE.put(name, nativeField(name).get(null));
            for (String name : List.of("useGradient", "rainbowMode", "gradientStart", "gradientStartAlpha", "gradientEnd", "gradientEndAlpha")) BLUR_BASELINE.put(name, nativeField(name).get(null));
            BLUR_BASELINE.put("radius", Minecraft.getInstance().options.menuBackgroundBlurriness().get());
            blurReady = true;
            LegacyBlurConfig.INSTANCE.preload();
            applyBlur();
        } catch (Exception error) { report(error); }
    }

    public static boolean initialized() { return blurReady; }

    public static boolean skipNativeBlurLoad() {
        if (blurReady) { applyBlur(); return true; }
        var path = FabricLoader.getInstance().getConfigDir().resolve("blur.json");
        if (!Files.isRegularFile(path)) return false;
        try (var reader = Files.newBufferedReader(path)) {
            return LegacyNativePolicy.coreBlurSchema(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception error) { return false; }
    }

    public static void importNativeBlurSchema(boolean legacyDocument) {
        if (!hasBlur) return;
        var path = ConfigManager.active().getFolder().resolve("blur.json");
        if (!legacyDocument) {
            try {
                for (String name : commonBlurFields()) setLegacy(name, BLUR_BASELINE.get(name));
                LegacyBlurConfig.useGradient = (Boolean) BLUR_BASELINE.get("useGradient");
                LegacyBlurConfig.rainbowMode = (Boolean) BLUR_BASELINE.get("rainbowMode");
                LegacyBlurConfig.gradientStartColor = baselineColor("gradientStart");
                LegacyBlurConfig.gradientEndColor = baselineColor("gradientEnd");
                LegacyBlurConfig.radius = (Integer) BLUR_BASELINE.get("radius");
            } catch (Exception error) { report(error); }
            return;
        }
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("gradientStartColor") && root.has("gradientStart")) {
                LegacyBlurConfig.gradientStartColor = new PolyColor(LegacyNativePolicy.argb(root.get("gradientStart").getAsString(), root.has("gradientStartAlpha") ? root.get("gradientStartAlpha").getAsInt() : 75));
                LegacyBlurConfig.gradientEndColor = new PolyColor(LegacyNativePolicy.argb(root.get("gradientEnd").getAsString(), root.has("gradientEndAlpha") ? root.get("gradientEndAlpha").getAsInt() : 75));
                if (!root.has("radius")) LegacyBlurConfig.radius = (Integer) BLUR_BASELINE.get("radius");
            }
        } catch (Exception error) { LOG.warn("Could not import the native GUI Blur color schema", error); }
    }

    public static void applyBlur() {
        if (!blurReady || applying) return;
        applying = true;
        try {
            for (String name : commonBlurFields()) {
                Object value = LegacyBlurConfig.class.getField(name).get(null);
                if (value instanceof String[] strings) value = new ArrayList<>(List.of(strings));
                if (value instanceof Enum<?> entry) value = entry.name();
                setNative(name, value);
            }
            setNative("useGradient", LegacyBlurConfig.enabled && LegacyBlurConfig.useGradient);
            setNative("rainbowMode", LegacyBlurConfig.rainbowMode);
            setColor("gradientStart", LegacyBlurConfig.gradientStartColor);
            setColor("gradientEnd", LegacyBlurConfig.gradientEndColor);
            int radius = LegacyNativePolicy.blurRadius(LegacyBlurConfig.enabled, LegacyBlurConfig.radius);
            var option = Minecraft.getInstance().options.menuBackgroundBlurriness();
            if (option.get() != radius) option.set(radius);
            appliedRadius = radius;
            refreshMidnightEntries();
            failed = false;
        } catch (Exception error) { report(error); }
        finally { applying = false; }
    }

    public static void applyScrolling() {
        if (!hasScrolling) return;
        try {
            var type = Class.forName("me.wolfii.smoothscrollingrefurbished.config.Config");
            var owner = type.getField("INSTANCE").get(null);
            type.getField("scrollStrength").setDouble(owner, LegacySmoothScrollingConfig.scrollStrength);
            type.getField("scrollbarFriction").setDouble(owner, LegacySmoothScrollingConfig.scrollbarFriction);
            type.getField("pushBackStrength").setDouble(owner, LegacyNativePolicy.scrollingStrength(LegacySmoothScrollingConfig.pushBack, LegacySmoothScrollingConfig.pushBackStrength));
        } catch (Exception error) { report(error); }
    }

    public static boolean captureScrollingSave() {
        if (!hasScrolling) return false;
        try {
            var type = Class.forName("me.wolfii.smoothscrollingrefurbished.config.Config");
            var owner = type.getField("INSTANCE").get(null);
            LegacySmoothScrollingConfig.scrollStrength = (float) type.getField("scrollStrength").getDouble(owner);
            LegacySmoothScrollingConfig.scrollbarFriction = (float) type.getField("scrollbarFriction").getDouble(owner);
            double strength = type.getField("pushBackStrength").getDouble(owner);
            LegacySmoothScrollingConfig.pushBack = strength > 0;
            if (strength > 0) LegacySmoothScrollingConfig.pushBackStrength = (float) strength;
            LegacySmoothScrollingConfig.INSTANCE.save();
            return true;
        } catch (Exception error) { report(error); return true; }
    }

    public static void captureBlurSave() {
        if (!blurReady || applying) return;
        try {
            boolean changed = false;
            for (String name : commonBlurFields()) {
                Object value = nativeField(name).get(null);
                Object compared = value instanceof Enum<?> entry ? entry.name() : value;
                if (Objects.equals(compared, APPLIED.get(name))) continue;
                setLegacy(name, value);
                changed = true;
            }
            Object gradient = nativeField("useGradient").get(null);
            if (!Objects.equals(gradient, APPLIED.get("useGradient"))) { LegacyBlurConfig.useGradient = (Boolean) gradient; changed = true; }
            boolean rainbow = nativeField("rainbowMode").getBoolean(null);
            if (rainbow != LegacyBlurConfig.rainbowMode) { LegacyBlurConfig.rainbowMode = rainbow; changed = true; }
            PolyColor start = captureColor("gradientStart", LegacyBlurConfig.gradientStartColor), end = captureColor("gradientEnd", LegacyBlurConfig.gradientEndColor);
            changed |= start != LegacyBlurConfig.gradientStartColor || end != LegacyBlurConfig.gradientEndColor;
            LegacyBlurConfig.gradientStartColor = start;
            LegacyBlurConfig.gradientEndColor = end;
            int radius = Minecraft.getInstance().options.menuBackgroundBlurriness().get();
            if (radius != appliedRadius) { LegacyBlurConfig.radius = radius; changed = true; }
            if (!changed) return;
            LegacyBlurConfig.INSTANCE.save();
            applyBlur();
        } catch (Exception error) { report(error); }
    }

    private static PolyColor captureColor(String name, PolyColor previous) throws Exception {
        String rgb = (String) nativeField(name).get(null);
        int alpha = nativeField(name + "Alpha").getInt(null);
        if (Objects.equals(rgb, APPLIED.get(name)) && Objects.equals(alpha, APPLIED.get(name + "Alpha"))) return previous;
        return new PolyColor(LegacyNativePolicy.argb(rgb, alpha), previous.getChroma(), previous.getChromaSpeed());
    }

    private static PolyColor baselineColor(String name) { return new PolyColor(LegacyNativePolicy.argb((String) BLUR_BASELINE.get(name), (Integer) BLUR_BASELINE.get(name + "Alpha"))); }

    private static void setLegacy(String name, Object value) throws Exception {
        var field = LegacyBlurConfig.class.getField(name);
        if (field.getType() == String[].class) value = ((List<?>) value).toArray(String[]::new);
        else if (field.getType().isEnum()) value = enumValue(field.getType(), ((Enum<?>) value).name());
        field.set(null, value);
    }

    private static List<String> commonBlurFields() {
        return List.of("blurContainers", "blurBooks", "blurSigns", "blurCommandBlocks", "blurDeathScreen", "blurTitleScreen", "darkenTitleScreen", "reduceInGameBlur", "showScreenID", "forceEnabledScreens", "forceDisabledScreens", "gradientRotation", "fadeTimeMillis", "fadeOutTimeMillis", "blurAnimationCurve", "backgroundAnimationCurve");
    }

    private static Field nativeField(String name) throws Exception {
        Field field = FIELDS.get(name);
        if (field == null) { field = Class.forName(BLUR).getField(name); FIELDS.put(name, field); }
        return field;
    }

    private static void setNative(String name, Object value) throws Exception {
        if (Objects.equals(APPLIED.get(name), value)) return;
        Field field = nativeField(name);
        field.set(null, field.getType().isEnum() ? enumValue(field.getType(), value.toString()) : value);
        APPLIED.put(name, value instanceof List<?> list ? List.copyOf(list) : value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }

    private static void setColor(String name, PolyColor color) throws Exception {
        int argb = color.getArgb();
        setNative(name, LegacyNativePolicy.rgb(argb));
        setNative(name + "Alpha", argb >>> 24);
    }

    public static void refreshMidnightEntries() {
        try {
            var type = Class.forName("eu.midnightdust.lib.config.MidnightConfig");
            var entriesField = type.getDeclaredField("entries"); entriesField.setAccessible(true);
            for (Object entry : ((Map<?, ?>) entriesField.get(null)).values()) {
                var entryType = entry.getClass();
                if (!"blur".equals(entryType.getField("modid").get(entry))) continue;
                if (entryType.getField("entry").get(entry) == null) continue;
                Field field = (Field) entryType.getField("field").get(entry);
                if (!Modifier.isStatic(field.getModifiers())) continue;
                var value = entryType.getDeclaredField("value"); value.setAccessible(true);
                Object current = field.get(null);
                value.set(entry, current);
                var temporary = entryType.getDeclaredField("tempValue"); temporary.setAccessible(true);
                if (current instanceof List<?> list && list.isEmpty()) temporary.set(entry, "");
                else {
                    if (current instanceof List<?> list) {
                        var index = entryType.getDeclaredField("listIndex"); index.setAccessible(true);
                        if (index.getInt(entry) < 0 || index.getInt(entry) >= list.size()) index.setInt(entry, 0);
                    }
                    temporary.set(entry, entryType.getMethod("toTemporaryValue").invoke(entry));
                }
            }
        } catch (Exception error) { report(error); }
    }

    private static void report(Exception error) {
        if (!failed) LOG.error("Could not synchronize legacy native settings", error);
        failed = true;
    }
}
