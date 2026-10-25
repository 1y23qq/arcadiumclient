// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import io.github.arcadiumclient.modern.gui.qa.mixin.SoundInstanceQaAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import kotlin.Lazy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.polyfrost.oneconfig.api.notifications.v1.NotificationsManager;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class NativeSoundQa {
    private static final List<Map<String, Object>> SOUNDS = new ArrayList<>();
    private static final List<Map<String, Object>> ALL_SOUNDS = new ArrayList<>();
    private static boolean recording;
    private NativeSoundQa() {}

    static void start() { SOUNDS.clear(); ALL_SOUNDS.clear(); recording = true; }
    static List<Map<String, Object>> finish() { recording = false; return List.copyOf(SOUNDS); }

    static Map<String, Object> diagnostics() {
        var result = new LinkedHashMap<String, Object>();
        result.put("nativePlayCalls", List.copyOf(ALL_SOUNDS));
        result.put("notificationHandlerPresent", NotificationsManager.INSTANCE.getOnNotification() != null);
        result.put("notificationGate", OneConfigConfig.enableNotifications);
        result.put("uiSoundGate", OneConfigConfig.enableUISounds);
        result.put("notificationSoundGate", OneConfigConfig.enableNotificationSounds);
        result.put("volume", OneConfigConfig.uiSoundVolume);
        try {
            Class<?> sounds = Class.forName("org.polyfrost.oneconfig.internal.ui.sound.UiSounds");
            var field = sounds.getDeclaredField("service$delegate"); field.setAccessible(true);
            Object service = ((Lazy<?>) field.get(null)).getValue();
            result.put("service", service == null ? "null" : service.getClass().getName());
            Class<?> type = Class.forName("org.polyfrost.oneconfig.internal.ui.sound.UiSoundService");
            var declarations = new ArrayList<String>();
            var resources = type.getClassLoader().getResources("META-INF/services/" + type.getName());
            while (resources.hasMoreElements() && declarations.size() < 8) declarations.add(resources.nextElement().toExternalForm());
            result.put("providerResources", declarations);
            result.put("providerConstructed", provider(type));
        } catch (Throwable failure) { result.put("serviceError", causes(failure)); }
        try { result.put("currentTheme", currentTheme().toString()); }
        catch (Throwable failure) { result.put("themeError", causes(failure)); }
        try {
            var time = Class.forName("org.polyfrost.oneconfig.internal.ModernNotifications").getDeclaredField("lastSoundAt");
            time.setAccessible(true); result.put("notificationThrottleAgeMs", (System.nanoTime() - time.getLong(null)) / 1_000_000d);
        } catch (Throwable failure) { result.put("throttleError", causes(failure)); }
        return result;
    }

    static Map<String, Object> failureProbes() {
        var result = new LinkedHashMap<String, Object>();
        result.put("publicUiSoundsPlay", probe(() -> {
            Class<?> sounds = Class.forName("org.polyfrost.oneconfig.internal.ui.sound.UiSounds");
            Class<?> event = Class.forName("org.polyfrost.oneconfig.internal.ui.sound.UiSoundEvent");
            sounds.getMethod("play", event).invoke(sounds.getField("INSTANCE").get(null), enumValue(event, "NOTIFICATION"));
        }));
        result.put("privateModernSound", probe(() -> {
            Class<?> sounds = Class.forName("org.polyfrost.oneconfig.internal.ui.sound.UiSounds");
            var delegate = sounds.getDeclaredField("service$delegate"); delegate.setAccessible(true);
            Object service = ((Lazy<?>) delegate.get(null)).getValue();
            if (service == null) throw new IllegalStateException("The actual sound provider is null");
            var play = service.getClass().getDeclaredMethod("playModernSound", String.class, float.class, float.class);
            play.setAccessible(true); play.invoke(service, "ui.click", 1.2f, .3f);
        }));
        result.put("nativeSoundManagerControl", probe(() -> Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, .15f))));
        return result;
    }
    private static Map<String, Object> probe(Probe probe) {
        var result = new LinkedHashMap<String, Object>();
        start();
        try { probe.run(); result.put("returned", true); }
        catch (Throwable failure) { result.put("error", causes(failure)); }
        finally { finish(); result.put("nativePlayCalls", List.copyOf(ALL_SOUNDS)); }
        return result;
    }
    private static Object currentTheme() throws ReflectiveOperationException {
        Class<?> type = Class.forName("org.polyfrost.oneconfig.internal.ui.sound.UiSoundTheme");
        Object companion = type.getField("Companion").get(null);
        return companion.getClass().getMethod("current").invoke(companion);
    }
    @SuppressWarnings({"unchecked", "rawtypes"}) private static Object enumValue(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }
    @FunctionalInterface private interface Probe { void run() throws Exception; }

    @SuppressWarnings({"unchecked", "rawtypes"}) private static String provider(Class<?> type) {
        var providers = ServiceLoader.load((Class) type, type.getClassLoader()).iterator();
        return providers.hasNext() ? providers.next().getClass().getName() : "none";
    }
    private static List<String> causes(Throwable failure) {
        var result = new ArrayList<String>();
        for (int i = 0; failure != null && i < 8; i++, failure = failure.getCause()) result.add(failure.toString());
        return result;
    }

    public static void played(SoundInstance sound) {
        if (!recording || sound == null) return;
        String id = sound.getIdentifier().toString();
        if (!(sound instanceof SoundInstanceQaAccess raw)) {
            if (ALL_SOUNDS.size() < 30) ALL_SOUNDS.add(Map.of("id", id, "rawValuesAvailable", false));
            return;
        }
        float pitch = raw.arcadium$rawPitch(), volume = raw.arcadium$rawVolume();
        if (ALL_SOUNDS.size() < 30) ALL_SOUNDS.add(Map.of("id", id, "pitch", pitch, "volume", volume));
        if (SOUNDS.size() >= 20 || Math.abs(pitch - 1.2f) > .01f) return;
        if (id.equals("oneconfig:ui.click") || id.equals("minecraft:ui.button.click"))
            SOUNDS.add(Map.of("id", id, "pitch", pitch, "volume", volume));
    }
}
