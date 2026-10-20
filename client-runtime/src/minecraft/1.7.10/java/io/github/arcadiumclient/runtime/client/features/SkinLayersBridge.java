// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import io.github.arcadiumclient.runtime.client.skins.LegacyPlayerSkins;
import java.lang.reflect.Field;

public final class SkinLayersBridge {
    private static volatile boolean[] parts = new boolean[6];
    private static volatile int distance = 24;

    private SkinLayersBridge() {}

    public static boolean available() { return true; }

    public static Boolean current() {
        for (boolean part : parts) if (part) return true;
        return false;
    }

    public static boolean part(int index) { return parts[index]; }
    public static int distance() { return distance; }

    public static void apply(boolean head, boolean body, boolean leftArm, boolean rightArm,
                             boolean leftLeg, boolean rightLeg, int renderDistance) {
        parts = new boolean[]{head, body, leftArm, rightArm, leftLeg, rightLeg};
        distance = Math.max(4, Math.min(64, renderDistance));
    }

    public static void discardTexture(Object texture) { LegacyPlayerSkins.discard(texture); }

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
        try {
            target.getClass().getMethod("set" + capitalize(name), value instanceof Boolean ? boolean.class : int.class).invoke(target, value);
        } catch (NoSuchMethodException ignored) {
            Object option = target.getClass().getMethod("get" + capitalize(name)).invoke(target);
            option.getClass().getMethod("set", Object.class).invoke(option, value);
        }
    }

    private static String capitalize(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
}
