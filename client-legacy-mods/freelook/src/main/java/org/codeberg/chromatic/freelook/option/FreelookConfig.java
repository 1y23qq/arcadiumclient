package org.codeberg.chromatic.freelook.option;

import org.polyfrost.oneconfig.internal.legacy.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import org.codeberg.chromatic.freelook.handler.FreelookHandler;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.annotations.*;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Stream;

public class FreelookConfig extends Config {
    public static final FreelookConfig INSTANCE = new FreelookConfig();

    @Keybind(
            title = "Activate Freelook"
    )
    public static OneConfigKeybind activationKey = KeybindHelper.builder()
            .action(FreelookHandler.INSTANCE::setKeyDown)
            .build();

    @Include
    public static boolean migratedVanillaKeybind = false;

    @Include
    public static boolean migratedDefaults = false;

    @Dropdown(
            title = "Perspective Type",
            options = { "First Person", "Third Person (Back)", "Third Person (Front)" }
    )
    public static int perspectiveType = 1;

    @Dropdown(
            title = "Press Mode",
            options = { "Hold", "Quick Press", "Toggle" }
    )
    public static int pressMode = 0;

    @Slider(
            title = "Hold Threshold",
            min = 50f, max = 1000f, step = 50f
    )
    public static float holdThreshold = 300f;

    @Dropdown(
            title = "On Cycle Change",
            options = { "Cycle and Freelook", "Stop Freelook", "Block Camera Cycle" }
    )
    public static int onCycleChange = 1;

    @Switch(
            title = "Smooth Camera"
    )
    public static boolean smoothCamera = true;

    @Slider(
            title = "Sensitivity (%)",
            min = 10f, max = 200f, step = 5f
    )
    public static float sensitivity = 100f;

    @Slider(
            title = "Cinematic Camera (%)",
            min = 0f, max = 100f, step = 5f
    )
    public static float cinematicCamera = 0f;

    @Accordion(
            title = "Pitch"
    )
    public static class Pitch {
        @Include
        public static boolean enabled = true;

        @Switch(
                title = "Invert"
        )
        public static boolean invert = false;

        @Switch(
                title = "Lock"
        )
        public static boolean lock = true;
    }

    @Accordion(
            title = "Yaw"
    )
    public static class Yaw {
        @Include
        public static boolean enabled = true;

        @Switch(
                title = "Invert"
        )
        public static boolean invert = false;
    }

    public void migrateVanillaKeybind() {
        if (migratedVanillaKeybind) return;
        migratedVanillaKeybind = true;

        try (Stream<String> lines = Files.lines(FabricLoader.getInstance().getGameDir().resolve("options.txt"))) {
            lines.filter(line -> line.startsWith("key_key.freelook.activate:")).findFirst().ifPresent(line -> {
                String name = line.substring(line.indexOf(':') + 1);
                if (name.startsWith("scancode.")) return;

                InputConstants.Key key = InputConstants.getKey(name);

                int[] code = key.getValue() == InputConstants.UNKNOWN.getValue() ? null : new int[]{key.getValue()};
                boolean mouse = key.getType() == InputConstants.Type.MOUSE;
                activationKey.setKeyCodes(mouse ? null : code);
                activationKey.setMouseBtns(mouse ? code : null);
                KeybindManager.refreshMinecraftBinding(activationKey);
            });
        } catch (IOException | RuntimeException ignored) {
        }

        save();
    }

    public void migrateDefaults() {
        if (migratedDefaults) return;
        migratedDefaults = true;

        if (pressMode == 1) pressMode = 0;

        int[] mouse = activationKey.getMouseBtns();
        if (Arrays.equals(activationKey.getKeyCodes(), new int[]{InputConstants.KEY_LALT}) && (mouse == null || mouse.length == 0)) {
            activationKey.setKeyCodes(null);
            KeybindManager.refreshMinecraftBinding(activationKey);
        }

        save();
    }

    private FreelookConfig() {
        super("freelook.json", "Freelook", Category.QOL);

        addDependency("Pitch.invert", "Pitch.enabled");
        addDependency("Pitch.lock", "Pitch.enabled");

        addDependency("Yaw.invert", "Yaw.enabled");

        addDependency("holdThreshold", "pressMode", () -> pressMode != 1 ? Property.Display.DISABLED : Property.Display.SHOWN);
    }
}
