package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

class HostApiReferenceCompatibilityTest {
    private static final String COMMANDS = "net/minecraftforge/client/ClientCommandHandler";

    @Test void realFabricChatCallResolvesTheActualRemappedForgeOverride() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path game = root.resolve("build/forge-compat/runtime-qa/20261016-042311-a30217cef9/all/game");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Assumptions.assumeTrue(Files.isDirectory(game) && Files.isRegularFile(tiny));
        Path patched;
        try (var files = Files.list(game.resolve(".arcadium/forge-compat/game"))) {
            patched = files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow();
        }
        Path runtime;
        try (var files = Files.walk(game.resolve(".arcadium/forge-compat/game-cache"))) {
            runtime = files.filter(path -> path.getFileName().toString().equals("client-intermediary.jar")).findFirst().orElseThrow();
        }
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader);
        }
        Path forge = root.resolve("build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        var transformer = new ForgeJarTransformer(); transformer.index(patched, mappings, true); transformer.index(forge, mappings, true);
        ClassNode screen = read(runtime, mappings.classFromSrg("net/minecraft/client/gui/GuiScreen"));
        var calls = screen.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
            .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
            .filter(call -> call.owner.equals(COMMANDS)).toList();
        assertEquals(1, calls.size());
        MethodInsnNode chat = calls.get(0); assertEquals("a", chat.name);
        ClassNode api = new ClassNode(); read(forge, COMMANDS).accept(new ClassRemapper(api, mappings.officialToRuntime()));
        assertFalse(api.methods.stream().anyMatch(method -> method.name.equals(chat.name) && method.desc.equals(chat.desc)));
        HostApiReferenceCompatibility.normalize(screen, mappings);
        assertEquals(mappings.methodFromSrg("net/minecraft/command/CommandHandler", "func_71556_a",
            "(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;)I"), chat.name);
        assertTrue(api.methods.stream().anyMatch(method -> method.name.equals(chat.name) && method.desc.equals(chat.desc)));
        byte[] once = bytes(screen); HostApiReferenceCompatibility.normalize(screen, mappings); assertArrayEquals(once, bytes(screen));
    }

    @Test void mapsInheritedMethodHandlesAndLeavesOtherOwnersUntouched() throws Exception {
        var mappings = new ForgeMappings(resolver("tiny\t2\t0\tofficial\tintermediary\n"
            + "c\ta\tnet/minecraft/Parent\n\tm\t()V\ta\tm_run\n"),
            new StringReader("CL: a net/minecraft/test/Parent\nMD: a/a ()V net/minecraft/test/Parent/func_run ()V\n"));
        mappings.indexOfficialHierarchy(COMMANDS, "a", List.of());
        ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "net/minecraft/Caller", null, "java/lang/Object", null);
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()V", null, null);
        var reference = new LdcInsnNode(new Handle(Opcodes.H_INVOKEVIRTUAL, COMMANDS, "a", "()V", false));
        method.instructions.add(reference); method.visitInsn(Opcodes.POP);
        MethodInsnNode other = new MethodInsnNode(Opcodes.INVOKESTATIC, "example/Original", "a", "()V", false);
        method.instructions.add(other); method.visitInsn(Opcodes.RETURN); node.methods.add(method);
        HostApiReferenceCompatibility.normalize(node, mappings);
        assertEquals("m_run", ((Handle) reference.cst).getName()); assertEquals("a", other.name);
        byte[] once = bytes(node); HostApiReferenceCompatibility.normalize(node, mappings); assertArrayEquals(once, bytes(node));
    }

    private static ClassNode read(Path path, String name) throws Exception {
        try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static MappingResolver resolver(String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary");
    }
}
