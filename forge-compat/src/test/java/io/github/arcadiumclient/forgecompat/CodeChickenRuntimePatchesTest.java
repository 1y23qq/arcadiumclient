package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class CodeChickenRuntimePatchesTest {
    private static final String SHA256 = "d34d7c499e0e89869412dd7a65cb181d3d4fda6aac798302f94b7e8bf1574f4d";
    private static final String OWNER = "codechicken/lib/asm/ObfMapping";
    private static final String TINY = """
        tiny\t2\t0\tofficial\tintermediary
        c\ta\thost/Game
        \tf\tI\tx\tf_timer
        \tm\t()V\ta\tm_tick
        """;
    private static final String SRG = """
        CL: a net/minecraft/client/Minecraft
        FD: a/x net/minecraft/client/Minecraft/field_1
        MD: a/a ()V net/minecraft/client/Minecraft/func_1 ()V
        """;
    @TempDir Path temporary;

    @Test void appliesOnlyToTheAuditedCclArchive() throws Exception {
        assertNull(CodeChickenRuntimePatches.forMod("unrelated", null));
        assertNotNull(CodeChickenRuntimePatches.forMod(SHA256, mappings()));
    }

    @Test void usesRealHostMappingsForReflectionAndSrgNamesForTemporaryAsm() throws Exception {
        CodeChickenRuntimePatches.forMod(SHA256, mappings());
        var runtime = CodeChickenRuntimePatches.runtimeRemapper();
        assertEquals("host/Game", runtime.map("net/minecraft/client/Minecraft"));
        assertEquals("m_tick", runtime.mapMethodName("net/minecraft/client/Minecraft", "func_1", "()V"));
        assertEquals("f_timer", runtime.mapFieldName("net/minecraft/client/Minecraft", "field_1", "I"));
        assertEquals("[Lhost/Game;", runtime.mapDesc("[Lnet/minecraft/client/Minecraft;"));
        for (String owner : List.of("a", "host/Game", "net/minecraft/client/Minecraft")) {
            assertEquals("net/minecraft/client/Minecraft", CodeChickenRuntimePatches.legacyClassName(owner));
        }
        assertEquals("java/lang/String", CodeChickenRuntimePatches.legacyClassName("java/lang/String"));
        assertNull(CodeChickenRuntimePatches.legacyClassName(null));
        assertEquals("field_1", CodeChickenRuntimePatches.legacyFieldName("a", "x", "I"));
        assertEquals("field_1", CodeChickenRuntimePatches.legacyFieldName("host/Game", "f_timer", "I"));
        assertEquals("field_1", CodeChickenRuntimePatches.legacyFieldName("net/minecraft/client/Minecraft", "field_1", "I"));
        assertEquals("field_1", CodeChickenRuntimePatches.legacyFieldName(null, "field_1", null));
        assertEquals("func_1", CodeChickenRuntimePatches.legacyMethodName("a", "a", "()V"));
        assertEquals("func_1", CodeChickenRuntimePatches.legacyMethodName("host/Game", "m_tick", "()V"));
        assertEquals("func_1", CodeChickenRuntimePatches.legacyMethodName("net/minecraft/client/Minecraft", "func_1", "()V"));
        assertEquals("custom", CodeChickenRuntimePatches.legacyMethodName("mod/Helper", "custom", "()V"));
    }

    @Test void rejectsUnexpectedHelperShapeRatherThanSilentlySkippingThePatch() throws Exception {
        Consumer<ClassNode> patch = CodeChickenRuntimePatches.forMod(SHA256, mappings());
        ClassNode unexpected = new ClassNode();
        unexpected.name = OWNER;
        assertThrows(IllegalStateException.class, () -> patch.accept(unexpected));
    }

    @Test void patchesOnlyTwoRealCclHelpersAndRetainsTheirExistingAsmAlgorithms() throws Exception {
        Path source = ccl();
        Consumer<ClassNode> patch = CodeChickenRuntimePatches.forMod(SHA256, mappings());
        List<String> changed = new ArrayList<>();
        try (ZipFile archive = new ZipFile(source.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (var input = archive.getInputStream(entry)) { new ClassReader(input.readAllBytes()).accept(node, 0); }
                byte[] before = bytes(node);
                patch.accept(node);
                if (!java.util.Arrays.equals(before, bytes(node))) changed.add(node.name);
                verify(node);
                if (node.name.equals(OWNER)) {
                    for (String methodName : List.of("<clinit>", "loadMCPRemapper")) {
                        MethodNode method = node.methods.stream().filter(value -> value.name.equals(methodName)).findFirst().orElseThrow();
                        for (var instruction : method.instructions) {
                            if (instruction instanceof MethodInsnNode call) {
                                assertFalse(call.owner.contains("MCPRemapper"), "CSV/Swing discovery must not run");
                                assertFalse(call.owner.contains("LaunchClassLoader"), "Byte availability must not select a development mapping mode");
                            }
                        }
                    }
                    MethodNode classloading = node.methods.stream().filter(value -> value.name.equals("toClassloading")).findFirst().orElseThrow();
                    assertTrue(java.util.stream.StreamSupport.stream(classloading.instructions.spliterator(), false)
                        .anyMatch(instruction -> instruction instanceof FieldInsnNode field && field.name.equals("obfMapper")));
                }
                if (node.name.equals(OWNER + "$ObfRemapper")) {
                    for (MethodNode method : node.methods) for (var instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode field) assertFalse(field.owner.contains("FMLDeobfuscatingRemapper"));
                    }
                }
            }
        }
        assertEquals(List.of(OWNER + "$ObfRemapper", OWNER).stream().sorted().toList(), changed.stream().sorted().toList());
    }

    @Test void transformsRealCclJarWithTheTrustedPatchAndChecksAll188Classes() throws Exception {
        Path source = ccl();
        var mappings = mappings();
        ForgeJarTransformer transformer = new ForgeJarTransformer();
        Path output = transformer.transform(source, temporary.resolve("remapped"), mappings, false,
            CodeChickenRuntimePatches.forMod(ForgeModScanner.sha256(source), mappings));
        int classes = 0;
        try (ZipFile archive = new ZipFile(output.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (var input = archive.getInputStream(entry)) { new ClassReader(input.readAllBytes()).accept(node, 0); }
                verify(node);
                classes++;
            }
            assertNotNull(archive.getEntry("assets/ccl/asm/hooks.asm"));
        }
        assertEquals(188, classes);
    }

    private static Path ccl() throws Exception {
        Path path = Path.of("../build/forge-compat/dependencies/CodeChickenLib-1.8.9-2.0.2.1-universal.jar").toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path));
        assertEquals(SHA256, ForgeModScanner.sha256(path));
        return path;
    }

    private static ForgeMappings mappings() throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(new StringReader(TINY), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true);
        return new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), new StringReader(SRG));
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void verify(ClassNode node) throws Exception {
        ClassNode serialized = new ClassNode();
        new ClassReader(bytes(node)).accept(serialized, 0);
        serialized.check(Opcodes.ASM9);
        for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
            new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method);
        }
    }
}
