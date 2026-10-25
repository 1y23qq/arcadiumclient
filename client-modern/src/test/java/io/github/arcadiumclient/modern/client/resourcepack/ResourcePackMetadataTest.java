// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.resourcepack;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.arcadiumclient.modern.client.resourcepack.ResourcePackCompatibility.Status.*;

class ResourcePackMetadataTest {
    @TempDir Path root;

    private static ResourcePackCompatibility.Status status(String fields, int major, int minor) {
        return ResourcePackCompatibility.parse("{\"pack\":{\"description\":\"Test\"," + fields + "}}", major, minor);
    }

    @Test void exactLegacyFormatsDoNotMatchNewerGames() {
        assertEquals(COMPATIBLE, status("\"pack_format\":1", 1, 0));
        assertEquals(TOO_OLD, status("\"pack_format\":1", 75, 0));
        assertEquals(TOO_NEW, status("\"pack_format\":2", 1, 0));
    }

    @Test void supportedFormatRangesIncludeBothEndpoints() {
        for (String range : new String[]{"[1,75]", "{\"min_inclusive\":1,\"max_inclusive\":75}"}) {
            String fields = "\"pack_format\":75,\"supported_formats\":" + range;
            assertEquals(COMPATIBLE, status(fields, 1, 0));
            assertEquals(COMPATIBLE, status(fields, 75, 0));
            assertEquals(TOO_OLD, status(fields, 76, 0));
        }
        assertEquals(COMPATIBLE, status("\"supported_formats\":1", 1, 0));
    }

    @Test void modernMajorAndMinorBoundsUseInclusiveRanges() {
        String fields = "\"min_format\":[75,2],\"max_format\":[76,3]";
        assertEquals(TOO_NEW, status(fields, 75, 1));
        assertEquals(COMPATIBLE, status(fields, 75, 2));
        assertEquals(COMPATIBLE, status(fields, 76, 3));
        assertEquals(TOO_OLD, status(fields, 76, 4));
        assertEquals(COMPATIBLE, status("\"min_format\":75,\"max_format\":75", 75, 99));
        assertEquals(COMPATIBLE, status("\"min_format\":[75],\"max_format\":[75]", 75, 99));
    }

    @Test void modernBoundsTakePrecedenceOverBackwardCompatibilityFields() {
        assertEquals(COMPATIBLE, status("\"pack_format\":1,\"supported_formats\":[1,75],\"min_format\":1,\"max_format\":76", 76, 0));
        assertEquals(TOO_OLD, status("\"pack_format\":1,\"min_format\":1,\"max_format\":75", 76, 0));
    }

    @Test void malformedAndUnknownMetadataCannotPassTheDefaultFilter() {
        for (String fields : new String[]{"\"pack_format\":-1", "\"pack_format\":1.5", "\"pack_format\":\"1\"",
                "\"pack_format\":2147483648", "\"supported_formats\":[3,1]", "\"supported_formats\":[1]",
                "\"pack_format\":1,\"min_format\":1", "\"min_format\":1,\"max_format\":[1,-1]"}) {
            assertEquals(UNKNOWN, status(fields, 1, 0), fields);
        }
        assertEquals(UNKNOWN, ResourcePackCompatibility.parse("invalid", 1, 0));
        assertEquals(UNKNOWN, ResourcePackCompatibility.parse("{}", 1, 0));
        assertEquals(UNKNOWN, ResourcePackCompatibility.read(root.resolve("missing.zip"), 1, 0));
    }

    @Test void unversionedLegacyMetadataRemainsUsableWithoutClaimingModernSupport() {
        String old = "{\"pack\":{\"description\":\"Legacy textures\"}}";
        assertEquals(COMPATIBLE, ResourcePackCompatibility.parse(old, 1, 0));
        assertEquals(UNKNOWN, ResourcePackCompatibility.parse(old, 75, 0));
    }

    @Test void metadataLoadsFromNestedFoldersAndZipPacks() throws Exception {
        Path folder = root.resolve("PVP/Favorites/Folder Pack");
        Files.createDirectories(folder);
        String metadata = "{\"pack\":{\"description\":\"Nested\",\"pack_format\":1,\"supported_formats\":[1,75]}}";
        Files.writeString(folder.resolve("pack.mcmeta"), metadata);
        Path zip = zip("PVP/Favorites/Zip Pack.zip", "pack.mcmeta", metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(COMPATIBLE, ResourcePackCompatibility.read(folder, 1, 0));
        assertEquals(COMPATIBLE, ResourcePackCompatibility.read(zip, 75, 0));
        assertEquals(TOO_OLD, ResourcePackCompatibility.read(zip, 76, 0));
    }

    @Test void oversizedMetadataIsRejectedBeforeParsing() throws Exception {
        Path folder = root.resolve("large");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("pack.mcmeta"), " ".repeat(1024 * 1024 + 1));
        assertEquals(UNKNOWN, ResourcePackCompatibility.read(folder, 1, 0));
    }

    @Test void largePackIconsLoadForFoldersAndZipsWithoutLosingTransparency() throws Exception {
        BufferedImage image = new BufferedImage(1024, 512, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(20, 160, 255, 128));
        graphics.fillRect(0, 0, 1024, 512);
        graphics.dispose();
        byte[] original = png(image);
        Path folder = root.resolve("Icons/Favorite/Folder");
        Files.createDirectories(folder);
        Files.write(folder.resolve("pack.png"), original);
        Path zip = zip("Icons/Favorite/Pack.zip", "pack.png", original);
        for (Path path : new Path[]{folder, zip}) {
            byte[] encoded = ResourcePackImages.read(path);
            assertNotNull(encoded);
            BufferedImage preview = ImageIO.read(new ByteArrayInputStream(encoded));
            assertEquals(128, preview.getWidth());
            assertEquals(64, preview.getHeight());
            assertEquals(128, preview.getRGB(64, 32) >>> 24);
        }
    }

    @Test void smallNativeIconsRetainDimensionsAndExternalImageOwnership() throws Exception {
        BufferedImage nativeImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB) {
            @Override public void flush() { fail("The native repository owns this image"); }
        };
        nativeImage.setRGB(32, 32, 0xffff1020);
        byte[] encoded = ResourcePackImages.encode(nativeImage);
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(encoded));
        assertEquals(64, preview.getWidth());
        assertEquals(0xffff1020, preview.getRGB(32, 32));
        assertEquals(0xffff1020, nativeImage.getRGB(32, 32));
    }

    @Test void invalidMissingAndOversizedIconsUseTheFallback() throws Exception {
        assertNull(ResourcePackImages.normalize(new byte[]{1, 2, 3}));
        assertNull(ResourcePackImages.normalize(new byte[16 * 1024 * 1024 + 1]));
        assertNull(ResourcePackImages.read(root.resolve("missing")));
        Path zip = zip("no-icon.zip", "pack.mcmeta", new byte[]{'{', '}'});
        assertNull(ResourcePackImages.read(zip));
    }

    private Path zip(String name, String entry, byte[] bytes) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry(entry));
            output.write(bytes);
            output.closeEntry();
        }
        return path;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
