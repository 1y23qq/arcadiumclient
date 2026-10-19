package io.github.arcadiumclient.forgecompat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class ForgeModScannerTest {
    @TempDir Path temporary;
    private final ForgeModScanner scanner = new ForgeModScanner();

    @Test void identifiesRenamedModsFromBytecodeInsteadOfFileNames() throws Exception {
        Path path = jar("renamed.jar", Map.of("example/Main.class", modClass("sample", "Example", "1.2")));
        var result = scanner.inspect(path);
        assertEquals("sample", result.mods().get(0).id());
        assertEquals("example.Main", result.mods().get(0).entrypoint());
        assertEquals(64, result.sha256().length());
    }

    @Test void nativeFabricModsAreLeftToFabricLoader() throws Exception {
        Path path = jar("native.jar", Map.of("fabric.mod.json", "{}".getBytes(),
                "example/Main.class", modClass("sample", "Example", "1")));
        assertNull(scanner.inspect(path));
    }

    @Test void readsCoremodsEvenWithoutModAnnotations() throws Exception {
        Path path = jar("core.jar", Map.of("META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\r\nFMLCorePlugin: example.Core\r\nFMLAT: core_at.cfg\r\n\r\n".getBytes()));
        var result = scanner.inspect(path);
        assertEquals("example.Core", result.corePlugin());
        assertEquals(java.util.List.of("core_at.cfg"), result.accessTransformers());
    }

    @Test void discoversTweakerOnlyModsAndTheirPlainLibraryDependencies() throws Exception {
        Path game = temporary.resolve("tweaker-game");
        Path mods = game.resolve("mods");
        Files.createDirectories(mods);
        ClassWriter library = new ClassWriter(0);
        library.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/Library", null, "java/lang/Object", null);
        library.visitEnd();
        Files.write(mods.resolve("library.jar"), zip(Map.of("example/Library.class", library.toByteArray())));
        assertTrue(scanner.discover(game, mods).isEmpty(), "A library alone must preserve the normal Fabric launch.");
        Files.write(mods.resolve("tweaker.jar"), zip(Map.of("META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\r\nTweakClass: example.Tweaker\r\n\r\n".getBytes())));
        var found = scanner.discover(game, mods);
        assertEquals(2, found.size());
        assertEquals("example.Tweaker", found.stream().filter(file -> file.path().endsWith("tweaker.jar")).findFirst().orElseThrow().tweakClass());
        assertNull(scanner.inspect(mods.resolve("library.jar")));
    }

    @Test void metadataCanOverrideUnexpandedJourneyMapDependencies() throws Exception {
        String info = "[{\"modid\":\"sample\",\"useDependencyInformation\":true,"
                + "\"requiredMods\":[\"Forge@[11.15.1.1722,)\"],"
                + "\"dependencies\":[\"Forge@[11.15.1.1722,)\"]}]";
        Path path = jar("metadata.jar", Map.of("example/Main.class", modClass("sample", "Example", "1"),
                "mcmod.info", info.getBytes()));
        assertEquals("required-after:Forge@[11.15.1.1722,)", scanner.inspect(path).mods().get(0).dependencies());
    }

    @Test void discoversBothActiveAndManualFoldersWithoutLoadingDisabledOrOtherVersions() throws Exception {
        Path manual = temporary.resolve("game/mods");
        Path active = temporary.resolve("cluster-mods");
        Files.createDirectories(manual.resolve("1.8.9"));
        Files.createDirectories(manual.resolve("1.12.2"));
        Files.createDirectories(active);
        byte[] contents = zip(Map.of("example/Main.class", modClass("sample", "Example", "1")));
        Files.write(manual.resolve("a.jar"), contents);
        Files.write(manual.resolve("1.8.9/b.JAR"), contents);
        Files.write(manual.resolve("1.12.2/ignored.jar"), contents);
        Files.write(manual.resolve("disabled.jar.disabled"), contents);
        Files.write(active.resolve("c.jar"), contents);
        assertEquals(3, scanner.discover(temporary.resolve("game"), active).size());
    }

    @Test void rejectsInvalidArchivesAndMetadata() throws Exception {
        Path path = jar("invalid.jar", Map.of("../outside.class", new byte[1]));
        assertThrows(IOException.class, () -> scanner.inspect(path));
        Path metadata = jar("invalid-metadata.jar", Map.of("mcmod.info", "\"not-an-object\"".getBytes()));
        assertThrows(IOException.class, () -> scanner.inspect(metadata));
    }

    @Test void inspectsProvidedModsWithoutExecutingTheirCode() throws Exception {
        Path samples = ForgeTestFixtures.optionalGuestDirectory();
        Assumptions.assumeTrue(Files.isDirectory(samples));
        Map<String, ForgeModScanner.ModFile> mods = new LinkedHashMap<>();
        try (var files = Files.list(samples)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".jar")).toList()) {
                var file = scanner.inspect(path);
                assertNotNull(file, path.toString());
                if (!file.mods().isEmpty()) mods.put(file.mods().get(0).id(), file);
            }
        }
        assertTrue(mods.keySet().containsAll(java.util.Set.of("JEI", "journeymap", "nei", "keystrokes", "wawla")),
                "Discovered mod IDs: " + mods.keySet());
        assertEquals("codechicken.nei.asm.NEICorePlugin", mods.get("nei").corePlugin());
        assertTrue(mods.get("keystrokes").mixinConfigs().contains("mixins.raven.json"));
        assertEquals("required-after:Waila", mods.get("wawla").mods().get(0).dependencies());
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path path = temporary.resolve(name);
        Files.write(path, zip(entries));
        return path;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] modClass(String id, String name, String version) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/Main", null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        annotation.visit("modid", id);
        annotation.visit("name", name);
        annotation.visit("version", version);
        annotation.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
