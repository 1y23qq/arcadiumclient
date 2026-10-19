// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.forgecompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;
import org.glavo.pack200.Pack200;
import org.tukaani.xz.LZMAInputStream;

final class ForgeRuntimeArtifacts {
    static final URI OFFICIAL = URI.create("https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
    static final String SHA256 = "596512ad5f12f95d8a3170321543d4455d23b8fe649c68580c5f828fe74f6668";
    private static final long DOWNLOAD_LIMIT = 16L * 1024 * 1024;
    private static final long MAPPINGS_LIMIT = 32L * 1024 * 1024;
    private static final String GENERATION = "1:" + SHA256;

    record Prepared(Path original, Path mappings, Path patches) { }

    private ForgeRuntimeArtifacts() { }

    static Prepared prepare(Path cache) throws IOException {
        Files.createDirectories(cache);
        try (FileChannel channel = FileChannel.open(cache.resolve("runtime-download.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            Path original = download(OFFICIAL, SHA256, cache.resolve("forge-1.8.9-11.15.1.2318-universal.jar"));
            Path mappings = cache.resolve("forge-local.srg");
            Path patches = cache.resolve("forge-local-game-patches.jar");
            Path receipt = cache.resolve("forge-local-artifacts.properties");
            Properties hashes = new Properties();
            if (Files.isRegularFile(receipt)) {
                try (InputStream input = Files.newInputStream(receipt)) { hashes.load(input); }
            }
            if (GENERATION.equals(hashes.getProperty("generation"))
                    && matches(mappings, hashes.getProperty("mappings")) && matches(patches, hashes.getProperty("patches"))) {
                return new Prepared(original, mappings, patches);
            }
            Path mappingTemp = Files.createTempFile(cache, "forge-map-", ".tmp");
            Path patchesTemp = Files.createTempFile(cache, "forge-patches-", ".tmp");
            Path receiptTemp = Files.createTempFile(cache, "forge-receipt-", ".tmp");
            try {
                try (ZipFile archive = new ZipFile(original.toFile())) {
                    var mapEntry = archive.getEntry("deobfuscation_data-1.8.9.lzma");
                    var patchEntry = archive.getEntry("binpatches.pack.lzma");
                    if (mapEntry == null || patchEntry == null) throw new IOException("The official Forge archive is incomplete.");
                    try (InputStream input = new LZMAInputStream(archive.getInputStream(mapEntry));
                         OutputStream output = Files.newOutputStream(mappingTemp)) {
                        copyBounded(input, output, MAPPINGS_LIMIT);
                    }
                    try (InputStream input = new LZMAInputStream(archive.getInputStream(patchEntry));
                         JarOutputStream output = new JarOutputStream(Files.newOutputStream(patchesTemp))) {
                        Pack200.newUnpacker().unpack(input, output);
                    }
                }
                hashes.clear();
                hashes.setProperty("generation", GENERATION);
                hashes.setProperty("mappings", digest(mappingTemp));
                hashes.setProperty("patches", digest(patchesTemp));
                try (OutputStream output = Files.newOutputStream(receiptTemp)) { hashes.store(output, "Generated locally from the official Forge archive"); }
                Files.move(mappingTemp, mappings, StandardCopyOption.REPLACE_EXISTING);
                Files.move(patchesTemp, patches, StandardCopyOption.REPLACE_EXISTING);
                Files.move(receiptTemp, receipt, StandardCopyOption.REPLACE_EXISTING);
                return new Prepared(original, mappings, patches);
            } finally {
                Files.deleteIfExists(mappingTemp);
                Files.deleteIfExists(patchesTemp);
                Files.deleteIfExists(receiptTemp);
            }
        }
    }

    static Path download(URI source, String expectedHash, Path destination) throws IOException {
        if (!expectedHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("A SHA-256 digest is required.");
        if (matches(destination, expectedHash)) return destination;
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), "forge-download-", ".tmp");
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest request = HttpRequest.newBuilder(source).timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "ArcadiumClient/ForgeCompatibility").GET().build();
            HttpResponse<InputStream> response;
            try { response = client.send(request, HttpResponse.BodyHandlers.ofInputStream()); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("The Forge support download was interrupted.", interrupted);
            }
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temporary)) {
                if (response.statusCode() != 200) throw new IOException("Could not download Forge support (HTTP " + response.statusCode() + "). Check your connection and try again.");
                if (response.headers().firstValueAsLong("Content-Length").orElse(0) > DOWNLOAD_LIMIT)
                    throw new IOException("The Forge support download exceeds its size limit.");
                copyBounded(input, output, DOWNLOAD_LIMIT);
            }
            if (!matches(temporary, expectedHash)) throw new IOException("The Forge support download failed SHA-256 verification.");
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void copyBounded(InputStream input, OutputStream output, long maximum) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        for (int read; (read = input.read(buffer)) != -1;) {
            total += read;
            if (total > maximum) throw new IOException("The Forge support data exceeds its size limit.");
            output.write(buffer, 0, read);
        }
    }

    private static boolean matches(Path path, String expected) throws IOException {
        return expected != null && Files.isRegularFile(path) && expected.equals(digest(path));
    }

    private static String digest(Path path) throws IOException {
        MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) hash.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(hash.digest());
    }
}
