// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import io.github.arcadiumclient.featuretest.mixin.LegacyScreenInput;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;

final class LegacyComposeQa {
    private LegacyComposeQa() {}

    static Object instance(String name) throws Exception { return Class.forName(name).getField("INSTANCE").get(null); }
    static Object stat(String name, String method, Object... arguments) throws Exception { return call(Class.forName(name), method, arguments); }

    static Object call(Object target, String name, Object... arguments) throws Exception {
        Class<?> type = target instanceof Class<?> owner ? owner : target.getClass();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                if (target instanceof Class<?> && !Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) if (!compatible(parameters[i], arguments[i])) matches = false;
                if (!matches) continue;
                method.setAccessible(true);
                try { return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, arguments); }
                catch (java.lang.reflect.InvocationTargetException failure) {
                    if (failure.getCause() instanceof Exception cause) throw cause;
                    if (failure.getCause() instanceof Error cause) throw cause;
                    throw failure;
                }
            }
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            if (target instanceof Class<?> && !Modifier.isStatic(method.getModifiers())) continue;
            boolean matches = true;
            Class<?>[] parameters = method.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) if (!compatible(parameters[i], arguments[i])) matches = false;
            if (!matches) continue;
            method.setAccessible(true);
            return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, arguments);
        }
        if (target instanceof Class<?>) {
            try { return call(type.getField("INSTANCE").get(null), name, arguments); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static boolean compatible(Class<?> type, Object argument) {
        if (argument == null) return !type.isPrimitive();
        if (!type.isPrimitive()) return type.isInstance(argument);
        return type == int.class && argument instanceof Integer || type == float.class && argument instanceof Float
            || type == double.class && argument instanceof Double || type == boolean.class && argument instanceof Boolean
            || type == long.class && argument instanceof Long || type == char.class && argument instanceof Character;
    }

    static Object field(Object target, String name) throws Exception {
        Class<?> type = target instanceof Class<?> owner ? owner : target.getClass();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field.get(target instanceof Class<?> ? null : target); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    static List<Object> nodes(Screen screen) throws Exception {
        Object scene = field(screen, "sceneOrNull");
        require(scene != null, "The Compose screen has not rendered");
        List<Object> result = children(call(call(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode"));
        for (Object layer : (Collection<?>) field(scene, "layers")) result.addAll(children(call(call(call(layer, "getOwner"), "getSemanticsOwner"), "getRootSemanticsNode")));
        return result;
    }

    private static List<Object> children(Object node) throws Exception {
        List<Object> result = new ArrayList<>(); result.add(node);
        for (Object child : (Collection<?>) call(node, "getChildren")) result.addAll(children(child));
        return result;
    }

    static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : (Iterable<?>) call(node, "getConfig")) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
            result.put((String) call(entry.getKey(), "getName"), entry.getValue());
        }
        return result;
    }

    static Object text(Screen screen, String text, boolean action) throws Exception {
        List<Object> nodes = nodes(screen); java.util.Collections.reverse(nodes);
        for (Object node : nodes) {
            Map<String, Object> values = properties(node);
            if (action && !values.containsKey("OnClick")) continue;
            if (values.get("Text") instanceof Collection<?> pieces) for (Object piece : pieces) {
                if (text.equals(call(piece, "getText"))) return node;
            }
        }
        throw new IllegalStateException("Missing UI text: " + text);
    }

    static boolean hasText(Screen screen, String text) throws Exception {
        try { text(screen, text, false); return true; } catch (IllegalStateException absent) { return false; }
    }

    static Object description(Screen screen, String label) throws Exception {
        for (Object node : nodes(screen)) if (properties(node).get("ContentDescription") instanceof Collection<?> values && values.contains(label)) return node;
        throw new IllegalStateException("Missing UI action: " + label);
    }

    static void click(Screen screen, String label) throws Exception { activate(text(screen, label, true)); }
    static void activate(Object node) throws Exception {
        require(Boolean.TRUE.equals(call(call(properties(node).get("OnClick"), "getAction"), "invoke")), "The UI action was rejected");
    }

    static void pointer(Minecraft client, Object node) throws Exception {
        Object rect = call(node, "getBoundsInRoot");
        double x = (((Number) call(rect, "getLeft")).doubleValue() + ((Number) call(rect, "getRight")).doubleValue()) / 2;
        double y = (((Number) call(rect, "getTop")).doubleValue() + ((Number) call(rect, "getBottom")).doubleValue()) / 2;
        Object platform = stat("org.polyfrost.oneconfig.api.platform.v1.Platform", "screen");
        double ratio = ((Number) call(platform, "pixelRatio")).doubleValue();
        int viewport = ((Number) call(platform, "viewportHeight")).intValue();
        Mouse.setCursorPosition((int) Math.round(x * ratio), viewport - (int) Math.round(y * ratio) - 1);
        Screen screen = client.screen;
        int sx = (int) Math.round(x * screen.width / (client.width / ratio));
        int sy = (int) Math.round(y * screen.height / (client.height / ratio));
        ((LegacyScreenInput) screen).arcadium$press(sx, sy, 0);
        ((LegacyScreenInput) screen).arcadium$release(sx, sy, 0);
    }

    static void search(Screen screen, String value) throws Exception {
        for (Object node : nodes(screen)) {
            Object action = properties(node).get("SetText");
            if (action == null) continue;
            Object text = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class).newInstance(value, List.of(), List.of());
            require(Boolean.TRUE.equals(call(call(action, "getAction"), "invoke", text)), "Search rejected the query");
            return;
        }
        throw new IllegalStateException("The screen has no editable search field");
    }

    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
