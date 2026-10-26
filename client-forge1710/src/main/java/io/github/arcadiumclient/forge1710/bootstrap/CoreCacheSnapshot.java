// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.bootstrap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CoreCacheSnapshot {
    private static final ClassValue<Layout> LAYOUTS = new ClassValue<>() {
        @Override protected Layout computeValue(Class<?> type) {
            try {
                Field[] fields = Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).toArray(Field[]::new);
                Field name = type.getDeclaredField("transformerClassName");
                Field entries = type.getDeclaredField("transformationMap");
                if (type.getSuperclass() != Object.class || fields.length != 2 || name.getType() != String.class || entries.getType() != Map.class)
                    throw new ReflectiveOperationException("Unexpected transformer data layout");
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                for (Field field : fields) field.setAccessible(true);
                return new Layout(constructor, fields, entries);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("The pinned CoreTweaks LITE cache layout has changed", error);
            }
        }
    };

    private CoreCacheSnapshot() { }

    public static Map<?, ?> snapshot(Map<?, ?> original) {
        if (original.getClass() != ConcurrentHashMap.class)
            throw new IllegalStateException("The pinned CoreTweaks LITE cache map has changed");
        var snapshot = new ConcurrentHashMap<Object, Object>();
        for (var entry : original.entrySet()) {
            Object data = entry.getValue();
            Layout layout = LAYOUTS.get(data.getClass());
            try {
                Object copy = layout.constructor.newInstance();
                for (Field field : layout.fields) {
                    Object value = field.get(data);
                    if (field.equals(layout.entries)) {
                        if (!(value instanceof Map<?, ?> entries)) throw new IllegalStateException("Missing transformer cache entries");
                        value = new ConcurrentHashMap<>(entries);
                    }
                    field.set(copy, value);
                }
                snapshot.put(entry.getKey(), copy);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not snapshot the CoreTweaks LITE cache", error);
            }
        }
        return snapshot;
    }

    private record Layout(Constructor<?> constructor, Field[] fields, Field entries) { }
}
