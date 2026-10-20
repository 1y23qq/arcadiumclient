// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.window;

import io.github.arcadiumclient.capture.SdlCaptureCompatibility;
import java.io.IOException;
import java.util.Properties;

public final class GameBranding {
    private static final String MINECRAFT_VERSION = loadVersion();
    private static final String WINDOW_TITLE = "Arcadium Client " + MINECRAFT_VERSION
            + (SdlCaptureCompatibility.isEnabled() ? " (Minecraft)" : "");

    private GameBranding() {}

    public static String minecraftVersion() {
        return MINECRAFT_VERSION;
    }

    public static String windowTitle() {
        return WINDOW_TITLE;
    }

    private static String loadVersion() {
        try (var input = GameBranding.class.getResourceAsStream("/arcadium-game.properties")) {
            if (input == null) throw new IOException("Game branding version is missing");
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("minecraftVersion");
            if (!"1.7.10".equals(version) && !"1.8.9".equals(version))
                throw new IOException("Unsupported branding game version");
            return version;
        } catch (IOException exception) {
            throw new IllegalStateException("Arcadium game branding could not be loaded", exception);
        }
    }
}
