// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.window;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.system.Platform;
import org.lwjgl.system.Pointer;

public final class GameWindowIcon {
    private static boolean reportedFailure;

    private GameWindowIcon() {}

    public static ByteBuffer[] buffers() {
        return Images.BUFFERS;
    }

    public static boolean applySdl(long window) {
        if (Platform.get() != Platform.WINDOWS || Pointer.POINTER_SIZE != Long.BYTES || window <= 0) return false;
        try {
            return buffers() != null && WindowsGameIcons.apply(window);
        } catch (RuntimeException | LinkageError exception) {
            report(exception);
            return false;
        }
    }

    public static void releaseSdl(long window) {
        if (Platform.get() == Platform.WINDOWS && window > 0) WindowsGameIcons.release(window);
    }

    private static ByteBuffer[] load() {
        try (var input = GameWindowIcon.class.getResourceAsStream("/assets/arcadiumclient/icon.png")) {
            if (input == null) throw new IOException("Window icon resource is missing");
            BufferedImage original = ImageIO.read(input);
            if (original == null) throw new IOException("Window icon resource is invalid");
            int[] sizes = {256, 128, 64, 48, 32, 16};
            ByteBuffer[] images = new ByteBuffer[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                int size = sizes[i];
                BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = icon.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(original, 0, 0, size, size, null);
                } finally {
                    graphics.dispose();
                }
                ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4);
                for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                    int pixel = icon.getRGB(x, y);
                    pixels.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) (pixel >> 24));
                }
                images[i] = pixels.flip();
            }
            return images;
        } catch (IOException | RuntimeException exception) {
            report(exception);
            return null;
        }
    }

    private static void report(Throwable exception) {
        if (reportedFailure) return;
        reportedFailure = true;
        System.err.println("Arcadium window icon could not be applied: " + exception.getClass().getSimpleName());
    }

    private static final class Images {
        private static final ByteBuffer[] BUFFERS = load();
    }
}
