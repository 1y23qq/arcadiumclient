package io.github.arcadiumclient.forgecompat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class BeforeForgeInvokeTest {
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String WRAPPER = "net/minecraftforge/fml/common/asm/transformers/TerminalTransformer$ExitVisitor";
    private static final String SYSTEM_EXIT = "Ljava/lang/System;exit(I)V";
    @TempDir Path temporary;

    @BeforeAll static void configure() throws Exception { HostCompatibilityPatchesTest.configure(); }
    @AfterAll static void restore() { InjectionPoint.register(BeforeInvoke.class); }

    private static InjectionPointData data(String targetClass, String requested, int ordinal) {
        return new InjectionPointData(HostCompatibilityPatchesTest.context(targetClass), "INVOKE", List.of(), requested,
            "exit-slice", ordinal, Opcodes.INVOKESTATIC, "crash-exit", 0);
    }

    private static MethodInsnNode call(InsnList code, String owner, String name, String descriptor, int opcode) {
        var instruction = new MethodInsnNode(opcode, owner, name, descriptor, false);
        code.add(instruction);
        return instruction;
    }

    private static MethodInsnNode wrapper(InsnList code) {
        return call(code, WRAPPER, "systemExitCalled", "(I)V", Opcodes.INVOKESTATIC);
    }

    private static List<AbstractInsnNode> matches(BeforeInvoke point, InsnList code) {
        List<AbstractInsnNode> matches = new ArrayList<>();
        point.find("()V", code, matches);
        return matches;
    }

    @Test void prefersTheUnchangedSystemExitCallWhenBothFormsExist() {
        InsnList code = new InsnList();
        var original = call(code, "java/lang/System", "exit", "(I)V", Opcodes.INVOKESTATIC);
        wrapper(code);
        var input = data(MINECRAFT, SYSTEM_EXIT, -1);
        assertEquals(List.of(original), matches(new BeforeForgeInvoke(input), code));
        assertEquals(matches(new BeforeInvoke(input), code), matches(new BeforeForgeInvoke(input), code));
    }

    @Test void findsOnlyTheTerminalTransformersEquivalentStaticCall() {
        InsnList code = new InsnList();
        var actual = wrapper(code);
        call(code, WRAPPER, "runtimeExitCalled", "(Ljava/lang/Runtime;I)V", Opcodes.INVOKESTATIC);
        var input = data(MINECRAFT, SYSTEM_EXIT, -1);
        assertTrue(matches(new BeforeInvoke(input), code).isEmpty());
        assertEquals(List.of(actual), matches(new BeforeForgeInvoke(input), code));
    }

    @Test void preservesOrdinalsAndLeavesOutOfRangeRequestsUnmatched() {
        InsnList code = new InsnList();
        wrapper(code);
        var second = wrapper(code);
        assertEquals(List.of(second), matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, 1)), code));
        assertTrue(matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, 2)), code).isEmpty());
    }

    @Test void neverUsesTheWrapperToRescueAMissingOrdinalWhenSystemExitRemains() {
        InsnList code = new InsnList();
        call(code, "java/lang/System", "exit", "(I)V", Opcodes.INVOKESTATIC);
        wrapper(code);
        wrapper(code);
        assertTrue(matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, 1)), code).isEmpty());
    }

    @Test void leavesOtherTargetsAndIncompleteOrDifferentRequestsUnchanged() {
        InsnList code = new InsnList();
        wrapper(code);
        assertTrue(matches(new BeforeForgeInvoke(data("fixture/OtherClass", SYSTEM_EXIT, -1)), code).isEmpty());
        for (String request : List.of("exit(I)V", "Ljava/lang/Runtime;exit(I)V", "Ljava/lang/System;exit(J)V",
                "Ljava/lang/System;gc()V")) {
            var input = data(MINECRAFT, request, -1);
            assertEquals(matches(new BeforeInvoke(input), code), matches(new BeforeForgeInvoke(input), code));
        }
    }

    @Test void rejectsDifferentWrapperIdentitiesDescriptorsAndOpcodes() {
        InsnList code = new InsnList();
        call(code, "fixture/OtherWrapper", "systemExitCalled", "(I)V", Opcodes.INVOKESTATIC);
        call(code, WRAPPER, "systemExitCalled", "(J)V", Opcodes.INVOKESTATIC);
        call(code, WRAPPER, "systemExitCalled", "(I)V", Opcodes.INVOKEVIRTUAL);
        call(code, WRAPPER, "runtimeHaltCalled", "(Ljava/lang/Runtime;I)V", Opcodes.INVOKESTATIC);
        assertTrue(matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1)), code).isEmpty());
    }

    @Test void retainsOuterMetadataAndLeavesEveryTargetInstructionIntact() {
        InsnList code = new InsnList();
        wrapper(code);
        var before = List.of(code.toArray());
        var point = new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1));
        assertEquals("exit-slice", point.getSlice());
        assertEquals("crash-exit", point.getId());
        assertEquals(1, matches(point, code).size());
        assertEquals(before, List.of(code.toArray()));
    }

    @Test void keepsMixedEquivalentFormsConservativeInsteadOfReinterpretingOrdinals() {
        InsnList code = new InsnList();
        wrapper(code);
        call(code, "io/github/arcadiumclient/forgecompat/HostExitCompatibility", "handleExit", "(I)V", Opcodes.INVOKESTATIC);
        assertTrue(matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1)), code).isEmpty());
    }

    @Test void normalizesOnlyTheMappedCrashReportMethodAndPreservesOtherFmlCalls() throws Exception {
        ClassNode minecraft = crashExitFixture(MINECRAFT, "c", "(Lb;)V");
        MethodNode report = minecraft.methods.get(0);
        HostExitCompatibility.normalize(minecraft);
        assertEquals(1, matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1)), report.instructions).size());
        assertEquals(1, Arrays.stream(report.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode).count());
        new Analyzer<>(new BasicVerifier()).analyze(minecraft.name, report);
        for (ClassNode other : List.of(crashExitFixture("fixture/OtherClass", "c", "(Lb;)V"),
                crashExitFixture(MINECRAFT, "otherMethod", "(Lb;)V"), crashExitFixture(MINECRAFT, "c", "(Ljava/lang/Object;)V"))) {
            var original = List.of(other.methods.get(0).instructions.toArray());
            HostExitCompatibility.normalize(other);
            assertEquals(original, List.of(other.methods.get(0).instructions.toArray()));
        }
    }

    @Test void rejectsUnexpectedCrashExitStackPatternsWithoutPartiallyRewritingTheMethod() {
        ClassNode minecraft = crashExitFixture(MINECRAFT, "c", "(Lb;)V");
        var instructions = minecraft.methods.get(0).instructions;
        var load = Arrays.stream(instructions.toArray()).filter(instruction -> instruction.getOpcode() == Opcodes.ILOAD)
            .findFirst().orElseThrow();
        instructions.set(load, new InsnNode(Opcodes.ICONST_M1));
        var original = List.of(instructions.toArray());
        var failure = assertThrows(IllegalStateException.class, () -> HostExitCompatibility.normalize(minecraft));
        assertTrue(failure.getMessage().contains("Unsupported Forge crash-report exit bytecode"));
        assertEquals(original, List.of(instructions.toArray()));
    }

    @Test void delegatesTheStaticAdapterToTheRealFmlExitImplementation() throws Exception {
        ClassNode adapter = new ClassNode();
        try (var input = getClass().getResourceAsStream("/io/github/arcadiumclient/forgecompat/HostExitCompatibility.class")) {
            assertNotNull(input);
            new ClassReader(input.readAllBytes()).accept(adapter, 0);
        }
        MethodNode exit = adapter.methods.stream().filter(method -> method.name.equals("handleExit") && method.desc.equals("(I)V"))
            .findFirst().orElseThrow();
        assertEquals(List.of("net/minecraftforge/fml/common/FMLCommonHandler.instance()Lnet/minecraftforge/fml/common/FMLCommonHandler;",
            "net/minecraftforge/fml/common/FMLCommonHandler.handleExit(I)V"), Arrays.stream(exit.instructions.toArray())
            .filter(instruction -> instruction instanceof MethodInsnNode).map(instruction -> {
                var call = (MethodInsnNode) instruction;
                return call.owner + "." + call.name + call.desc;
            }).toList());
        new Analyzer<>(new BasicVerifier()).analyze(adapter.name, exit);
    }

    private static ClassNode crashExitFixture(String owner, String name, String descriptor) {
        ClassNode node = new ClassNode();
        node.name = owner;
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        call(method.instructions, "net/minecraftforge/fml/common/FMLCommonHandler", "instance",
            "()Lnet/minecraftforge/fml/common/FMLCommonHandler;", Opcodes.INVOKESTATIC);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        call(method.instructions, "net/minecraftforge/fml/common/FMLCommonHandler", "handleExit", "(I)V", Opcodes.INVOKEVIRTUAL);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 2;
        method.maxLocals = 3;
        node.methods.add(method);
        return node;
    }

    @Test void preservesTheRealCrashPatchCallbackAndFindsActualTransformedMinecraftCalls() throws Exception {
        Path crashPatch = Path.of("../build/download-cache/packages/mods/modrinth/nZ3E8WQz/tD0pt5Br/CrashPatch-2.2.0+1.8.9.jar");
        Path forge = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Assumptions.assumeTrue(Files.isRegularFile(crashPatch) && Files.isRegularFile(forge) && Files.isRegularFile(client));
        ClassNode mixin = read(crashPatch, "org/polyfrost/crashpatch/mixin/Mixin_CatchCrashes.class");
        MethodNode callback = mixin.methods.stream().filter(method -> method.name.equals("crashpatch$exitIfDying"))
            .findFirst().orElseThrow();
        assertEquals("(I)V", callback.desc);
        assertTrue(Arrays.stream(callback.instructions.toArray()).anyMatch(instruction -> instruction instanceof FieldInsnNode field
            && field.name.equals("crashpatch$letDie") && field.desc.equals("Z")));
        assertEquals(1, matches(new BeforeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1)), callback.instructions).size());
        AnnotationNode redirect = callback.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/Redirect;"))
            .findFirst().orElseThrow();
        @SuppressWarnings("unchecked") List<AnnotationNode> locations = (List<AnnotationNode>) redirect.values.get(redirect.values.indexOf("at") + 1);
        AnnotationNode location = locations.get(0);
        assertEquals(SYSTEM_EXIT, location.values.get(location.values.indexOf("target") + 1));
        assertFalse(redirect.values.contains("require"));

        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) {
            assertNotNull(input); Files.copy(input, patches);
        }
        Path patched = new ForgeGamePatcher().patch(client, patches, temporary.resolve("patched"));
        ClassNode minecraft = read(patched, "ave.class");
        minecraft.name = MINECRAFT;
        MethodNode report = minecraft.methods.stream().filter(method -> method.name.equals("c") && method.desc.equals("(Lb;)V"))
            .findFirst().orElseThrow();
        assertTrue(matches(new BeforeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1)), report.instructions).isEmpty());
        assertEquals(1, Arrays.stream(report.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.owner.equals("net/minecraftforge/fml/common/FMLCommonHandler") && call.name.equals("handleExit")).count());
        HostExitCompatibility.normalize(minecraft);
        var crashTargets = matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1)), report.instructions);
        assertEquals(1, crashTargets.size());
        assertEquals("io/github/arcadiumclient/forgecompat/HostExitCompatibility", ((MethodInsnNode) crashTargets.get(0)).owner);
        assertEquals(callback.desc, ((MethodInsnNode) crashTargets.get(0)).desc);
        new Analyzer<>(new BasicVerifier()).analyze(minecraft.name, report);
        ClassWriter source = new ClassWriter(0);
        minecraft.accept(source);
        byte[] transformed;
        try (URLClassLoader loader = new URLClassLoader(new URL[]{forge.toUri().toURL()}, getClass().getClassLoader())) {
            var transformer = (IClassTransformer) loader.loadClass("net.minecraftforge.fml.common.asm.transformers.TerminalTransformer")
                .getConstructor().newInstance();
            transformed = transformer.transform(MINECRAFT, MINECRAFT, source.toByteArray());
        }
        ClassNode result = new ClassNode();
        new ClassReader(transformed).accept(result, 0);
        int total = 0;
        for (MethodNode original : minecraft.methods) {
            var input = data(MINECRAFT, SYSTEM_EXIT, -1);
            int expected = matches(new BeforeInvoke(input), original.instructions).size();
            if (expected == 0) continue;
            MethodNode method = result.methods.stream().filter(candidate -> candidate.name.equals(original.name)
                && candidate.desc.equals(original.desc)).findFirst().orElseThrow();
            assertTrue(matches(new BeforeInvoke(input), method.instructions).isEmpty());
            var nodes = matches(new BeforeForgeInvoke(input), method.instructions);
            assertEquals(expected, nodes.size());
            for (var node : nodes) {
                var call = (MethodInsnNode) node;
                assertEquals(WRAPPER, call.owner);
                assertEquals("systemExitCalled", call.name);
                assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
            }
            total += expected;
        }
        assertEquals(2, total);
        MethodNode runtimeReport = result.methods.stream().filter(method -> method.name.equals("c") && method.desc.equals("(Lb;)V"))
            .findFirst().orElseThrow();
        assertEquals(1, matches(new BeforeForgeInvoke(data(MINECRAFT, SYSTEM_EXIT, -1)), runtimeReport.instructions).size());
        new Analyzer<>(new BasicVerifier()).analyze(result.name, runtimeReport);
    }

    private static ClassNode read(Path jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name))) {
            new ClassReader(input.readAllBytes()).accept(node, 0);
        }
        return node;
    }
}
