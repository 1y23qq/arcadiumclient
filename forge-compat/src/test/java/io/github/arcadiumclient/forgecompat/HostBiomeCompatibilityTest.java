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
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.points.BeforeFieldAccess;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class HostBiomeCompatibilityTest {
    private static final String SRG_BIOME = "net/minecraft/world/biome/BiomeGenBase";
    private static final String FIXTURE = "io/github/arcadiumclient/forgecompat/fixture/RegistrationBiome";
    @TempDir Path temporary;

    @Test void verifiesCopiedConstructorFramesOnTheJvmAndPreservesRegisterFalse() throws Exception {
        ClassNode node = fixture();
        byte[] registerBefore = methodBytes(node.name, constructor(node, "(IZ)V"));
        HostBiomeCompatibility.normalize(node, fixtureMappings());
        MethodNode single = constructor(node, "(I)V");
        assertEquals(1, single.parameters.size());
        assertEquals("id", single.parameters.get(0).name);
        assertTrue(single.maxLocals >= 3);
        assertEquals(Opcodes.ICONST_1, single.instructions.getFirst().getOpcode());
        assertTrue(Arrays.stream(single.instructions.toArray()).anyMatch(instruction -> instruction instanceof FrameNode frame
            && frame.local != null && frame.local.size() == 3));
        assertArrayEquals(registerBefore, methodBytes(node.name, constructor(node, "(IZ)V")));
        class FixtureLoader extends ClassLoader {
            Class<?> define(byte[] bytes) { return defineClass(FIXTURE.replace('/', '.'), bytes, 0, bytes.length); }
        }
        Class<?> loaded = new FixtureLoader().define(bytes(node));
        Object registered = loaded.getConstructor(int.class).newInstance(17);
        Object[] registry = (Object[]) loaded.getField("values").get(null);
        assertEquals(17, loaded.getField("id").getInt(registered));
        assertEquals(7, loaded.getField("flowers").getInt(registered));
        assertSame(registered, registry[17]);
        Object absent = loaded.getConstructor(int.class, boolean.class).newInstance(31, false);
        assertEquals(31, loaded.getField("id").getInt(absent));
        assertEquals(7, loaded.getField("flowers").getInt(absent));
        assertNull(registry[31]);
        Object explicit = loaded.getConstructor(int.class, boolean.class).newInstance(32, true);
        assertSame(explicit, registry[32]);
        new Analyzer<>(new BasicVerifier()).analyze(node.name, single);
    }

    @Test void isIdempotentAndLeavesUnrelatedClassesUntouched() throws Exception {
        var mappings = fixtureMappings();
        ClassNode node = fixture();
        HostBiomeCompatibility.normalize(node, mappings);
        byte[] normalized = bytes(node);
        HostBiomeCompatibility.normalize(node, mappings);
        assertArrayEquals(normalized, bytes(node));
        ClassNode unrelated = fixture();
        unrelated.name = "fixture/Other";
        byte[] original = bytes(unrelated);
        HostBiomeCompatibility.normalize(unrelated, mappings);
        assertArrayEquals(original, bytes(unrelated));
    }

    @Test void rejectsUnknownDelegationAndRegistrationShapesBeforeChangingEitherConstructor() throws Exception {
        for (boolean delegation : List.of(true, false)) {
            ClassNode node = fixture();
            MethodNode target = constructor(node, delegation ? "(I)V" : "(IZ)V");
            AbstractInsnNode instruction = Arrays.stream(target.instructions.toArray()).filter(value ->
                delegation ? value.getOpcode() == Opcodes.ICONST_1 : value.getOpcode() == Opcodes.AASTORE).findFirst().orElseThrow();
            target.instructions.set(instruction, new InsnNode(delegation ? Opcodes.ICONST_0 : Opcodes.NOP));
            byte[] original = bytes(node);
            assertThrows(IllegalStateException.class, () -> HostBiomeCompatibility.normalize(node, fixtureMappings()));
            assertArrayEquals(original, bytes(node));
        }
    }

    @Test void restoresBothActualOslInjectionPointsOnTheOfficialForgePatchedBiome() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Path dependencies = root.resolve("build/forge-compat/dependencies");
        Path osl = ForgeTestFixtures.nativeJar("ornithe-standard-libraries-0.22.0.jar");
        Assumptions.assumeTrue(Files.isRegularFile(client) && Files.isRegularFile(tiny) && Files.isRegularFile(osl));
        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) {
            assertNotNull(input); Files.copy(input, patches);
        }
        Path patched = new ForgeGamePatcher().patch(client, patches, temporary.resolve("patched"));
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(dependencies.resolve("forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader);
        }
        new ForgeJarTransformer().index(patched, mappings, true);
        ClassNode vanilla = readClass(client, "ady.class"), source = readClass(patched, "ady.class");
        ClassNode runtime = new ClassNode();
        source.accept(new ClassRemapper(runtime, mappings.officialToRuntime()));
        ClassNode mixin = actualOslMixin(osl);
        MethodNode auto = mixin.methods.stream().filter(method -> method.name.equals("osl$biomes$handleAutoAssignId")).findFirst().orElseThrow();
        AnnotationNode modifier = auto.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/ModifyVariable;")).findFirst().orElseThrow();
        assertEquals(List.of("<init>"), value(modifier, "method"));
        assertEquals(true, value(modifier, "argsOnly"));
        assertEquals(0, value(modifier, "ordinal"));
        AnnotationNode at = (AnnotationNode) value(modifier, "at");
        assertEquals("Ljava/lang/Object;<init>()V", value(at, "target"));
        var point = new BeforeInvoke(new InjectionPointData(HostCompatibilityPatchesTest.context(runtime.name), "INVOKE", List.of(),
            (String) value(at, "target"), "", -1, -1, "osl-biome-auto-id", 0));
        assertEquals(1, invokeMatches(point, constructor(vanilla, "(I)V")).size());
        assertEquals(0, invokeMatches(point, constructor(runtime, "(I)V")).size());
        MethodNode growth = mixin.methods.stream().filter(method -> method.name.equals("osl$biomes$growArrays")).findFirst().orElseThrow();
        AnnotationNode injection = growth.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        AnnotationNode field = (AnnotationNode) ((List<?>) value(injection, "at")).get(0);
        assertEquals(List.of("array=set"), value(field, "args"));
        var arrayPoint = new BeforeFieldAccess(new InjectionPointData(HostCompatibilityPatchesTest.context(runtime.name), "FIELD",
            List.of("array=set"), (String) value(field, "target"), "", -1, Opcodes.GETSTATIC, "osl-biome-grow", 0));
        assertEquals(0, fieldMatches(arrayPoint, constructor(runtime, "(I)V")).size());
        byte[] handler = methodBytes(mixin.name, auto);
        Map<String, byte[]> untouched = new LinkedHashMap<>();
        for (MethodNode method : runtime.methods) if (!method.name.equals("<init>") || !method.desc.equals("(I)V")) {
            untouched.put(method.name + method.desc, methodBytes(runtime.name, method));
        }
        HostBiomeCompatibility.normalize(runtime, mappings);
        assertEquals(1, invokeMatches(point, constructor(runtime, "(I)V")).size());
        assertEquals(1, fieldMatches(arrayPoint, constructor(runtime, "(I)V")).size());
        assertArrayEquals(handler, methodBytes(mixin.name, auto));
        assertTrue(Arrays.stream(auto.instructions.toArray()).anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.IntInsnNode integer
            && integer.operand == -172), "The original OSL automatic-ID sentinel must remain active");
        assertTrue(Arrays.stream(auto.instructions.toArray()).anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.IntInsnNode integer
            && integer.operand == 255), "The original OSL save-format ID limit must remain active");
        for (MethodNode method : runtime.methods) {
            if (!method.name.equals("<init>") || !method.desc.equals("(I)V")) assertArrayEquals(untouched.get(method.name + method.desc), methodBytes(runtime.name, method));
        }
        ClassNode serialized = new ClassNode();
        new ClassReader(bytes(runtime)).accept(serialized, 0);
        for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0) {
            new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method);
        }
        System.out.println("Real OSL biome auto-ID/grow targets restored 0→1 each; original (IZ)V and registry lifecycle unchanged");
    }

    private static ClassNode fixture() {
        ClassNode node = new ClassNode();
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, FIXTURE, null, "java/lang/Object", null);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "id", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "flowers", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "[L" + FIXTURE + ";", null, null));
        MethodNode init = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        init.visitIntInsn(Opcodes.SIPUSH, 256); init.visitTypeInsn(Opcodes.ANEWARRAY, FIXTURE);
        init.visitFieldInsn(Opcodes.PUTSTATIC, FIXTURE, "values", "[L" + FIXTURE + ";"); init.visitInsn(Opcodes.RETURN);
        node.methods.add(init);
        MethodNode single = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
        single.parameters = new ArrayList<>(List.of(new ParameterNode("id", 0)));
        single.visitVarInsn(Opcodes.ALOAD, 0); single.visitVarInsn(Opcodes.ILOAD, 1); single.visitInsn(Opcodes.ICONST_1);
        single.visitMethodInsn(Opcodes.INVOKESPECIAL, FIXTURE, "<init>", "(IZ)V", false); single.visitInsn(Opcodes.RETURN);
        node.methods.add(single);
        MethodNode register = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(IZ)V", null, null);
        register.visitVarInsn(Opcodes.ALOAD, 0); register.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        register.visitVarInsn(Opcodes.ALOAD, 0); register.visitVarInsn(Opcodes.ILOAD, 1); register.visitFieldInsn(Opcodes.PUTFIELD, FIXTURE, "id", "I");
        var done = new org.objectweb.asm.Label();
        register.visitVarInsn(Opcodes.ILOAD, 2); register.visitJumpInsn(Opcodes.IFEQ, done);
        register.visitFieldInsn(Opcodes.GETSTATIC, FIXTURE, "values", "[L" + FIXTURE + ";");
        register.visitVarInsn(Opcodes.ILOAD, 1); register.visitVarInsn(Opcodes.ALOAD, 0); register.visitInsn(Opcodes.AASTORE);
        register.visitLabel(done); register.visitVarInsn(Opcodes.ALOAD, 0); register.visitIntInsn(Opcodes.BIPUSH, 7);
        register.visitFieldInsn(Opcodes.PUTFIELD, FIXTURE, "flowers", "I"); register.visitInsn(Opcodes.RETURN);
        node.methods.add(register);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        ClassNode result = new ClassNode(); new ClassReader(writer.toByteArray()).accept(result, 0);
        return result;
    }

    private static ForgeMappings fixtureMappings() throws Exception {
        return new ForgeMappings(resolver("tiny\t2\t0\tofficial\tintermediary\nc\tady\t" + FIXTURE
            + "\n\tf\tI\taz\tid\n\tf\t[Lady;\taE\tvalues\n"), new StringReader("CL: ady " + SRG_BIOME
            + "\nFD: ady/az " + SRG_BIOME + "/field_76756_M\nFD: ady/aE " + SRG_BIOME + "/field_76773_a\n"));
    }

    private static MappingResolver resolver(String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary");
    }
    private static MethodNode constructor(ClassNode node, String descriptor) { return node.methods.stream().filter(method -> method.name.equals("<init>") && method.desc.equals(descriptor)).findFirst().orElseThrow(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        node.methods.add(method); return bytes(node);
    }
    private static ClassNode readClass(Path jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { new ClassReader(input.readAllBytes()).accept(node, 0); }
        return node;
    }
    private static ClassNode actualOslMixin(Path jar) throws Exception {
        byte[] nested;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            nested = zip.getInputStream(zip.getEntry("META-INF/jars/osl-biomes-0.1.0+mc1.8.2-pre5-mc15w51b.jar")).readAllBytes();
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(nested))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("net/ornithemc/osl/biomes/impl/mixin/common/BiomeMixin.class")) {
                    ClassNode node = new ClassNode(); new ClassReader(zip.readAllBytes()).accept(node, 0); return node;
                }
            }
        }
        throw new IllegalStateException("The real OSL 1.8.9 biome mixin is missing");
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    private static List<AbstractInsnNode> invokeMatches(BeforeInvoke point, MethodNode method) {
        List<AbstractInsnNode> matches = new ArrayList<>(); point.find(method.desc, method.instructions, matches); return matches;
    }
    private static List<AbstractInsnNode> fieldMatches(BeforeFieldAccess point, MethodNode method) {
        List<AbstractInsnNode> matches = new ArrayList<>(); point.find(method.desc, method.instructions, matches); return matches;
    }
}
