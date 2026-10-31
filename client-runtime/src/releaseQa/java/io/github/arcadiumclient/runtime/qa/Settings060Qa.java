package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.gui.SharedGameOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class Settings060Qa {
    private static int ticks;
    private static boolean done;
    public static boolean enabled() { return System.getProperty("arcadium.settings060.output") != null; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    public static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        OneConfigConfig.limitUnfocusedFps = false;
        if (++ticks < 80) return;
        done = true;
        var result = new LinkedHashMap<String, Object>();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline account");
            ClientPreferences.setSetupComplete(true);
            Path options = mc.gameDir.toPath().resolve("options.txt");
            boolean reopen = Boolean.getBoolean("arcadium.settings060.reopen");
            if (reopen) {
                verify();
                var selected = mc.getResourcePacks().getSelectedPacks().stream().map(pack -> pack.getName()).toList();
                result.put("selectedPacks", selected);
                require(selected.contains("persistence-qa"), "The saved resource pack was not loaded: " + selected);
            }
            else {
                boolean filtered = Boolean.getBoolean("arcadium.settings060.filtered");
                if (filtered) mc.options.save();
                mc.options.mouseSensitivity = .73f; mc.options.gamma = .62f; mc.options.fpsLimit = 143; mc.options.ambientOcclusion = 1;
                mc.options.resourcePacks.clear(); mc.options.resourcePacks.add("persistence-qa");
                var bindings = mc.options.keyBindings;
                int attackIndex = -1;
                if (filtered) for (int i = 0; i < bindings.length; i++) if (bindings[i] == mc.options.attackKey) { attackIndex = i; bindings[i] = mc.options.useKey; break; }
                try { mc.options.save(); } finally { if (attackIndex >= 0) bindings[attackIndex] = mc.options.attackKey; }
                result.put("filteredControls", filtered);
                require(Files.isRegularFile(options), "Minecraft did not create options.txt");
                result.put("saved", SharedGameOptions.read(options));
                mc.options.mouseSensitivity = .1f; mc.options.gamma = .1f; mc.options.fpsLimit = 30; mc.options.ambientOcclusion = 0;
                mc.options.resourcePacks.clear(); mc.options.load(); verify();
            }
            result.put("reopened", reopen); result.put("passed", true);
        } catch (Throwable error) { result.put("passed", false); result.put("error", error.toString()); error.printStackTrace(); }
        try { Files.writeString(Path.of(System.getProperty("arcadium.settings060.output")), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
    private static void verify() {
        var options = Minecraft.getInstance().options;
        require(Math.abs(options.mouseSensitivity - .73f) < .0001f, "Sensitivity was reset: " + options.mouseSensitivity);
        require(Math.abs(options.gamma - .62f) < .0001f, "Brightness was reset: " + options.gamma);
        require(options.fpsLimit == 143, "FPS limit was reset: " + options.fpsLimit);
        require(options.ambientOcclusion == 1, "Smooth lighting was reset: " + options.ambientOcclusion);
        require(options.resourcePacks.equals(java.util.List.of("persistence-qa")), "Resource packs were reset: " + options.resourcePacks);
    }
}
