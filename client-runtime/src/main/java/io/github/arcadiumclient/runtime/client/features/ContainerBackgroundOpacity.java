// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.lang.reflect.Field;

public final class ContainerBackgroundOpacity {
    private static final Field OPACITY = findOpacity();

    private ContainerBackgroundOpacity() {}

    private static Field findOpacity() {
        try {
            return Class.forName("dev.microcontrollers.betterscreens.config.BetterScreensConfig", false,
                ContainerBackgroundOpacity.class.getClassLoader()).getField("containerBackgroundOpacity");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean available() { return OPACITY != null; }

    public static float percent() {
        if (OPACITY != null) {
            try {
                return BackgroundOpacity.percent(OPACITY.getFloat(null), 81.56863f);
            } catch (IllegalAccessException | LinkageError ignored) {}
        }
        return 81.56863f;
    }
}
