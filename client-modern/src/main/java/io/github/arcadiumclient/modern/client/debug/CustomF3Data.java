// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.debug;

import io.github.arcadiumclient.modern.client.features.CustomF3Config;
import io.github.arcadiumclient.modern.client.debug.betterf3.modules.*;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.DebugLine;
import io.github.arcadiumclient.shared.debug.DebugLayout;
import io.github.arcadiumclient.shared.debug.DebugView;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.slf4j.LoggerFactory;

public final class CustomF3Data {
    private static final Map<String, BaseModule> MODULES = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static long refreshed;
    private static DebugView.Frame frame;
    private static final List<String> VANILLA_LEFT = new ArrayList<>(), VANILLA_RIGHT = new ArrayList<>();
    private CustomF3Data() { }
    public static void clear() { MODULES.clear();frame=null;refreshed=0;VANILLA_LEFT.clear();VANILLA_RIGHT.clear(); }
    public static void beginFrame() { VANILLA_LEFT.clear(); VANILLA_RIGHT.clear(); }
    public static void capture(List<String> lines, boolean left) { (left ? VANILLA_LEFT : VANILLA_RIGHT).addAll(lines); }

    public static DebugView.Frame collect(Minecraft client) {
        long now = System.nanoTime();
        if (CustomF3Config.getOptimizeDebugText() && frame != null && now - refreshed < 50_000_000L) return frame;
        DebugLayout layout = CustomF3Config.layout();
        var values = new HashMap<String, List<DebugView.Line>>();
        Set<String> active = new HashSet<>();
        for (DebugLayout.Module config : layout.all()) {
            active.add(config.key);
            if (!config.enabled || config.kind.equals("empty")) continue;
            try {
                BaseModule module = MODULES.computeIfAbsent(config.key, key -> create(config.kind));
                module.lines().forEach(line -> { line.enabled = config.lines.getOrDefault(line.id(), true); line.active = true; });
                for (var color : config.colors.entrySet()) module.getClass().getField(color.getKey()).set(module, TextColor.fromRgb(color.getValue()));
                if (module instanceof SystemModule system) { system.memoryColorToggle = config.memoryColorToggle; system.timeFormat = config.timeFormat; }
                module.update(client);
                if (module instanceof MiscLeftModule misc) misc.update(io.github.arcadiumclient.shared.debug.DebugLineFilter.extras(VANILLA_LEFT));
                if (module instanceof MiscRightModule misc) misc.update(io.github.arcadiumclient.shared.debug.DebugLineFilter.extras(VANILLA_RIGHT));
                if (module instanceof SystemModule) {
                    for (String key : List.of("cpu", "gpu", "gpu_driver", "opengl_version", "java_version")) {
                        String override = CustomF3Config.overrideText(key, "");
                        if (!override.isBlank() && module.line(key) != null) module.line(key).value(override);
                    }
                    String vendor = CustomF3Config.overrideText("vendor", "");
                    if (!vendor.isBlank()) module.line("display").value(client.getWindow().getWidth() + " x " + client.getWindow().getHeight() + " (" + vendor + ")");
                }
                List<DebugView.Line> result = new ArrayList<>();
                for (Component text : module.linesFormatted(client.showOnlyReducedInfo())) result.add(line(text));
                if (module instanceof SystemModule && config.lines.getOrDefault("os", true)) result.add(DebugView.label("OS", CustomF3Config.overrideText("os", System.getProperty("os.name") + " " + System.getProperty("os.arch")), config.color("nameColor"), config.color("valueColor")));
                values.put(config.key, result);
            } catch (Exception | LinkageError failure) {
                if (WARNED.add(config.kind)) LoggerFactory.getLogger("Arcadium/CustomF3").warn("Could not update debug module {}", config.kind, failure);
            }
        }
        MODULES.keySet().retainAll(active);
        frame = DebugView.columns(layout, values, CustomF3Config.getSpaceModules());
        refreshed = now;
        return frame;
    }
    private static BaseModule create(String kind) {
        return switch (kind) {
            case "minecraft" -> new MinecraftModule(); case "fps" -> new FpsModule(); case "graphics" -> new GraphicsModule();
            case "server" -> new ServerModule(); case "coords" -> new CoordsModule(); case "chunks" -> new ChunksModule();
            case "location" -> new LocationModule(); case "entity" -> new EntityModule(); case "sound" -> new SoundModule();
            case "help" -> new HelpModule(); case "system" -> new SystemModule(); case "target" -> new TargetModule();
            case "miscleft" -> new MiscLeftModule(); case "miscright" -> new MiscRightModule();
            default -> throw new IllegalArgumentException("Unknown debug module: " + kind);
        };
    }
    private static DebugView.Line line(Component component) {
        List<DebugView.Span> spans = new ArrayList<>();
        component.visit((style, text) -> { spans.add(new DebugView.Span(text, 0xFF000000 | (style.getColor() == null ? 0xE0E0E0 : style.getColor().getValue()))); return Optional.empty(); }, net.minecraft.network.chat.Style.EMPTY);
        return new DebugView.Line(spans);
    }
}
