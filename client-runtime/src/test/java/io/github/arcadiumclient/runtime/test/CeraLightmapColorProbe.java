// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test;

import java.lang.reflect.Method;

public final class CeraLightmapColorProbe {
    public static int verify() throws ReflectiveOperationException {
        Method channel = Class.forName("dev.rdh.cera.modules.colors.LightMaps")
                .getDeclaredMethod("channel", float.class, float.class, boolean.class);
        channel.setAccessible(true);
        int checked = 0;
        for (float gamma : new float[] {0, .25F, .5F, 1, 2, 4, 8, 16, 100}) {
            for (int input = 0; input <= 255; input++) {
                float value = input / 255.0F;
                float adjusted = value;
                if (gamma > 0.0001F) {
                    float inverse = 1 - value;
                    float bright = 1 - inverse * inverse * inverse * inverse;
                    adjusted = gamma * bright + (1 - gamma) * value;
                }
                int expected = (int) (Math.max(0, Math.min(1, adjusted)) * 255);
                int actual = (int) channel.invoke(null, value, gamma, gamma > 0.0001F);
                if (actual != expected) {
                    throw new AssertionError("Lightmap color overflow at gamma=" + gamma
                            + ", input=" + input + ": expected=" + expected + ", actual=" + actual);
                }
                checked++;
            }
        }
        return checked;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        System.out.println("Verified " + verify() + " custom lightmap color cases.");
    }
}
