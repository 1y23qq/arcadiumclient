// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.resourcepack;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

public final class ResourcePackImages {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int PREVIEW_SIZE = 128;
    private ResourcePackImages() { }

    public static byte[] read(Path pack) {
        try {
            if (Files.isDirectory(pack)) {
                try (InputStream input = Files.newInputStream(pack.resolve("pack.png"))) { return read(input); }
            }
            try (ZipFile archive = new ZipFile(pack.toFile())) {
                var entry = archive.getEntry("pack.png");
                if (entry == null || entry.isDirectory()) return null;
                try (InputStream input = archive.getInputStream(entry)) { return read(input); }
            }
        } catch (IOException | RuntimeException failure) { return null; }
    }
    public static byte[] read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        return bytes.length > MAX_BYTES ? null : normalize(bytes);
    }
    public static byte[] normalize(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) return null;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 16384 || height > 16384 || (long) width * height > 64L * 1024 * 1024) return null;
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / PREVIEW_SIZE);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                var decoded = reader.read(0, parameters);
                try { return encode(decoded); } finally { decoded.flush(); }
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException failure) { return null; }
    }
    public static byte[] encode(BufferedImage source) throws IOException {
        double scale = Math.min(1.0, PREVIEW_SIZE / (double) Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
            return output.toByteArray();
        } finally { image.flush(); }
    }
}
