// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public final class LegacyCrosshairTweaksConfig extends Config {
    @Switch(title = "Use Legacy Crosshair Tweaks", description = "Use the shared visibility, blending and opacity settings below. Turn this off to use the native PolyCrosshair profile settings.")
    public static boolean legacySettings = Files.isRegularFile(FabricLoader.getInstance().getConfigDir().resolve("crosshairtweaks.json"));
    @Switch(title = "Hide Crosshair") public static boolean disableCrosshair = false;
    @Switch(title = "Hide in Containers") public static boolean hideCrosshairInContainers = true;
    @Switch(title = "Hide in Third Person") public static boolean hideCrosshairInPerspective = false;
    @Switch(title = "Always Show in Spectator") public static boolean showCrosshairInSpectator = false;
    @Switch(title = "Disable First Person Blending") public static boolean removeCrosshairBlendingFirstPerson = false;
    @Switch(title = "Disable Third Person Blending") public static boolean removeCrosshairBlendingThirdPerson = false;
    @Slider(title = "First Person Opacity", min = 0, max = 100, step = 1) public static float crosshairOpacityFirstPerson = 100;
    @Slider(title = "Third Person Opacity", min = 0, max = 100, step = 1) public static float crosshairOpacityThirdPerson = 100;
    public static final LegacyCrosshairTweaksConfig INSTANCE = new LegacyCrosshairTweaksConfig();
    private LegacyCrosshairTweaksConfig() { super("crosshairtweaks.json", "Crosshair Tweaks", Category.VISUALS); }
}
