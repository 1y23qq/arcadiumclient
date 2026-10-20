// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import java.awt.image.BufferedImage;

public final class LegacySkinImages {
    private LegacySkinImages() {}

    public static BufferedImage normalize(BufferedImage source) {
        if (source == null || source.getWidth() != 64 || (source.getHeight() != 32 && source.getHeight() != 64)) return null;
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 64, source.getHeight(), source.getRGB(0, 0, 64, source.getHeight(), null, 0, 64), 0, 64);
        if (source.getHeight() == 32) {
            mirrorLimb(image, 0, 16, 16, 48);
            mirrorLimb(image, 40, 16, 32, 48);
            boolean transparentHat = false;
            for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) {
                if ((image.getRGB(x, y) >>> 24) < 128) transparentHat = true;
            }
            if (!transparentHat) for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) {
                image.setRGB(x, y, image.getRGB(x, y) & 0xFFFFFF);
            }
        }
        opaque(image, 0, 0, 32, 16);
        opaque(image, 0, 16, 64, 32);
        opaque(image, 16, 48, 48, 64);
        return image;
    }

    private static void mirrorLimb(BufferedImage image, int fromU, int fromV, int toU, int toV) {
        mirror(image, fromU + 4, fromV, toU + 4, toV, 4, 4);
        mirror(image, fromU + 8, fromV, toU + 8, toV, 4, 4);
        mirror(image, fromU + 4, fromV + 4, toU + 4, toV + 4, 4, 12);
        mirror(image, fromU + 12, fromV + 4, toU + 12, toV + 4, 4, 12);
        mirror(image, fromU, fromV + 4, toU + 8, toV + 4, 4, 12);
        mirror(image, fromU + 8, fromV + 4, toU, toV + 4, 4, 12);
    }

    private static void mirror(BufferedImage image, int fromU, int fromV, int toU, int toV, int width, int height) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            image.setRGB(toU + x, toV + y, image.getRGB(fromU + width - 1 - x, fromV + y));
        }
    }

    private static void opaque(BufferedImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) image.setRGB(x, y, image.getRGB(x, y) | 0xFF000000);
    }
}
