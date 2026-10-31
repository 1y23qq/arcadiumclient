// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.debug;

import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import io.github.arcadiumclient.shared.debug.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;

public final class CustomF3Data {
    private static long refreshed, previousBytes, previousAllocation;
    private static long allocationRate;
    private static DebugView.Frame frame;
    private CustomF3Data() { }
    public static DebugView.Frame collect(Minecraft mc, DebugInfoSource source) {
        long now = System.nanoTime();
        if (CustomF3Config.getOptimizeDebugText() && frame != null && now - refreshed < 50_000_000L) return frame;
        DebugLayout layout = CustomF3Config.layout();
        Map<String, String> raw = NativeDebugInfo.collect(mc, source);
        LegacyDebugExtras.collect(mc,raw);
        Runtime runtime = Runtime.getRuntime(); long bytes = runtime.totalMemory() - runtime.freeMemory();
        if (previousAllocation != 0 && now - previousAllocation >= 500_000_000L) { allocationRate = Math.max(0, bytes - previousBytes) * 1_000_000_000L / (now - previousAllocation); previousBytes = bytes; previousAllocation = now; }
        if (previousAllocation == 0) { previousAllocation = now; previousBytes = bytes; }
        raw.put("allocation_rate", allocationRate / 1024 / 1024 + " MB/s");
        raw.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        if (CustomF3Config.getSpoofSpecs() && !CustomF3Config.getGpuVendor().isBlank()) {
            raw.computeIfPresent("display", (key, value) -> value.replaceAll("\\([^)]*\\)$", "(" + java.util.regex.Matcher.quoteReplacement(CustomF3Config.overrideText("vendor", "")) + ")"));
        }
        Map<String, List<DebugView.Line>> values = new HashMap<>();
        for (var module : layout.all()) {
            if (!module.enabled || module.kind.equals("empty")) continue;
            List<DebugView.Line> lines = new ArrayList<>();
            for (var entry : module.definition().lines().entrySet()) {
                String id = entry.getKey(); if (!module.lines.getOrDefault(id, true)) continue;
                String value = id.equals("time") ? LocalDateTime.now().format(DateTimeFormatter.ofPattern(module.timeFormat)) : raw.get(id);
                if (value == null || value.isEmpty()) continue;
                value = CustomF3Config.overrideText(id, value);
                int color = module.color("valueColor");
                if (id.equals("fps")) {
                    int fps; try { fps = Integer.parseInt(value.split(" ")[0]); } catch (NumberFormatException failure) { fps = 0; }
                    color = module.color(fps >= 60 ? "colorHigh" : fps >= 30 ? "colorMed" : "colorLow");
                }
                if (id.equals("memory_usage") && module.memoryColorToggle) { long percent = bytes * 100 / Math.max(1, runtime.maxMemory()); color = 0xFF000000 | (percent > 85 ? 0xFF5555 : percent > 65 ? 0xFFFF55 : 0x55FF55); }
                if ((module.kind.equals("help") || id.equals("chunk_culling")) && (value.startsWith("Visible") || value.startsWith("Hidden") || value.equals("Enabled") || value.equals("Disabled"))) color = module.color(value.startsWith("Hidden") || value.equals("Disabled") ? "disabledColor" : "enabledColor");
                String title = id.startsWith("misc_") || id.equals("fps") || id.equals("player_coords") ? "" : entry.getValue();
                if (module.kind.equals("coords") && Set.of("player_coords","block_coords","chunk_relative_coords","chunk_coords","velocity").contains(id)) {
                    String[] coordinates = value.trim().split("[ /]+");
                    if (coordinates.length == 3) {
                        List<DebugView.Span> spans = new ArrayList<>();
                        if (title.isEmpty()) { spans.add(new DebugView.Span("X",module.color("colorX"))); spans.add(new DebugView.Span("Y",module.color("colorY"))); spans.add(new DebugView.Span("Z: ",module.color("colorZ"))); }
                        else spans.add(new DebugView.Span(title+": ",module.color("nameColor")));
                        for(int axis=0;axis<3;axis++) { if(axis>0)spans.add(new DebugView.Span(" / ",module.color("valueColor"))); spans.add(new DebugView.Span(coordinates[axis],module.color(new String[]{"colorX","colorY","colorZ"}[axis]))); }
                        lines.add(new DebugView.Line(spans)); continue;
                    }
                }
                if (id.equals("entities") || id.equals("chunk_sections") || id.equals("sounds") || id.equals("ambient_sounds")) {
                    var count = java.util.regex.Pattern.compile("^(?:[CE]: )?(\\d+)/(\\d+)(.*)$").matcher(value);
                    if (count.matches()) {
                        lines.add(new DebugView.Line(List.of(new DebugView.Span(title+": ",module.color("nameColor")),new DebugView.Span(count.group(1)+"/",color),new DebugView.Span(count.group(2),module.color(id.equals("sounds") || id.equals("ambient_sounds") ? "maximumColor" : "totalColor")),new DebugView.Span(count.group(3),color)))); continue;
                    }
                }
                for (String part : value.split("\n")) lines.add(DebugView.label(title, part, module.color("nameColor"), color));
            }
            values.put(module.key, lines);
        }
        frame = DebugView.columns(layout, values, CustomF3Config.getSpaceModules()); refreshed = now; return frame;
    }
}
