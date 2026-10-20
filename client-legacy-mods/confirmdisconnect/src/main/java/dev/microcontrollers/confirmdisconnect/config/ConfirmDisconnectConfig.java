package dev.microcontrollers.confirmdisconnect.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class ConfirmDisconnectConfig extends Config {
    public static final ConfirmDisconnectConfig INSTANCE = new ConfirmDisconnectConfig();

    @Switch(
            title = "confirm-disconnect.confirm-enabled",
            description = "confirm-disconnect.confirm-enabled.description"
    )
    public static boolean confirmEnabled = true;

    @Switch(
            title = "confirm-disconnect.use-discrete-confirmation",
            description = "confirm-disconnect.use-discrete-confirmation.description"
    )
    public static boolean useDiscreteConfirmation = false;

    @Switch(
            title = "confirm-disconnect.enable-in-singleplayer",
            description = "confirm-disconnect.enable-in-singleplayer.description"
    )
    public static boolean enableInSingleplayer = true;

    @Switch(
            title = "confirm-disconnect.enable-in-multiplayer",
            description = "confirm-disconnect.enable-in-multiplayer.description"
    )
    public static boolean enableInMultiplayer = true;

    @Slider(
            title = "confirm-disconnect.delay",
            description = "confirm-disconnect.delay.description",
            max = 10F,
            step = 0.1F
    )
    public static double confirmDelay = 0.0D;

    @Switch(
            title = "confirm-disconnect.confirm-on-left",
            description = "confirm-disconnect.confirm-on-left.description"
    )
    public static boolean confirmOnLeft = false;

    public ConfirmDisconnectConfig() {
        super("confirmdisconnect.json", "/assets/confirmdisconnect/icon.png", "Confirm Disconnect", Category.QOL);
        save();
    }
}

