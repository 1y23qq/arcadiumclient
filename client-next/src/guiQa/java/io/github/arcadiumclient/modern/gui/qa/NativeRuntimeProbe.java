// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeRuntimeProbe {
    private record Tint(int stroke, int argb, net.minecraft.world.item.ItemDisplayContext context) {}
    private static boolean glintActive, handActive;
    private static final Map<String, Integer> glintDraws = new LinkedHashMap<>();
    private static final Map<Tint, Integer> vertexTints = new LinkedHashMap<>();
    private static final Map<String, Integer> drawProfiles = new LinkedHashMap<>();
    private static ThreadLocal<Integer> activeStroke;
    private static int handAttempts, handReturns;
    private NativeRuntimeProbe() {}
    @SuppressWarnings("unchecked")
    public static void beginGlint() {
        glintActive = true; glintDraws.clear(); vertexTints.clear(); drawProfiles.clear();
        try {
            var field = io.github.arcadiumclient.modern.client.glint.ModernGlint.class.getDeclaredField("STROKE");
            field.setAccessible(true); activeStroke = (ThreadLocal<Integer>) field.get(null);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not observe the actual glint stroke scope", failure); }
    }
    public static void glintDraw(String pipeline) {
        if (glintActive && pipeline.startsWith("arcadium:pipeline/")) {
            glintDraws.merge(pipeline, 1, Integer::sum);
            drawProfiles.merge(org.polyfrost.oneconfig.api.config.v1.ConfigManager.activeProfile(), 1, Integer::sum);
        }
    }
    public static void vertexTint(int color) {
        if (!glintActive) return;
        var key = new Tint(activeStroke.get(), color, io.github.arcadiumclient.modern.client.glint.ModernGlint.currentContext());
        vertexTints.merge(key, 1, Integer::sum);
    }
    public static int guiVertices(int stroke, int argb) {
        return vertexTints.getOrDefault(new Tint(stroke, argb, net.minecraft.world.item.ItemDisplayContext.GUI), 0);
    }
    public static Map<String, Object> glint() {
        var report = new LinkedHashMap<String, Object>();
        report.put("pipelines", Map.copyOf(glintDraws));
        report.put("profiles", Map.copyOf(drawProfiles));
        report.put("vertexTints", vertexTints.entrySet().stream().map(entry -> Map.of("stroke", entry.getKey().stroke,
            "argb", String.format("%08X", entry.getKey().argb), "context", entry.getKey().context.name(), "vertices", entry.getValue())).toList());
        report.put("ownDrawCalls", glintDraws.values().stream().mapToInt(Integer::intValue).sum());
        return report;
    }
    public static void beginHand() { handActive = true; handAttempts = handReturns = 0; }
    public static void handAttempt() { if (handActive) handAttempts++; }
    public static void handReturn() { if (handActive) handReturns++; }
    public static Map<String, Integer> hand() { return Map.of("attempts", handAttempts, "completed", handReturns); }
}
