// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AnimationModeBridge {
    private static final String[] CATEGORIES = {"items", "movement", "screen", "fixes", "other"};
    private final Object config;
    private final Map<Field, Object> overflow = new LinkedHashMap<>();
    private final Map<String, Object> runtime = new LinkedHashMap<>();
    private boolean attached;

    public AnimationModeBridge(Object config) throws ReflectiveOperationException {
        this.config = config;
        for (String name : CATEGORIES) {
            Field field = config.getClass().getField(name);
            if (!field.trySetAccessible()) throw new IllegalAccessException(field.toString());
            Object original = field.get(config);
            Object neutral = field.getType().getConstructor().newInstance();
            neutralize(neutral);
            overflow.put(field, original);
            runtime.put(name, neutral);
        }
    }

    public synchronized void apply(Map<String, Object> values) throws ReflectiveOperationException {
        for (Object category : runtime.values()) neutralize(category);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String[] path = entry.getKey().split("\\.", 2);
            Object category = runtime.get(path[0]);
            if (category == null || path.length != 2) throw new IllegalArgumentException(entry.getKey());
            Field field = category.getClass().getField(path[1]);
            Object value = entry.getValue();
            if (field.getType().isEnum() && value instanceof String) value = enumConstant(field.getType(), (String) value);
            field.set(category, value);
        }
        for (Field field : overflow.keySet()) field.set(config, runtime.get(field.getName()));
        attached = true;
    }

    public synchronized void restore() throws IllegalAccessException {
        if (!attached) return;
        for (Map.Entry<Field, Object> entry : overflow.entrySet()) entry.getKey().set(config, entry.getValue());
        attached = false;
    }

    public synchronized boolean isAttached() {
        return attached;
    }

    private static void neutralize(Object category) throws ReflectiveOperationException {
        Object defaults = category.getClass().getConstructor().newInstance();
        for (Field field : category.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            Class<?> type = field.getType();
            Object value = field.get(defaults);
            if (type == boolean.class) value = false;
            else if (type.isEnum()) {
                String neutral = type.getSimpleName().equals("CapeMovementSetting") ? "V1_12" : "VANILLA";
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(neutral)) { value = constant; break; }
                }
            }
            field.set(category, value);
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        throw new IllegalArgumentException(type.getName() + "." + name);
    }
}
