package io.github.arcadiumclient.forgecompat.mapping;

import com.google.gson.JsonParser;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ForgeMappingsRuntimeRoundTripTest {
    @Test void preservesEveryRealHostDeclarationAndMemberReferenceAcrossTheSrgView() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path game = fixture(root);
        Assumptions.assumeTrue(game != null, "Run the isolated JEI game fixture to provide the real Fabric-remapped game JAR");
        Path runtime = runtimeJar(game);
        Path patched;
        try (var files = Files.list(game.resolve(".arcadium/forge-compat/game"))) {
            patched = files.filter(path -> path.getFileName().toString().startsWith("minecraft-1.8.9-forge-")
                && path.toString().endsWith(".jar")).findFirst().orElseThrow();
        }
        Path dependencies = root.resolve("build/forge-compat/dependencies");
        Path loom = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9");
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(dependencies.resolve("forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(ForgeMappingsTest.resolver("intermediary",
                Files.readString(loom.resolve("calamus-gen2-intermediary-v2.tiny"))), reader);
        }
        ForgeJarTransformer transformer = new ForgeJarTransformer();
        transformer.index(patched, mappings, true);
        transformer.index(dependencies.resolve("forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"), mappings, true);
        transformer.index(root.resolve("forge-mod-test/jei_1.8.9-2.28.18.187.jar"), mappings, false);
        int classes = 0, methods = 0, fields = 0, references = 0;
        try (ZipFile archive = new ZipFile(runtime.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode before = new ClassNode();
                try (var input = archive.getInputStream(entry)) { new ClassReader(input.readAllBytes()).accept(before, 0); }
                ClassNode srg = new ClassNode(), after = new ClassNode();
                before.accept(new ClassRemapper(srg, mappings.runtimeToSrg()));
                srg.accept(new ClassRemapper(after, mappings.srgToRuntime()));
                assertEquals(before.name, after.name);
                assertEquals(before.superName, after.superName);
                assertEquals(before.interfaces, after.interfaces);
                assertEquals(before.signature, after.signature);
                assertEquals(before.methods.size(), after.methods.size());
                assertEquals(before.fields.size(), after.fields.size());
                for (int i = 0; i < before.methods.size(); i++) {
                    var first = before.methods.get(i); var second = after.methods.get(i);
                    String context = before.name + "." + first.name + first.desc;
                    assertEquals(first.name, second.name, context);
                    assertEquals(first.desc, second.desc, context);
                    assertEquals(first.signature, second.signature, context);
                    assertEquals(first.instructions.size(), second.instructions.size(), context);
                    for (int j = 0; j < first.instructions.size(); j++) {
                        var instruction = first.instructions.get(j); var changed = second.instructions.get(j);
                        if (instruction instanceof FieldInsnNode field && changed instanceof FieldInsnNode mapped) {
                            assertEquals(List.of(field.owner, field.name, field.desc), List.of(mapped.owner, mapped.name, mapped.desc), context);
                            references++;
                        } else if (instruction instanceof MethodInsnNode call && changed instanceof MethodInsnNode mapped) {
                            assertEquals(List.of(call.owner, call.name, call.desc), List.of(mapped.owner, mapped.name, mapped.desc), context);
                            references++;
                        }
                    }
                    methods++;
                }
                for (int i = 0; i < before.fields.size(); i++) {
                    var first = before.fields.get(i); var second = after.fields.get(i);
                    assertEquals(first.name, second.name, before.name);
                    assertEquals(first.desc, second.desc, before.name + "." + first.name);
                    assertEquals(first.signature, second.signature, before.name + "." + first.name);
                    fields++;
                }
                classes++;
            }
        }
        assertTrue(classes >= 2500);
        assertTrue(methods >= 20000);
        assertTrue(fields >= 8000);
        assertTrue(references >= 100000);
        System.out.println("Real Fabric runtime SRG roundtrip unchanged: classes=" + classes + ", methods=" + methods
            + ", fields=" + fields + ", memberReferences=" + references);
    }

    private static Path fixture(Path root) throws Exception {
        String configured = System.getProperty("arcadium.forgeTest.gameDir");
        if (configured != null) return Path.of(configured);
        Path directory = root.resolve("build/forge-compat/runtime-qa");
        if (!Files.isDirectory(directory)) return null;
        List<Path> runs;
        try (var files = Files.list(directory)) { runs = files.filter(Files::isDirectory).sorted(java.util.Comparator.reverseOrder()).toList(); }
        for (Path run : runs) for (String name : List.of("jei", "all")) {
            Path game = run.resolve(name + "/game");
            if (completed(game) && Files.isRegularFile(runtimeJar(game))
                && Files.isDirectory(game.resolve(".arcadium/forge-compat/game"))) return game;
        }
        return null;
    }

    private static boolean completed(Path game) throws Exception {
        Path result = game.resolve("forge-qa/result.json");
        if (!Files.isRegularFile(result)) return false;
        try (var input = Files.newBufferedReader(result)) {
            var data = new JsonParser().parse(input).getAsJsonObject();
            return data.has("passed") && data.get("passed").getAsBoolean();
        }
    }

    private static Path runtimeJar(Path game) throws Exception {
        Path result = game.resolve("forge-qa/result.json");
        if (Files.isRegularFile(result)) {
            try (var input = Files.newBufferedReader(result)) {
                var data = new JsonParser().parse(input).getAsJsonObject();
                var checks = data.getAsJsonObject("checks");
                if (checks != null && checks.has("forgeGameCache")) {
                    Path root = game.toAbsolutePath().normalize();
                    Path runtime = root.resolve(checks.getAsJsonObject("forgeGameCache").get("gameJar").getAsString()).normalize();
                    if (!runtime.startsWith(root)) throw new IllegalStateException("The Forge QA game cache escaped its fixture");
                    return runtime;
                }
            }
        }
        return game.resolve(".fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar");
    }
}
