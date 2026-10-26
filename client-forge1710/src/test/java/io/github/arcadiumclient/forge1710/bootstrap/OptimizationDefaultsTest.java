package io.github.arcadiumclient.forge1710.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class OptimizationDefaultsTest {
    @TempDir Path directory;

    @Test void initializesOnlyMissingFilesAndPreservesCustomSettings() throws Exception {
        Path config = directory.resolve("config");
        Files.createDirectories(config);
        Path custom = config.resolve("hodgepodge.cfg");
        String original = "speedups {\n    B:fastItemEntityPhysics=false\n}\n";
        Files.writeString(custom, original);
        OptimizationDefaults.install(directory);
        assertEquals(original, Files.readString(custom));
        Path core = config.resolve("coretweaks.cfg");
        assertTrue(Files.readString(core).contains("transformerCacheMode=LITE"));
        String edited = Files.readString(core).replace("transformerCacheMode=LITE", "transformerCacheMode=FULL");
        Files.writeString(core, edited);
        OptimizationDefaults.install(directory);
        assertEquals(edited, Files.readString(core));
        assertTrue(Files.readString(config.resolve("archaicfix.cfg")).contains("enableHitThroughGrassFix=false"));
    }

    @Test void keepsLightingAndMemoryMixinsWhileRemovingUnconditionalGameplayChanges() {
        List<String> input = List.of("common.core.MixinAxisAlignedBB", "client.core.MixinEntityRenderer",
            "client.core.MixinMinecraft", "client.lighting.MixinMinecraft", "client.core.MixinThreadDownloadImageData",
            "common.lighting.MixinChunk", "common.core.MixinLongHashMap");
        assertEquals(input.subList(3, input.size()), OptimizationMixinPolicy.archaicMixins(input));
        assertEquals(7, input.size());
    }

    @Test void rejectsAnUnexpectedArchaicMixinContract() {
        assertThrows(IllegalStateException.class,
            () -> OptimizationMixinPolicy.archaicMixins(List.of("common.lighting.MixinChunk")));
    }

    @Test void verifiesHostedRuntimeBytesRatherThanItsFilename() throws Exception {
        Path archive = directory.resolve("sponge-mixin.jar");
        byte[] expected = "verified runtime".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(expected));
        Files.writeString(archive, "different runtime");
        assertFalse(HostedMixinRuntime.matches(archive, digest));
        Files.write(archive, expected);
        assertTrue(HostedMixinRuntime.matches(archive, digest));
        assertFalse(HostedMixinRuntime.matches(archive, "invalid digest"));
    }

    @Test void preservesUnknownAndAdditionalErrorsEvenWithAnAcceptedHostWarning() {
        String warning = HostedMixinRuntime.warning("+unimixins-all.jar", "sponge-mixin.jar");
        assertTrue(HostedMixinRuntime.filter(List.of(warning), warning).isEmpty());
        List<String> duplicate = List.of(warning, "Detected incompatible jars: [another-mixin.jar]");
        assertSame(duplicate, HostedMixinRuntime.filter(duplicate, warning));
        List<String> unknown = List.of("Unknown Mixin engine");
        assertSame(unknown, HostedMixinRuntime.filter(unknown, warning));
        assertSame(unknown, HostedMixinRuntime.filter(unknown, null));
        assertEquals(List.of(warning), HostedMixinRuntime.filter(List.of(warning), null));
    }
}
