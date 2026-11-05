// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.config.v1.*;

final class CaptureContext {
    private static final String PREFIX = "io.github.arcadiumclient.runtime.client.optimizations.";
    private static final Pattern CONFIG_ID = Pattern.compile("(?:arcadium-[a-z0-9-]+|oneconfig|themes|hybedwars|vanillahud|polyblur|polysprint|polyhitbox|overflowanimations|overflowparticles|colorsaturation|evergreenhud|no-render|fullbright|nametags)\\.json");
    private static final Pattern SETTING_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_:-]*");
    private static final Pattern PRIVATE_KEY = Pattern.compile(".*(token|password|secret|apikey|account|username|uuid|serveraddress).*", Pattern.CASE_INSENSITIVE);
    private static final String[] COUNTER_CLASSES = {"GpuModelTransforms", "GpuPlayerPipeline", "GpuEquipmentPipeline", "GpuSkinLayers", "NativePlayerPosePipeline", "GpuResidentPlayerState", "GpuTerrainCommands", "GpuNameTags",
        "RetainedNameTags", "FastInvisibleHolograms", "RenderHookAllocationControl", "EfficientMotionBlur", "GpuParticles",
        "ScoreboardPreparation", "FastFramePresentation", "RetainedHudBuffers", "WorldRenderScale"};
    private CaptureContext() { }

    static Map<String, Object> environment(Minecraft mc) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("os.name", "os.version", "os.arch", "java.vendor", "java.runtime.version", "java.vm.name")) out.put(key, CaptureProfile.cleanHardware(System.getProperty(key)));
        out.put("availableProcessors", Runtime.getRuntime().availableProcessors()); out.put("heapMaxBytes", Runtime.getRuntime().maxMemory());
        var bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean os) out.put("physicalMemoryBytes", os.getTotalMemorySize());
        out.put("glVendor", CaptureProfile.cleanHardware(GL11.glGetString(GL11.GL_VENDOR)));
        out.put("glRenderer", CaptureProfile.cleanHardware(GL11.glGetString(GL11.GL_RENDERER)));
        out.put("glVersion", CaptureProfile.cleanHardware(GL11.glGetString(GL11.GL_VERSION)));
        var caps = GL.getCapabilities();
        out.put("gpuCapabilities", Map.of("OpenGL33", caps.OpenGL33, "OpenGL43", caps.OpenGL43,
            "timerQuery", caps.OpenGL33 || caps.GL_ARB_timer_query, "multiDrawIndirect", caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect,
            "bufferStorage", caps.OpenGL44 || caps.GL_ARB_buffer_storage));
        out.put("minecraftVersion", "1.8.9"); out.put("mappingNamespace", FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace());
        List<Map<String, String>> mods = new ArrayList<>();
        FabricLoader.getInstance().getAllMods().stream().sorted(Comparator.comparing(mod -> mod.getMetadata().getId())).limit(512)
            .forEach(mod -> mods.add(Map.of("id", safeVersion(mod.getMetadata().getId()), "version", safeVersion(mod.getMetadata().getVersion().getFriendlyString()))));
        out.put("mods", mods);
        out.put("arcadiumVersion", FabricLoader.getInstance().getModContainer("arcadium_runtime").map(mod -> safeVersion(mod.getMetadata().getVersion().getFriendlyString())).orElse("unknown"));
        out.put("safeJvmFlags", safeJvmFlags(ManagementFactory.getRuntimeMXBean().getInputArguments()));
        return out;
    }

    static String safeVersion(String text) {
        if (text == null || text.length() > 160 || !text.matches("[A-Za-z0-9_.+~:-]+")) return "unreported";
        return text;
    }

    static Map<String, String> fingerprints() {
        Map<String, String> result = new TreeMap<>();
        for (var entry : Map.of("runtime", PerformanceCapture.class, "core", org.polyfrost.oneconfig.internal.ui.api.PerformanceCapturePage.class).entrySet()) {
            try {
                Path source = Path.of(entry.getValue().getProtectionDomain().getCodeSource().getLocation().toURI());
                if (!Files.isRegularFile(source) || Files.size(source) > 256L * 1024 * 1024) continue;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream stream = Files.newInputStream(source)) {
                    byte[] buffer = new byte[65536]; int read;
                    while ((read = stream.read(buffer)) >= 0) digest.update(buffer, 0, read);
                }
                result.put(entry.getKey() + "JarSha256", HexFormat.of().formatHex(digest.digest()));
            } catch (Exception unavailable) { }
        }
        return result;
    }

    static List<String> safeJvmFlags(List<String> arguments) {
        return arguments.stream().filter(value -> value.matches("-Xm[sx][0-9]+[kKmMgG]?") ||
            value.matches("-XX:[+-](UseG1GC|UseZGC|UseShenandoahGC|UseParallelGC|UseSerialGC|AlwaysPreTouch|UseStringDeduplication|DisableExplicitGC)") ||
            value.matches("-XX:(MaxGCPauseMillis|G1HeapRegionSize|ConcGCThreads|ParallelGCThreads|ActiveProcessorCount|G1NewSizePercent|G1MaxNewSizePercent|InitiatingHeapOccupancyPercent)=[0-9]+[kKmMgG]?"))
            .limit(64).toList();
    }

    static Map<String, Object> settings(Minecraft mc) {
        Map<String, Object> out = new TreeMap<>(), game = new TreeMap<>();
        if (mc.options != null) {
            var options = mc.options;
            game.put("viewDistance", options.viewDistance); game.put("fpsLimit", options.fpsLimit); game.put("vsync", options.vsync);
            game.put("fancyGraphics", options.fancyGraphics); game.put("ambientOcclusion", options.ambientOcclusion);
            game.put("cloudRenderMode", options.cloudRenderMode); game.put("mipmapLevels", options.mipmapLevels);
            game.put("anaglyph", options.anaglyph); game.put("fov", options.fov); game.put("gamma", options.gamma);
            game.put("particles", options.particles); game.put("guiScale", options.guiScale); game.put("entityShadows", options.entityShadows);
            game.put("useVbo", options.useVbo); game.put("perspective", options.perspective); game.put("hideGui", options.hideGui);
        }
        game.put("windowWidth", mc.width); game.put("windowHeight", mc.height); game.put("fullscreen", mc.isFullscreen());
        game.put("selectedResourcePackCount", mc.options == null ? 0 : mc.options.resourcePacks.size());
        out.put("game", game);
        Map<String, Object> configs = new TreeMap<>();
        for (Tree tree : ConfigManager.active().trees()) {
            String id = tree.getID();
            if (id == null || !CONFIG_ID.matcher(id).matches()) continue;
            Map<String, Object> values = numericTree(tree, 0);
            if (!values.isEmpty()) configs.put(id, values);
        }
        out.put("numericAndBooleanModuleSettings", configs);
        return out;
    }

    static Map<String, Object> numericTree(Tree tree, int depth) {
        Map<String, Object> out = new TreeMap<>();
        if (depth > 5) return out;
        for (var entry : tree.map.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() > 80 || !SETTING_KEY.matcher(key).matches() || PRIVATE_KEY.matcher(key).matches()
                || Set.of("toggleSprintState", "toggleSneakState", "toggled", "setupComplete", "preview").contains(key)) continue;
            if (entry.getValue() instanceof Tree child) {
                var values = numericTree(child, depth + 1); if (!values.isEmpty()) out.put(key, values);
            } else if (entry.getValue() instanceof Property<?> property) {
                try { Object value = property.get(); if (numeric(value)) out.put(key, value); }
                catch (RuntimeException unreadable) { }
            }
            if (out.size() >= 512) break;
        }
        return out;
    }

    private static boolean numeric(Object value) {
        return value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
            || (value instanceof Float f && Float.isFinite(f)) || (value instanceof Double d && Double.isFinite(d));
    }

    static Map<String, Long> counters() {
        Map<String, Long> out = new TreeMap<>();
        for (String name : COUNTER_CLASSES) try {
            Class<?> type = Class.forName(PREFIX + name, false, CaptureContext.class.getClassLoader());
            for (Field field : type.getFields()) if (Modifier.isStatic(field.getModifiers()) && (field.getType() == long.class || field.getType() == int.class))
                out.put(name + "." + field.getName(), ((Number) field.get(null)).longValue());
        } catch (Throwable ignored) { }
        out.put("NickHider.cacheHits", io.github.arcadiumclient.runtime.client.features.NickHider.cacheHits());
        out.put("NickHider.cacheMisses", io.github.arcadiumclient.runtime.client.features.NickHider.cacheMisses());
        if (Minecraft.getInstance().textRenderer instanceof org.polyfrost.oneconfig.internal.GameFontMetricCounters metrics) {
            out.put("FontMetrics.cacheHits", metrics.arcadium$metricHits()); out.put("FontMetrics.cacheMisses", metrics.arcadium$metricMisses());
        }
        out.put("NativeFont.batchedGlyphs", org.polyfrost.oneconfig.internal.ui.compose.LegacyGameFont.batchedGlyphs);
        out.put("NativeFont.immediateGlyphs", org.polyfrost.oneconfig.internal.ui.compose.LegacyGameFont.immediateGlyphs);
        return out;
    }

    static Map<String, Object> optimizationState() {
        Map<String, Object> state = new TreeMap<>();
        for (String name : List.of("GpuModelTransforms", "GpuPlayerPipeline", "GpuEquipmentPipeline", "GpuResidentPlayerState", "GpuTerrainCommands", "GpuParticles", "RetainedNameTags")) {
            Map<String, Object> flags = new TreeMap<>();
            try {
                Class<?> type = Class.forName(PREFIX + name, false, CaptureContext.class.getClassLoader());
                for (String fieldName : List.of("failed", "checked", "supported", "initialized", "forgeChecked", "forgeActive", "players", "observedPlayers")) {
                    try { Field field = type.getDeclaredField(fieldName); field.setAccessible(true); Object value = field.get(null); if (value instanceof Boolean || value instanceof Integer) flags.put(fieldName, value); }
                    catch (ReflectiveOperationException absent) { }
                }
            } catch (ReflectiveOperationException unavailable) { }
            state.put(name, flags);
        }
        state.put("fonts", Map.of("gameSource", org.polyfrost.oneconfig.internal.FontPreferences.gameSource().name(),
            "modSource", org.polyfrost.oneconfig.internal.FontPreferences.modSource().name(),
            "vanillaBatchingEnabled", org.polyfrost.oneconfig.internal.TextOptimizationOptions.vanillaFontBatching,
            "metricCacheEnabled", org.polyfrost.oneconfig.internal.TextOptimizationOptions.fontMetricCache,
            "maskCacheEnabled", org.polyfrost.oneconfig.internal.TextOptimizationOptions.nameMaskCache));
        return Map.copyOf(state);
    }

    static Map<String, Object> scene(Minecraft mc, long elapsed) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("atSeconds", elapsed / 1e9); out.put("focused", Display.isActive()); out.put("screenOpen", mc.screen != null);
        out.put("worldLoaded", mc.world != null); out.put("singleplayer", mc.isIntegratedServerRunning());
        out.put("loadedPlayers", mc.world == null ? 0 : mc.world.players.size());
        out.put("loadedEntities", mc.world == null ? 0 : mc.world.entities.size());
        out.put("windowWidth", mc.width); out.put("windowHeight", mc.height);
        return out;
    }

    static Map<String, Object> process(long elapsed, long renderThread) {
        Map<String, Object> out = new LinkedHashMap<>(); out.put("atSeconds", elapsed / 1e9);
        var threads = ManagementFactory.getThreadMXBean();
        out.put("renderThreadCpuNs", threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled() ? threads.getThreadCpuTime(renderThread) : -1);
        var bean = ManagementFactory.getOperatingSystemMXBean();
        out.put("processCpuNs", bean instanceof com.sun.management.OperatingSystemMXBean os ? os.getProcessCpuTime() : -1);
        Runtime runtime = Runtime.getRuntime(); out.put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory()); out.put("heapCommittedBytes", runtime.totalMemory());
        out.put("liveThreads", threads.getThreadCount());
        if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
            out.put("processCpuLoad", finite(os.getProcessCpuLoad())); out.put("systemCpuLoad", finite(os.getCpuLoad()));
            out.put("freePhysicalMemoryBytes", os.getFreeMemorySize());
        }
        return out;
    }
    private static double finite(double value) { return Double.isFinite(value) ? value : -1; }
}
