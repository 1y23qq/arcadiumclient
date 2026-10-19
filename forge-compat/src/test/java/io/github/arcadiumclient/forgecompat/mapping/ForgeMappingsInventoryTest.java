package io.github.arcadiumclient.forgecompat.mapping;

import io.github.arcadiumclient.forgecompat.ForgeTestFixtures;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ForgeMappingsInventoryTest {
    @Test void remapsFullForgeApiAndAllProvidedModsWithoutLoadingTheirClasses() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path dependencies = root.resolve("build/forge-compat/dependencies");
        Path srg = dependencies.resolve("forge-1.8.9.srg");
        Path cache = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9");
        Path tiny = cache.resolve("calamus-gen2-intermediary-v2.tiny");
        Path vanilla = cache.resolve("minecraft-merged.jar");
        Path forge = dependencies.resolve("forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        List<Path> libraries = List.of(dependencies.resolve("CodeChickenCore-1.8.9-2.0.2.null-universal.jar"),
            dependencies.resolve("CodeChickenLib-1.8.9-2.0.2.1-universal.jar"),
            dependencies.resolve("Waila-1.6.0-B3_1.8.8.jar"));
        Path mods = ForgeTestFixtures.optionalGuestDirectory();
        Assumptions.assumeTrue(Files.isRegularFile(srg) && Files.isRegularFile(tiny)
            && Files.isRegularFile(vanilla) && Files.isRegularFile(forge) && Files.isDirectory(mods));
        Assumptions.assumeTrue(libraries.stream().allMatch(Files::isRegularFile));
        ForgeMappings mappings;
        try (var input = Files.newBufferedReader(srg)) {
            mappings = new ForgeMappings(ForgeMappingsTest.resolver("intermediary", Files.readString(tiny)), input);
        }
        index(mappings, vanilla, true);
        index(mappings, forge, true);
        for (Path library : libraries) index(mappings, library, false);
        List<Path> provided;
        try (var files = Files.list(mods)) {
            provided = files.filter(path -> path.toString().endsWith(".jar")).sorted().toList();
        }
        assertEquals(5, provided.size());
        for (Path mod : provided) index(mappings, mod, false);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(forge.getFileName().toString(), remap(mappings, forge, true));
        for (Path library : libraries) counts.put(library.getFileName().toString(), remap(mappings, library, false));
        for (Path mod : provided) counts.put(mod.getFileName().toString(), remap(mappings, mod, false));
        assertTrue(counts.values().stream().allMatch(count -> count > 0));
        assertEquals(192, counts.get("jei_1.8.9-2.28.18.187.jar"));
        assertEquals(10, counts.get("Wawla-1.8.9-1.1.4.171.jar"));
        System.out.println("Full SRG and real Fabric resolver structural remap: " + counts);
    }

    private static void index(ForgeMappings mappings, Path path, boolean official) throws Exception {
        try (ZipFile jar = new ZipFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassReader reader;
                try (var input = jar.getInputStream(entry)) { reader = new ClassReader(input.readAllBytes()); }
                if (official) mappings.indexOfficialHierarchy(reader.getClassName(), reader.getSuperName(), List.of(reader.getInterfaces()));
                else {
                    mappings.indexSrgHierarchy(reader.getClassName(), reader.getSuperName(), List.of(reader.getInterfaces()));
                    ClassNode node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    var annotations = new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
                    if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
                    if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
                    for (var annotation : annotations) {
                        if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                        List<String> targets = new ArrayList<>();
                        for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) {
                            String key = (String) annotation.values.get(i);
                            if (!(annotation.values.get(i + 1) instanceof List<?> values)) continue;
                            for (Object value : values) {
                                if (key.equals("value") && value instanceof Type type) targets.add(type.getInternalName());
                                if (key.equals("targets") && value instanceof String name) targets.add(name.replace('.', '/'));
                            }
                        }
                        if (!targets.isEmpty()) mappings.indexSrgMixinTargets(reader.getClassName(), targets);
                    }
                }
            }
        }
    }

    private static int remap(ForgeMappings mappings, Path path, boolean official) throws Exception {
        int count = 0;
        try (ZipFile jar = new ZipFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (var input = jar.getInputStream(entry)) {
                    ClassReader source = new ClassReader(input.readAllBytes());
                    ClassWriter output = new ClassWriter(0);
                    try {
                        source.accept(new ClassRemapper(output, official ? mappings.officialToRuntime() : mappings.srgToRuntime()), 0);
                        ClassNode result = new ClassNode();
                        new ClassReader(output.toByteArray()).accept(result, 0);
                        result.check(Opcodes.ASM9);
                        for (var method : result.methods) {
                            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                                new Analyzer<>(new BasicVerifier()).analyze(result.name, method);
                            }
                        }
                        assertEquals(official ? mappings.officialToRuntime().map(source.getClassName())
                            : mappings.classFromSrg(source.getClassName()), result.name);
                    } catch (RuntimeException exception) {
                        throw new AssertionError(path.getFileName() + "!" + entry.getName(), exception);
                    }
                    count++;
                }
            }
        }
        return count;
    }
}
