package io.github.arcadiumclient.forgecompat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;

public final class ForgeTestFixtures {
    private ForgeTestFixtures() {}

    public static Path nativeDirectory() {
        return Path.of(System.getProperty("arcadium.test.nativeMods", "../build/forge-compat/native-test-fixtures")).toAbsolutePath().normalize();
    }

    public static Path nativeJar(String filename) {
        Path path = nativeDirectory().resolve(filename);
        Assumptions.assumeTrue(Files.isRegularFile(path), "Pinned native test fixture is unavailable: " + filename + ". Run prepareNativeTestFixtures first.");
        return path;
    }

    public static Path optionalGuestDirectory() {
        Path path = Path.of(System.getProperty("arcadium.test.guestMods", "../forge-mod-test")).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(path), "Optional user-supplied Forge fixtures are unavailable. Set ARCADIUM_TEST_FORGE_MODS_DIR or -ParcadiumTestMods.");
        return path;
    }

    public static Path optionalGuest(String filename) {
        String configured = System.getProperty("arcadium.test.guestMods");
        Path path = Path.of(configured == null ? "../forge-mod-test" : configured).resolve(filename).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            Path dependency = Path.of("../build/forge-compat/dependencies").resolve(filename).toAbsolutePath().normalize();
            if (Files.isRegularFile(dependency)) path = dependency;
        }
        Assumptions.assumeTrue(Files.isRegularFile(path), "Optional user-supplied Forge fixture is unavailable: " + filename + ". Set ARCADIUM_TEST_FORGE_MODS_DIR or -ParcadiumTestMods.");
        return path;
    }
}
