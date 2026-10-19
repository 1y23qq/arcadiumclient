// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class NickBookReader {
    private final Map<Class<?>, List<Field>> fields = new HashMap<>();

    public String read(Object screen, Class<?> componentType, Function<Object, String> text) {
        if (screen == null || componentType == null) return "";
        List<Field> candidates = fields.computeIfAbsent(screen.getClass(), type -> {
            List<Field> result = new ArrayList<>();
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                for (Field field : parent.getDeclaredFields()) {
                    if (!List.class.isAssignableFrom(field.getType())) continue;
                    try { field.setAccessible(true); result.add(field); }
                    catch (RuntimeException ignored) { }
                }
            }
            return result;
        });
        for (Field field : candidates) {
            try {
                Object value = field.get(screen);
                if (!(value instanceof List<?>)) continue;
                List<?> lines = (List<?>) value;
                for (int i = 0; i + 1 < Math.min(lines.size(), 80); i++) {
                    if (!componentType.isInstance(lines.get(i)) || !componentType.isInstance(lines.get(i + 1))) continue;
                    if (!"you:".equalsIgnoreCase(PlayerNameUtil.plain(text.apply(lines.get(i))))) continue;
                    String nick = PlayerNameUtil.plain(text.apply(lines.get(i + 1)));
                    if (PlayerNameUtil.valid(nick)) return nick;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return "";
    }
}
