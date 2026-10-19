package io.github.arcadiumclient.forgecompat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.loader.impl.launch.FabricLauncher;

final class ForgeRuntimeLibraries {
    private ForgeRuntimeLibraries() { }

    static List<Path> install(FabricLauncher launcher, Path cache) throws IOException {
        String root = "/META-INF/arcadium/libraries/";
        String index;
        try (var input = ForgeRuntimeLibraries.class.getResourceAsStream(root + "index.txt")) {
            if (input == null) throw new IOException("The Forge runtime library index is missing.");
            index = new String(input.readNBytes(64 * 1024), StandardCharsets.UTF_8);
        }
        return install(launcher, cache, index);
    }

    static List<Path> install(FabricLauncher launcher, Path cache, String index) throws IOException {
        String root = "/META-INF/arcadium/libraries/";
        List<Path> paths = new ArrayList<>();
        var names = new HashSet<String>();
        for (String line : index.lines().filter(value -> !value.isBlank()).toList()) {
            String[] entry = line.split(" ", 3);
            if (entry.length < 2 || !entry[0].matches("[0-9a-f]{64}")
                    || !entry[1].matches("[a-zA-Z0-9._-]+\\.jar") || !names.add(entry[1])) {
                throw new IOException("The Forge runtime library index is invalid.");
            }
            Path destination = cache.resolve("libraries").resolve(entry[1]);
            Path jar = entry.length == 3
                    ? ForgeRuntimeArtifacts.download(source(entry[2]), entry[0], destination)
                    : ArcadiumLaunch.resource(root + entry[1], destination);
            if (!ForgeModScanner.sha256(jar).equals(entry[0])) {
                throw new IOException("The Forge runtime library failed verification: " + entry[1]);
            }
            paths.add(jar);
        }
        if (paths.isEmpty()) throw new IOException("The Forge runtime library index is empty.");
        for (Path path : paths) launcher.addToClassPath(path);
        return List.copyOf(paths);
    }

    private static URI source(String value) throws IOException {
        try {
            URI source = URI.create(value);
            boolean secure = "https".equals(source.getScheme()) && source.getHost() != null;
            boolean loopback = "http".equals(source.getScheme())
                    && ("127.0.0.1".equals(source.getHost()) || "localhost".equals(source.getHost()));
            if ((secure || loopback) && source.getUserInfo() == null && source.getFragment() == null) return source;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("The Forge runtime library download address is invalid.", invalid);
        }
        throw new IOException("The Forge runtime library download address must use HTTPS.");
    }
}
