// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;
import org.polyfrost.oneconfig.internal.ui.keybind.KeybindCatalogKt;
import org.polyfrost.oneconfig.internal.ui.keybind.KeybindProviderRegistry;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.KeybindsGraph;
import org.polyfrost.oneconfig.internal.ui.shell.LocalNavController;
import org.polyfrost.oneconfig.internal.ui.shell.ShellState;
import org.lwjgl.glfw.GLFW;

final class WaypointsKeybindQa {
    private final Path output;
    private int ticks, phase;
    private KeyMapping mapping;
    private KeyMapping[] beforeMappings;
    private int revision;
    private final LinkedHashMap<String, Object> report = new LinkedHashMap<>();

    WaypointsKeybindQa(Path output) { this.output = output; }

    @SuppressWarnings("unchecked")
    boolean tick(Minecraft mc) throws Exception {
        QaRuntimeErrors.check();
        if (mc.getOverlay() != null || mc.screen == null || ++ticks < 60) return false;
        if (phase == 0) {
            Class<?> waypoints = Class.forName("com.wwaypoints.WaypointsClient");
            boolean reopened = Boolean.getBoolean("arcadium.guiQa.waypointsReopened");
            var presets = (java.util.Collection<java.util.Map.Entry<String, KeyMapping>>) waypoints.getMethod("getPresetCreateWaypointKeys").invoke(null);
            mapping = presets.stream().map(java.util.Map.Entry::getValue)
                .filter(key -> key.saveString().equals(reopened ? "key.keyboard.g" : "key.keyboard.n"))
                .findFirst().orElseThrow(() -> new IllegalStateException("No native create-waypoint preset had the expected binding; presets=" + presets.size()));
            require(mapping.saveString().equals(reopened ? "key.keyboard.g" : "key.keyboard.n"), "Unexpected saved waypoint binding: " + mapping.saveString());
            var group = KeybindCatalogKt.collectAllKeybindGroups().stream()
                .filter(item -> item.getModTitle().toString().contains("wWaypoints"))
                .filter(item -> item.getEntries().stream().anyMatch(entry -> entry.getPath().equals("minecraft." + mapping.getName())))
                .findFirst().orElseThrow(() -> new IllegalStateException("Waypoint creation was not discoverable under wWaypoints"));
            var property = (Property<OneConfigKeybind>) group.getEntries().stream()
                .filter(entry -> entry.getPath().equals("minecraft." + mapping.getName())).findFirst().orElseThrow().getProp();
            require(property.getTitle().toString().startsWith("Create Waypoint:"), "The waypoint action had no clear label");
            report.put("group", group.getModTitle().toString());
            report.put("title", property.getTitle().toString());
            report.put("savedBindingOnStart", mapping.saveString());
            property.set(new OneConfigKeybind(new int[]{GLFW.GLFW_KEY_G}, null, (byte) 0, 0L, down -> true));
            require(mapping.saveString().equals("key.keyboard.g"), "Editing the GUI property did not change the mod's live binding");
            while (mapping.consumeClick()) { }
            KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_N));
            require(!mapping.consumeClick(), "The previous key still activated waypoint creation");
            KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
            require(mapping.consumeClick(), "The new key did not activate the native mapping");
            String options = Files.readString(mc.gameDirectory.toPath().resolve("options.txt"));
            require(options.contains("key_" + mapping.getName() + ":key.keyboard.g"), "The changed waypoint key was not saved");
            report.put("nativeKeyDispatchUpdated", true);
            report.put("optionsPersisted", true);
            beforeMappings = mc.options.keyMappings;
            revision = KeybindProviderRegistry.INSTANCE.getRevision().getIntValue();
            KeyMapping dynamic = new KeyMapping("Create: Arcadium QA", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, mapping.getCategory());
            KeyMapping[] added = Arrays.copyOf(beforeMappings, beforeMappings.length + 1);
            added[beforeMappings.length] = dynamic;
            replaceMappings(mc, added);
            OneConfigUI.open();
            phase = 1; ticks = 0;
            return false;
        }
        if (phase == 1) {
            require(KeybindProviderRegistry.INSTANCE.getRevision().getIntValue() > revision, "Late waypoint keys did not refresh the GUI catalog");
            require(KeybindCatalogKt.collectAllKeybindGroups().stream().flatMap(group -> group.getEntries().stream())
                .anyMatch(entry -> entry.getPath().equals("minecraft.Create: Arcadium QA")), "A new waypoint preset remained hidden");
            replaceMappings(mc, beforeMappings);
            KeyMapping.resetMapping();
            LocalNavController.NavControllerWrapper.INSTANCE.navigate(KeybindsGraph.INSTANCE, true);
            ShellState.INSTANCE.setSearchQuery("Waypoints");
            report.put("latePresetRefreshed", true);
            phase = 2; ticks = 0;
            return false;
        }
        require("Keybinds".equals(ShellState.INSTANCE.getTitle()), "The client Keybinds page did not render");
        Files.createDirectories(output);
        report.put("passed", true);
        report.put("reopened", Boolean.getBoolean("arcadium.guiQa.waypointsReopened"));
        Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        mc.options.save();
        mc.stop();
        return true;
    }

    private static void replaceMappings(Minecraft mc, KeyMapping[] mappings) throws Exception {
        Class<?> accessor = Class.forName("com.wwaypoints.client.mixin.OptionsKeyMappingsAccessor");
        accessor.getMethod("wwaypoints$setKeyMappings", KeyMapping[].class).invoke(mc.options, (Object) mappings);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
