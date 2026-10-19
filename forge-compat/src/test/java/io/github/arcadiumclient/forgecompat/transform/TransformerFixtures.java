package io.github.arcadiumclient.forgecompat.transform;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class TransformerFixtures {
    static final String GAME = "net/minecraft/client/Minecraft";
    static final String RUNTIME = "host/Game";
    static final String MIXIN = "example/GameMixin";
    static final String TINY = """
        tiny\t2\t0\tofficial\tintermediary\tnamed
        c\ta\thost/Game\tdev/Game
        \tf\tI\tx\tf_timer\ttimer
        \tm\t()V\ta\tm_tick\ttick
        \tm\t()La;\tb\tm_get\tgetGame
        c\tb\thost/World\tdev/World
        """;
    static final String SRG = """
        CL: a net/minecraft/client/Minecraft
        CL: b net/minecraft/world/World
        FD: a/x net/minecraft/client/Minecraft/field_1
        MD: a/a ()V net/minecraft/client/Minecraft/func_1 ()V
        MD: a/b ()La; net/minecraft/client/Minecraft/func_2 ()Lnet/minecraft/client/Minecraft;
        """;

    static MappingResolver resolver(String namespace, String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl")
            .getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true);
        return (MappingResolver) constructor.newInstance(tree, namespace);
    }

    static ForgeMappings mappings() throws Exception { return mappings("intermediary"); }
    static ForgeMappings mappings(String namespace) throws Exception {
        return new ForgeMappings(resolver(namespace, TINY), new StringReader(SRG));
    }

    static ClassNode simple(String name) {
        ClassNode node = new ClassNode();
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        node.methods.add(constructor);
        return node;
    }

    static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static Path jar(Path file, Map<String, byte[]> entries) throws Exception {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    static Map<String, byte[]> entries(Path file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                try (var input = zip.getInputStream(entry)) { result.put(entry.getName(), input.readAllBytes()); }
            }
        }
        return result;
    }

    static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static int verify(Path file) throws Exception {
        int count = 0;
        for (var entry : entries(file).entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassNode node = node(entry.getValue());
            node.check(Opcodes.ASM9);
            for (MethodNode method : node.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                    try { new Analyzer<>(new BasicVerifier()).analyze(node.name, method); }
                    catch (Exception exception) { throw new AssertionError(entry.getKey() + "." + method.name + method.desc, exception); }
                }
            }
            count++;
        }
        return count;
    }
}
