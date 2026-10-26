// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.bootstrap;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class OptimizationDefaults {
    private static final List<String> FILES = List.of("hodgepodge.cfg", "hodgepodgeEarly.properties", "coretweaks.cfg", "archaicfix.cfg");

    private OptimizationDefaults() { }

    public static void install() {
        Path runtime = Path.of(System.getProperty("arcadium.runtimeGameDir", System.getProperty("user.dir")));
        try { install(runtime); }
        catch (IOException error) { throw new IllegalStateException("Could not initialize Arcadium optimization settings", error); }
    }

    static void install(Path runtime) throws IOException {
        Path config = runtime.resolve("config");
        Files.createDirectories(config);
        for (String name : FILES) {
            Path target = config.resolve(name);
            if (Files.exists(target)) continue;
            try (var input = OptimizationDefaults.class.getResourceAsStream("/arcadium-defaults/" + name)) {
                if (input == null) throw new IOException("Missing optimization defaults: " + name);
                try { Files.copy(input, target); }
                catch (FileAlreadyExistsException ignored) { }
            }
        }
    }
}
