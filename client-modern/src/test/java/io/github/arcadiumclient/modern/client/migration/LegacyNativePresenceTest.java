// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.backend.impl.FileBackend;
import org.polyfrost.oneconfig.api.config.v1.serialize.impl.NightConfigSerializer;
import static org.junit.jupiter.api.Assertions.*;

class LegacyNativePresenceTest {
    @Test void backendCreationDoesNotTurnNativeOnlySettingsIntoALegacyImport(@TempDir Path folder) {
        for (String id : new String[]{"blur.json", "fullbright.json"}) {
            var config = new FixtureConfig(folder.resolve(id));
            var backend = new FileBackend(folder, NightConfigSerializer.JSON);
            Tree prepared = config.prepare();
            assertFalse(config.hadLegacyDocument());
            Tree registered = backend.register(prepared).get();
            assertTrue(Files.isRegularFile(folder.resolve(id)));
            assertFalse(config.hadLegacyDocument());
            config.enabled = false;
            assertTrue(backend.save(registered));
            var nextConfig = new FixtureConfig(folder.resolve(id));
            Tree next = new FileBackend(folder, NightConfigSerializer.JSON).register(nextConfig.prepare()).get();
            assertTrue(nextConfig.hadLegacyDocument());
            assertFalse((Boolean) next.getProp("enabled").get());
        }
    }
    public static final class FixtureConfig extends LegacyNativeConfig {
        @Switch(title = "Enabled") public boolean enabled = true;
        private final Path path;
        FixtureConfig(Path path) { super(path.getFileName().toString(), "Native settings", Category.OTHER); this.path = path; }
        @Override protected void addToInitQueue() { }
        @Override protected Path documentPath() { return path; }
        Tree prepare() { return makeTree(); }
    }
}
