// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SkinLayersBridge {
    private static final String[] PARTS = {"enableHat", "enableJacket", "enableLeftSleeve", "enableRightSleeve", "enableLeftPants", "enableRightPants"};

    private SkinLayersBridge() {}

    public static boolean available() {
        try { config(); return true; }
        catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }

    public static Boolean current() {
        try {
            Object config = config();
            for (String part : PARTS) if (Boolean.TRUE.equals(read(config, part))) return true;
            return false;
        } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }

    public static void apply(boolean head, boolean body, boolean leftArm, boolean rightArm,
                             boolean leftLeg, boolean rightLeg, int distance) throws ReflectiveOperationException {
        Object config = config();
        boolean[] values = {head, body, leftArm, rightArm, leftLeg, rightLeg};
        for (int i = 0; i < PARTS.length; i++) write(config, PARTS[i], values[i]);
        for (String name : new String[]{"enableSkulls", "enableSkullsItems"}) {
            try { write(config, name, head); }
            catch (NoSuchFieldException | NoSuchMethodException ignored) { }
        }
        write(config, "renderDistanceLOD", Math.max(4, Math.min(64, distance)));
        save(config);
    }

    public static void discardTexture(Object texture) {
        try {
            Class<?> cache = Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache");
            for (String name : new String[]{"players", "skulls"}) {
                Field field = cache.getDeclaredField(name);
                field.setAccessible(true);
                java.util.Map<?, ?> meshes = (java.util.Map<?, ?>) field.get(null);
                var entries = meshes.entrySet().iterator();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    Object key = entry.getKey();
                    Object skin = key;
                    if (name.equals("players")) {
                        Method getter = key.getClass().getMethod("getSkin");
                        getter.setAccessible(true);
                        skin = getter.invoke(key);
                    }
                    if (texture.equals(skin)) {
                        entry.getValue().getClass().getMethod("dispose").invoke(entry.getValue());
                        entries.remove();
                    }
                }
            }
            Field unusable = cache.getDeclaredField("unusable");
            unusable.setAccessible(true);
            ((java.util.Set<?>) unusable.get(null)).remove(texture);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }

    private static Object config() throws ReflectiveOperationException {
        try {
            Class<?> mod = Class.forName("org.polyfrost.skinlayers.SkinLayers");
            Object instance = mod.getField("INSTANCE").get(null);
            return mod.getMethod("getConfig").invoke(instance);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            try {
                return Class.forName("org.polyfrost.skinlayers.config.SkinLayersConfig").getField("INSTANCE").get(null);
            } catch (ClassNotFoundException ignoredLegacy) {
                return Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase").getField("config").get(null);
            }
        }
    }

    static Object read(Object target, String name) throws ReflectiveOperationException {
        try { return target.getClass().getField(name).get(target); }
        catch (NoSuchFieldException ignored) {
            Object value = target.getClass().getMethod("get" + capitalize(name)).invoke(target);
            if (value instanceof Boolean || value instanceof Number) return value;
            return value.getClass().getMethod("get").invoke(value);
        }
    }

    static void write(Object target, String name, Object value) throws ReflectiveOperationException {
        try {
            Field field = target.getClass().getField(name);
            field.set(target, value);
            return;
        } catch (NoSuchFieldException ignored) { }
        Class<?> primitive = value instanceof Boolean ? boolean.class : int.class;
        try {
            target.getClass().getMethod("set" + capitalize(name), primitive).invoke(target, value);
        } catch (NoSuchMethodException ignored) {
            Object option = target.getClass().getMethod("get" + capitalize(name)).invoke(target);
            option.getClass().getMethod("set", Object.class).invoke(option, value);
        }
    }

    private static void save(Object config) throws ReflectiveOperationException {
        try {
            Class<?> mod = Class.forName("org.polyfrost.skinlayers.SkinLayers");
            mod.getMethod("save").invoke(mod.getField("INSTANCE").get(null));
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            if (config.getClass().getName().startsWith("org.polyfrost.")) {
                Class<?> manager = Class.forName("net.ornithemc.osl.config.api.ConfigManager");
                for (Method method : manager.getMethods()) {
                    if (method.getName().equals("save") && method.getParameterCount() == 1 &&
                            method.getParameterTypes()[0].isInstance(config)) {
                        method.invoke(null, config);
                        return;
                    }
                }
                throw new NoSuchMethodException("OSL ConfigManager.save");
            }
            Class<?> mod = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase");
            mod.getMethod("writeConfig").invoke(mod.getField("instance").get(null));
        }
    }

    private static String capitalize(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
}
