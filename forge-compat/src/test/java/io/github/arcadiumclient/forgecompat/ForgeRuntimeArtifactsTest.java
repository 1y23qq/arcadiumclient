package io.github.arcadiumclient.forgecompat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ForgeRuntimeArtifactsTest {
    @TempDir Path directory;

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void correctCacheDoesNotContactNetwork() throws Exception {
        byte[] expected = "valid local fixture".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("forge.jar");
        Files.write(archive, expected);
        assertEquals(archive, ForgeRuntimeArtifacts.download(URI.create("http://127.0.0.1:1/unreachable"), hash(expected), archive));
        assertArrayEquals(expected, Files.readAllBytes(archive));
    }

    @Test void downloadIsVerifiedAndReusedOffline() throws Exception {
        byte[] expected = "small public fixture".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forge", request -> {
            requests.incrementAndGet();
            request.sendResponseHeaders(200, expected.length);
            try (var output = request.getResponseBody()) { output.write(expected); }
        });
        server.start();
        Path archive = directory.resolve("cached.jar");
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/forge");
        try { ForgeRuntimeArtifacts.download(endpoint, hash(expected), archive); }
        finally { server.stop(0); }
        assertArrayEquals(expected, Files.readAllBytes(archive));
        assertEquals(archive, ForgeRuntimeArtifacts.download(endpoint, hash(expected), archive));
        assertEquals(1, requests.get());
    }

    @Test void invalidDownloadDoesNotReplaceExistingCache() throws Exception {
        byte[] original = "old invalid cache".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("cached.jar");
        Files.write(archive, original);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forge", request -> {
            byte[] invalid = "wrong content".getBytes(StandardCharsets.UTF_8);
            request.sendResponseHeaders(200, invalid.length);
            try (var output = request.getResponseBody()) { output.write(invalid); }
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/forge");
            IOException error = assertThrows(IOException.class, () -> ForgeRuntimeArtifacts.download(endpoint, hash("expected content".getBytes(StandardCharsets.UTF_8)), archive));
            assertTrue(error.getMessage().contains("SHA-256"));
            assertArrayEquals(original, Files.readAllBytes(archive));
            try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        } finally { server.stop(0); }
    }

    @Test void downloadErrorsDoNotCreateExecutableArchive() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forge", request -> { request.sendResponseHeaders(503, -1); request.close(); });
        server.start();
        Path archive = directory.resolve("cached.jar");
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/forge");
            assertThrows(IOException.class, () -> ForgeRuntimeArtifacts.download(endpoint, "0".repeat(64), archive));
            assertFalse(Files.exists(archive));
        } finally { server.stop(0); }
    }

    @Test void invalidExpectedHashCannotDisableVerification() {
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeArtifacts.download(
            URI.create("http://127.0.0.1:1"), "", directory.resolve("cached.jar")));
    }
}
