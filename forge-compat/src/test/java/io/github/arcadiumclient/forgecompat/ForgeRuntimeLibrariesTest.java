package io.github.arcadiumclient.forgecompat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ForgeRuntimeLibrariesTest {
    @TempDir Path directory;

    @Test void productionKeepsOnlyTroveEmbeddedAndPinsTheOriginalExternalLibraries() throws Exception {
        Path production = Path.of(System.getProperty("arcadium.test.productionJar"));
        try (ZipFile archive = new ZipFile(production.toFile())) {
            String root = "META-INF/arcadium/libraries/";
            Set<String> embedded = archive.stream().map(entry -> entry.getName())
                    .filter(name -> name.startsWith(root) && name.endsWith(".jar"))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of(root + "trove4j-3.0.3.jar"), embedded);
            assertTrue(archive.stream().noneMatch(entry -> entry.getName().startsWith("LZMA/")
                    || entry.getName().startsWith("javax/vecmath/")));
            String index = new String(archive.getInputStream(archive.getEntry(root + "index.txt")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(index.contains("0c3085cb93c3ef95d731c274b6f2dd195aa0405fe9328d69cab7f5e899d5e124 lzma-0.0.1.jar https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar"));
            assertTrue(index.contains("09adf7955baa6bddf57a4bdbf9a91cf690261314969b59a18085ece1e05eccf9 vecmath-1.5.2.jar https://libraries.minecraft.net/java3d/vecmath/1.5.2/vecmath-1.5.2.jar"));
            byte[] trove = archive.getInputStream(archive.getEntry(root + "trove4j-3.0.3.jar")).readAllBytes();
            assertEquals("3c8616203d61a12a7e3487e8b34f3c198c2b5ba9e90da0c7ea32d99cd4958012", hash(trove));
            for (String notice : List.of("LICENSE.txt", "AUTHORS.txt", "README-license.txt")) {
                assertNotNull(archive.getEntry("META-INF/licenses/forge-libraries/trove4j-3.0.3/original-release-notices/" + notice));
            }
        }
    }

    @Test void externalLibrariesAreVerifiedBeforeBindingAndReusedOffline() throws Exception {
        byte[] first = "synthetic first library".getBytes(StandardCharsets.UTF_8);
        byte[] second = "synthetic second library".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/first", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, first.length);
            try (var output = exchange.getResponseBody()) { output.write(first); }
        });
        server.createContext("/second", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, second.length);
            try (var output = exchange.getResponseBody()) { output.write(second); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String index = hash(first) + " lzma-0.0.1.jar " + base + "/first\n"
                + hash(second) + " vecmath-1.5.2.jar " + base + "/second\n";
        List<Path> bound = new ArrayList<>();
        List<Path> paths;
        try { paths = ForgeRuntimeLibraries.install(launcher(bound), directory, index); }
        finally { server.stop(0); }
        assertEquals(paths, bound);
        assertEquals(2, requests.get());
        assertArrayEquals(first, Files.readAllBytes(paths.get(0)));
        assertArrayEquals(second, Files.readAllBytes(paths.get(1)));
        bound.clear();
        assertEquals(paths, ForgeRuntimeLibraries.install(launcher(bound), directory, index));
        assertEquals(paths, bound);
        assertEquals(2, requests.get());
    }

    @Test void checksumFailurePreservesCacheAndDoesNotBindAnyLibrary() throws Exception {
        Path existing = directory.resolve("libraries/lzma-0.0.1.jar");
        Files.createDirectories(existing.getParent());
        byte[] previous = "existing local cache".getBytes(StandardCharsets.UTF_8);
        Files.write(existing, previous);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invalid", exchange -> {
            byte[] invalid = "wrong download".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, invalid.length);
            try (var output = exchange.getResponseBody()) { output.write(invalid); }
        });
        server.start();
        List<Path> bound = new ArrayList<>();
        String index = "0".repeat(64) + " lzma-0.0.1.jar http://127.0.0.1:"
                + server.getAddress().getPort() + "/invalid";
        try {
            assertThrows(IOException.class, () -> ForgeRuntimeLibraries.install(launcher(bound), directory, index));
            assertArrayEquals(previous, Files.readAllBytes(existing));
            assertTrue(bound.isEmpty());
            try (var files = Files.list(existing.getParent())) { assertEquals(1, files.count()); }
        } finally { server.stop(0); }
    }

    @Test void malformedUnpinnedOrUnsafeIndexEntriesAreRejected() throws Exception {
        for (String index : List.of("", "no-hash library.jar", "0".repeat(64) + " ../escape.jar",
                "0".repeat(64) + " library.jar file:///outside.jar",
                "0".repeat(64) + " library.jar http://example.invalid/library.jar")) {
            List<Path> bound = new ArrayList<>();
            assertThrows(IOException.class, () -> ForgeRuntimeLibraries.install(launcher(bound), directory, index));
            assertTrue(bound.isEmpty());
        }
    }

    private static FabricLauncher launcher(List<Path> bound) {
        return (FabricLauncher) Proxy.newProxyInstance(FabricLauncher.class.getClassLoader(), new Class<?>[]{FabricLauncher.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addToClassPath")) {
                        bound.add((Path) arguments[0]);
                        return null;
                    }
                    throw new AssertionError(method.getName());
                });
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
