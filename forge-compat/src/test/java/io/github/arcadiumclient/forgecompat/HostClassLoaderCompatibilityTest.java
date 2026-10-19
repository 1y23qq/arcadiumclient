package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostClassLoaderCompatibilityTest {
    private static final String LEGACY = Type.getInternalName(LaunchClassLoader.class);
    private static final String BRIDGE = Type.getInternalName(HostClassLoaderCompatibility.class);
    private static final String CCC_SHA = "10eda9be9979f33a0f7f2e6d211ff4e7f332269d122e76e2cd14457b9943c9b3";
    @TempDir Path temporary;
    private LaunchClassLoader previous;

    public static final class OwnSource {
        public static Class<?> value;
        public static int evaluations;
        public static Class<?> evaluate() { evaluations++; return value; }
    }
    public static final class OwnLookup {
        public static LaunchClassLoader read() { return (LaunchClassLoader) OwnSource.evaluate().getClassLoader(); }
    }
    private static final class OwnLoader extends ClassLoader {
        OwnLoader() { super(HostClassLoaderCompatibilityTest.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }

    @BeforeEach void save() { previous = Launch.classLoader; Launch.classLoader = null; OwnSource.value = null; OwnSource.evaluations = 0; }
    @AfterEach void restore() throws Exception {
        if (Launch.classLoader != null && Launch.classLoader != previous) Launch.classLoader.close();
        Launch.classLoader = previous; OwnSource.value = null;
    }

    @Test void adaptsTheConfiguredHostWhilePreservingRealLegacyNullAndWrongLoaderCasts() throws Exception {
        ForgeMappings mappings = mappings(); OwnLoader host = new OwnLoader();
        try (LaunchClassLoader configured = loader(host, mappings); LaunchClassLoader another = loader(host, mappings)) {
            Launch.classLoader = configured;
            assertSame(configured, HostClassLoaderCompatibility.fromClassLoader(host));
            assertSame(configured, HostClassLoaderCompatibility.fromClassLoader(configured));
            assertSame(another, HostClassLoaderCompatibility.fromClassLoader(another));
            assertNull(HostClassLoaderCompatibility.fromClassLoader(null));
            assertThrows(ClassCastException.class, () -> HostClassLoaderCompatibility.fromClassLoader(new OwnLoader()));
            Launch.classLoader = null;
            assertThrows(ClassCastException.class, () -> HostClassLoaderCompatibility.fromClassLoader(host));
            assertSame(another, HostClassLoaderCompatibility.fromClassLoader(another));
        }
    }

    @Test void verifiesThePatchedCallsiteOnTheJvmAndEvaluatesTheOriginalReceiverExactlyOnce() throws Throwable {
        ClassNode original = lookup(); OwnLoader brokenHost = new OwnLoader(); Class<?> broken = brokenHost.define(bytes(original));
        Launch.classLoader = loader(brokenHost, mappings()); OwnSource.value = broken;
        assertThrows(ClassCastException.class, () -> invoke(broken)); assertEquals(1, OwnSource.evaluations);
        Launch.classLoader.close();
        HostClassLoaderCompatibility.normalize(original);
        OwnLoader host = new OwnLoader(); Class<?> loaded = host.define(bytes(original)); Launch.classLoader = loader(host, mappings());
        OwnSource.value = loaded; OwnSource.evaluations = 0;
        assertSame(Launch.classLoader, invoke(loaded)); assertEquals(1, OwnSource.evaluations);
        OwnSource.value = null; OwnSource.evaluations = 0;
        assertThrows(NullPointerException.class, () -> invoke(loaded)); assertEquals(1, OwnSource.evaluations);
        OwnSource.value = String.class; OwnSource.evaluations = 0;
        assertNull(invoke(loaded)); assertEquals(1, OwnSource.evaluations);
        OwnSource.value = OwnSource.class; OwnSource.evaluations = 0;
        assertThrows(ClassCastException.class, () -> invoke(loaded)); assertEquals(1, OwnSource.evaluations);
        verify(original);
    }

    @Test void changesOnlyTheCastAndIsIdempotent() throws Exception {
        ClassNode node = lookup(); MethodNode method = method(node, "read");
        List<AbstractInsnNode> before = Arrays.asList(method.instructions.toArray());
        AbstractInsnNode cast = before.stream().filter(instruction -> instruction.getOpcode() == Opcodes.CHECKCAST).findFirst().orElseThrow();
        HostClassLoaderCompatibility.normalize(node);
        List<AbstractInsnNode> after = Arrays.asList(method.instructions.toArray()); assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) if (before.get(i) != cast) assertSame(before.get(i), after.get(i));
        assertEquals(1, adapters(node));
        byte[] once = bytes(node); HostClassLoaderCompatibility.normalize(node); assertArrayEquals(once, bytes(node));
    }

    @Test void leavesMergedObjectCastInputsAndOtherClassLoaderUsesUnchanged() throws Exception {
        ClassNode node = lookup(); MethodNode method = method(node, "read"); method.desc = "(Ljava/lang/Class;Ljava/lang/Object;Z)L" + LEGACY + ";";
        method.instructions.clear(); method.localVariables.clear(); method.tryCatchBlocks.clear();
        LabelNode fromClass = new LabelNode(), join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2)); method.instructions.add(new JumpInsnNode(Opcodes.IFNE, fromClass));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(fromClass); method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false));
        method.instructions.add(join); method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, LEGACY)); method.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
        ClassWriter framed = new ClassWriter(ClassWriter.COMPUTE_FRAMES); node.accept(framed);
        ClassNode valid = new ClassNode(); new ClassReader(framed.toByteArray()).accept(valid, 0);
        byte[] before = bytes(valid); HostClassLoaderCompatibility.normalize(valid); assertArrayEquals(before, bytes(valid)); verify(valid);
        ClassNode other = lookup();
        TypeInsnNode cast = (TypeInsnNode) Arrays.stream(method(other, "read").instructions.toArray()).filter(instruction -> instruction.getOpcode() == Opcodes.CHECKCAST).findFirst().orElseThrow();
        cast.desc = "java/net/URLClassLoader"; before = bytes(other);
        HostClassLoaderCompatibility.normalize(other); assertArrayEquals(before, bytes(other));
    }

    @Test void preservesTheActualCccPluginAndAllButItsThreeInvalidClassLoaderCasts() throws Exception {
        Path source = ccc(); int sites = 0, classes = 0; Map<String, Integer> changed = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            ClassNode plugin = read(zip, "codechicken/core/launch/CodeChickenCorePlugin.class");
            assertEquals(List.of("net/minecraftforge/fml/relauncher/IFMLLoadingPlugin", "net/minecraftforge/fml/relauncher/IFMLCallHook"), plugin.interfaces);
            byte[] pluginBefore = bytes(plugin); HostClassLoaderCompatibility.normalize(plugin); assertArrayEquals(pluginBefore, bytes(plugin));
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement(); if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = read(zip, entry.getName()); Map<String, byte[]> originals = new LinkedHashMap<>();
                for (MethodNode method : node.methods) originals.put(method.name + method.desc, methodBytes(node.name, method));
                HostClassLoaderCompatibility.normalize(node); int count = adapters(node); sites += count;
                if (count > 0) changed.put(node.name, count);
                for (MethodNode method : node.methods) if (adapters(method) == 0) assertArrayEquals(originals.get(method.name + method.desc), methodBytes(node.name, method));
                verify(node); classes++;
            }
        }
        assertEquals(90, classes); assertEquals(3, sites);
        assertEquals(Map.of("codechicken/core/launch/DepLoader$DepLoadInst", 2, "codechicken/core/asm/DefaultImplementationTransformer", 1), changed);
    }

    @Test void appliesTheAdaptationThroughTheRealGuestJarTransformationAndRetainsPluginResources() throws Exception {
        Path source = ccc(); ForgeMappings mappings = mappings();
        Path output = new ForgeJarTransformer().transform(source, temporary.resolve("guest"), mappings, false, ForgeRuntimePatches.forMod(CCC_SHA, mappings));
        int sites = 0, classes = 0;
        try (ZipFile zip = new ZipFile(output.toFile())) {
            Manifest manifest = new Manifest(zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF")));
            assertEquals("codechicken.core.launch.CodeChickenCorePlugin", manifest.getMainAttributes().getValue("FMLCorePlugin"));
            assertNotNull(zip.getEntry("META-INF/ccc_at.cfg"));
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement(); if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = read(zip, entry.getName()); sites += adapters(node); verify(node); classes++;
            }
        }
        assertEquals(90, classes); assertEquals(3, sites);
        Path fixtureJar = temporary.resolve("owned.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fixtureJar))) {
            ClassNode node = lookup(); zip.putNextEntry(new ZipEntry(node.name + ".class")); zip.write(bytes(node)); zip.closeEntry();
        }
        Path api = new ForgeJarTransformer().transform(fixtureJar, temporary.resolve("official"), mappings, true, null);
        try (ZipFile zip = new ZipFile(api.toFile())) { assertEquals(0, adapters(read(zip, Type.getInternalName(OwnLookup.class) + ".class"))); }
        System.out.println("Real CCC plugin implements both FML interfaces; all 90 classes verified, three guest-only ClassLoader casts adapted without changing plugin/resources");
    }

    private static Object invoke(Class<?> type) throws Throwable { try { return type.getMethod("read").invoke(null); } catch (InvocationTargetException exception) { throw exception.getCause(); } }
    private static ClassNode lookup() throws Exception {
        ClassNode node = new ClassNode();
        try (var input = HostClassLoaderCompatibilityTest.class.getResourceAsStream("/" + Type.getInternalName(OwnLookup.class) + ".class")) { assertNotNull(input); new ClassReader(input.readAllBytes()).accept(node, 0); }
        node.nestHostClass = null; node.nestMembers = null; return node;
    }
    private static LaunchClassLoader loader(ClassLoader target, ForgeMappings mappings) {
        FabricLauncher launcher = (FabricLauncher) Proxy.newProxyInstance(HostClassLoaderCompatibilityTest.class.getClassLoader(), new Class<?>[] {FabricLauncher.class}, (proxy, method, args) -> {
            if (method.getName().equals("getTargetClassLoader")) return target;
            throw new AssertionError("Unexpected launcher operation: " + method.getName());
        });
        return new LaunchClassLoader(new URL[0], launcher, mappings);
    }
    private static Path ccc() throws Exception { Path path = ForgeTestFixtures.optionalGuest("CodeChickenCore-1.8.9-2.0.2.null-universal.jar").toAbsolutePath().normalize(); assertEquals(CCC_SHA, ForgeModScanner.sha256(path)); return path; }
    private static ForgeMappings mappings() throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader("tiny\t2\t0\tofficial\tintermediary\nc\ta\tfixture/OwnGame\n"), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); return new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), new StringReader("CL: a net/minecraft/client/Minecraft\n"));
    }
    private static ClassNode read(ZipFile zip, String name) throws Exception { ClassNode node = new ClassNode(); try (var input = zip.getInputStream(zip.getEntry(name))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static int adapters(ClassNode node) { return node.methods.stream().mapToInt(HostClassLoaderCompatibilityTest::adapters).sum(); }
    private static int adapters(MethodNode method) { return (int) Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(BRIDGE) && call.name.equals("fromClassLoader")).count(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node); }
    private static void verify(ClassNode node) throws Exception { ClassNode serialized = new ClassNode(); new ClassReader(bytes(node)).accept(serialized, 0); for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method); }
}
