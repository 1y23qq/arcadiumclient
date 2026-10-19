package io.github.arcadiumclient.forgecompat;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ForgeRuntimeHeadlessTest {
    @TempDir Path temporary;

    @Test void initializesRealTrustedForgeOnJava25WithoutExecutingGuestMods() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path metadata = root.resolve("build/forge-compat/fml-runtime-review/trusted-classpath.json");
        Assumptions.assumeTrue(Files.isRegularFile(metadata));
        LinkedHashSet<Path> classpath = new LinkedHashSet<>();
        classpath.add(root.resolve("forge-compat/build/resources/main"));
        Path lzma = root.resolve("build/forge-compat/dependencies/lzma-0.0.1.jar");
        Assumptions.assumeTrue(Files.isRegularFile(lzma));
        classpath.add(lzma);
        for (Class<?> type : List.of(ForgeRuntimeHeadlessProbe.class, ForgeRuntimePatches.class,
                MappingResolver.class, ClassReader.class, ClassNode.class, Remapper.class, JsonParser.class,
                org.objectweb.asm.tree.analysis.AnalyzerException.class)) {
            classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        for (var element : new JsonParser().parse(Files.readString(metadata)).getAsJsonArray()) {
            Path path = Path.of(element.getAsString());
            if (!Files.isRegularFile(path)) continue;
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.getEntry("ave.class") != null || zip.getEntry("net/minecraft/client/main/Main.class") != null
                        || zip.getEntry("fabric.mod.json") != null || zip.getEntry("mcmod.info") != null
                        || path.getFileName().toString().startsWith("asm-")
                        || path.getFileName().toString().contains("Arcadium")) continue;
                classpath.add(path);
            }
        }
        Path evidence = root.resolve("build/forge-compat/fml-runtime-review/headless");
        Files.createDirectories(evidence);
        Path output = temporary.resolve("probe");
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-Djava.awt.headless=true", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp",
            String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()),
            ForgeRuntimeHeadlessProbe.class.getName(), root.toString(), output.toString()));
        ProcessBuilder builder = new ProcessBuilder(command).directory(temporary.toFile()).redirectErrorStream(true)
            .redirectOutput(evidence.resolve("probe.log").toFile());
        for (String key : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(key);
        Process process = builder.start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        String log = Files.readString(evidence.resolve("probe.log"));
        List<String> stages = log.lines().filter(line -> line.contains("ARCADIUM_FML_PROBE "))
            .map(line -> line.substring(line.indexOf("ARCADIUM_FML_PROBE "))).toList();
        Files.write(evidence.resolve("stages.txt"), stages);
        assertTrue(finished, "Trusted FML probe timed out: " + stages);
        assertEquals(0, process.exitValue(), "Trusted FML initialization failed: " + stages);
        assertTrue(stages.contains("ARCADIUM_FML_PROBE SUCCESS"));
        assertFalse(log.contains("Ambiguous inherited member mapping"), "Trusted FML remapper reported an ambiguous member");
        Files.copy(output.resolve("result.json"), evidence.resolve("result.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
