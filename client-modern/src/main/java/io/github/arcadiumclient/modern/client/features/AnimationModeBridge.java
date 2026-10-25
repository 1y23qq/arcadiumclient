// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class AnimationModeBridge {
    private static final String[] CATEGORIES = {"items", "movement", "screen", "fixes", "other"};
    private final Object config;
    private final Map<Object, Map<Field, Object>> overflow = new LinkedHashMap<>();
    private final Map<String, Object> runtime = new LinkedHashMap<>();
    private boolean attached;

    public AnimationModeBridge(Object config) throws ReflectiveOperationException {
        this.config = config;
        for (String name : CATEGORIES) {
            Field field = config.getClass().getField(name);
            if (!field.trySetAccessible()) throw new IllegalAccessException(field.toString());
            Object original = field.get(config);
            overflow.put(original, new LinkedHashMap<>());
            runtime.put(name, original);
        }
    }

    public synchronized void apply(Map<String, Object> values) throws ReflectiveOperationException {
        if (!attached) {
            for (var entry : overflow.entrySet()) {
                entry.getValue().clear();
                for (Field field : entry.getKey().getClass().getFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) entry.getValue().put(field, field.get(entry.getKey()));
                }
            }
        }
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
        attached = true;
    }

    public synchronized void restore() throws IllegalAccessException {
        if (!attached) return;
        for (var entry : overflow.entrySet()) for (var value : entry.getValue().entrySet()) value.getKey().set(entry.getKey(), value.getValue());
        attached = false;
    }

    public synchronized boolean isAttached() {
        return attached;
    }

    public synchronized boolean withOriginalValues(BooleanSupplier write) {
        if (!attached) return write.getAsBoolean();
        var live = new LinkedHashMap<Object, Map<Field, Object>>();
        try {
            for (var category : overflow.entrySet()) {
                var values = new LinkedHashMap<Field, Object>();
                for (Field field : category.getValue().keySet()) values.put(field, field.get(category.getKey()));
                live.put(category.getKey(), values);
            }
            try {
                setValues(overflow);
                return write.getAsBoolean();
            } finally {
                setValues(live);
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not preserve the active animation state while saving", failure);
        }
    }

    private static void setValues(Map<Object, Map<Field, Object>> categories) throws IllegalAccessException {
        for (var category : categories.entrySet()) for (var value : category.getValue().entrySet()) value.getKey().set(category.getKey(), value.getValue());
    }

    private static void neutralize(Object category) throws ReflectiveOperationException {
        Object defaults = category.getClass().getConstructor().newInstance();
        for (Field field : category.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            Class<?> type = field.getType();
            Object value = field.get(defaults);
            if (type == boolean.class) value = false;
            else if (type.isEnum()) {
                String neutral = switch (type.getSimpleName()) {
                    case "CapeMovementSetting" -> "V1_12";
                    case "DroppedItemsSetting" -> "MODEL";
                    case "DamageTintHeldItemsSetting" -> "OFF";
                    case "HitColorArmorSetting" -> "UNCHANGED";
                    case "UnsneakSetting" -> "MATCH_SNEAK_ANIMATION";
                    default -> "VANILLA";
                };
                boolean found = false;
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(neutral)) { value = constant; found = true; break; }
                }
                if (!found) for (Object constant : type.getEnumConstants()) if (((Enum<?>) constant).name().equals("V1_8")) { value = constant; break; }
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
