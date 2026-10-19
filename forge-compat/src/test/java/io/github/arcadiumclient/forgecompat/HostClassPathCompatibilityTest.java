package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostClassPathCompatibilityTest {
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostClassPathCompatibility";
    private static final String CLASS_PATH = "com/google/common/reflect/ClassPath";
    private static final String DESC = "(Ljava/lang/ClassLoader;)Lcom/google/common/reflect/ClassPath;";
    private static final String MANAGER = "journeymap/client/feature/FeatureManager";
    private static final String IMPL = "journeymap.client.feature.impl";
    private static final String UNLIMITED = IMPL + ".Unlimited";
    private LaunchClassLoader previous;
    @TempDir Path temporary;

    @BeforeEach void save() { previous = Launch.classLoader; Launch.classLoader = null; }
    @AfterEach void restore() { Launch.classLoader = previous; }

    @Test void reproducesTheActualFabric195DummyParentFailureAndLoadsThroughRealKnotAfterScanning() throws Exception {
        try (Runtime runtime = new Runtime(policyJar(true, false))) {
            assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClassLoader", runtime.host.getClass().getName());
            assertInstanceOf(URLClassLoader.class, runtime.host.getParent());
            assertThrows(ClassNotFoundException.class, () -> runtime.host.getParent().loadClass("java.lang.Object"));
            Object original = runtime.scan(false, runtime.host); Object rawInfo = infos(original).get(0);
            InvocationTargetException raw = assertThrows(InvocationTargetException.class, () -> rawInfo.getClass().getMethod("load").invoke(rawInfo));
            assertInstanceOf(NoClassDefFoundError.class, raw.getCause());
            Object corrected = runtime.scan(true, runtime.host); List<?> actual = infos(corrected); assertEquals(1, actual.size());
            Class<?> loaded = (Class<?>) actual.get(0).getClass().getMethod("load").invoke(actual.get(0));
            assertSame(runtime.host, loaded.getClassLoader()); assertSame(loaded, runtime.host.loadClass(UNLIMITED));
            assertSame(loaded, actual.get(0).getClass().getMethod("load").invoke(actual.get(0)));
            assertEquals(runtime.host.getResource(UNLIMITED.replace('.', '/') + ".class"), actual.get(0).getClass().getMethod("url").invoke(actual.get(0)));
            assertTrue(((Set<?>) corrected.getClass().getMethod("getResources").invoke(corrected)).stream().anyMatch(info -> info.toString().equals("policy-resource.txt")));
            try (var resource = runtime.host.getResourceAsStream("policy-resource.txt")) { assertEquals("policy-source 日本語 + 100%", new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
        }
    }

    @Test void preservesActualJourneyMapUnlimitedSelectionAndFairPlayRulesWhenNoImplementationIsPresent() throws Exception {
        for (boolean unlimited : List.of(true, false)) try (Runtime runtime = new Runtime(policyJar(unlimited, true))) {
            Class<?> manager = runtime.host.loadClass(MANAGER.replace('/', '.'));
            Object selected = manager.getMethod("selectFixturePolicy").invoke(null);
            Class<?> contract = runtime.host.loadClass((MANAGER + "$PolicySet").replace('/', '.')); assertTrue(contract.isInstance(selected));
            assertEquals(unlimited ? "Unlimited" : "FairPlay", contract.getMethod("getName").invoke(selected));
            Set<?> policies = (Set<?>) contract.getMethod("getPolicies").invoke(selected); assertEquals(5, policies.size());
            for (Object policy : policies) {
                assertSame(runtime.host, policy.getClass().getClassLoader());
                OwnGame.INSTANCE.server = null; assertEquals(unlimited, policy.getClass().getMethod("isCurrentlyAllowed").invoke(policy));
                OwnGame.INSTANCE.server = new OwnServer(false); assertEquals(true, policy.getClass().getMethod("isCurrentlyAllowed").invoke(policy));
                OwnGame.INSTANCE.server = new OwnServer(true); assertEquals(unlimited, policy.getClass().getMethod("isCurrentlyAllowed").invoke(policy));
            }
            if (unlimited) {
                assertEquals(UNLIMITED, selected.getClass().getName());
                assertThrows(UnsupportedOperationException.class, policies::clear);
            }
        } finally { OwnGame.INSTANCE.server = null; }
    }

    @Test void usesTheConfiguredFacadeAndHostOnlyAndKeepsOtherLoaderSemantics() throws Exception {
        try (Runtime runtime = new Runtime(policyJar(true, false))) {
            Object hostInfo = infos(runtime.scan(true, runtime.host)).get(0);
            Object facadeInfo = infos(runtime.scan(true, Launch.classLoader)).get(0);
            assertSame(hostInfo.getClass().getMethod("load").invoke(hostInfo), facadeInfo.getClass().getMethod("load").invoke(facadeInfo));
            LaunchClassLoader configured = Launch.classLoader; Launch.classLoader = null;
            Object raw = infos(runtime.scan(true, runtime.host)).get(0);
            assertThrows(InvocationTargetException.class, () -> raw.getClass().getMethod("load").invoke(raw)); Launch.classLoader = configured;
            try (URLClassLoader other = new URLClassLoader(new URL[]{runtime.input.toUri().toURL()}, getClass().getClassLoader())) {
                Object info = infos(runtime.scan(true, other)).get(0);
                Class<?> loaded = (Class<?>) info.getClass().getMethod("load").invoke(info); assertSame(other, loaded.getClassLoader());
            }
            InvocationTargetException missing = assertThrows(InvocationTargetException.class, () -> runtime.scan(true, null)); assertInstanceOf(NullPointerException.class, missing.getCause());
        }
    }

    @Test void rewritesExactlyTheStaticFactoryForGuestAndOfficialJarsWithoutChangingItsAbi() throws Exception {
        ClassNode source = new ClassNode(); source.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/ClassPathCaller", null, "java/lang/Object", null);
        MethodNode scan = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "scan", DESC, null, null);
        scan.visitVarInsn(Opcodes.ALOAD, 0); scan.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_PATH, "from", DESC, false); scan.visitInsn(Opcodes.ARETURN); source.methods.add(scan);
        MethodNode unchanged = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "identity", "(L" + CLASS_PATH + ";)Ljava/lang/String;", null, null);
        unchanged.visitVarInsn(Opcodes.ALOAD, 0); unchanged.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false); unchanged.visitInsn(Opcodes.ARETURN); source.methods.add(unchanged);
        Path input = jar(temporary.resolve("callsite.jar"), Map.of(source.name + ".class", bytes(source)));
        byte[] original = Files.readAllBytes(input);
        for (boolean official : List.of(false, true)) {
            Path output = new ForgeJarTransformer().transform(input, temporary.resolve("alias-cache"), mappings(), official, null);
            ClassNode result = node(entry(output, source.name + ".class")); MethodInsnNode call = calls(result.methods.get(0)).get(0);
            assertEquals(BRIDGE, call.owner); assertEquals("from", call.name); assertEquals(DESC, call.desc); assertEquals(Opcodes.INVOKESTATIC, call.getOpcode()); assertFalse(call.itf);
            assertEquals(scan.instructions.size(), result.methods.get(0).instructions.size());
            assertArrayEquals(methodBytes(method(node(entry(input, source.name + ".class")), "identity")), methodBytes(method(result, "identity")));
            for (MethodNode method : result.methods) new Analyzer<>(new BasicVerifier()).analyze(result.name, method);
        }
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void preservesTheActualJourneyMapPolicyDiscoveryMethodAndAllOriginalPolicyBytes() throws Exception {
        Path original = journeyMap(); byte[] before = Files.readAllBytes(original);
        Path output = new ForgeJarTransformer().transform(original, temporary.resolve("actual-cache"), mappings(), false, null);
        ClassNode source = node(entry(original, MANAGER + ".class")), result = node(entry(output, MANAGER + ".class"));
        assertEquals(1, calls(method(source, "locatePolicySet")).stream().filter(call -> call.owner.equals(CLASS_PATH) && call.name.equals("from")).count());
        for (MethodInsnNode call : calls(method(source, "locatePolicySet"))) {
            if (call.owner.equals(CLASS_PATH) && call.name.equals("from")) call.owner = BRIDGE;
            else if (call.owner.equals("java/lang/Class") && call.name.equals("forName")) call.owner = "io/github/arcadiumclient/forgecompat/reflect/ForgeReflection";
        }
        assertArrayEquals(methodBytes(method(source, "locatePolicySet")), methodBytes(method(result, "locatePolicySet")));
        assertArrayEquals(methodBytes(method(source, "createFairPlay")), methodBytes(method(result, "createFairPlay")));
        for (String name : List.of("impl/Unlimited", "Policy", "Feature", "FeatureManager$PolicySet", "FeatureManager$1")) {
            ClassNode expected = node(entry(original, "journeymap/client/feature/" + name + ".class")); ClassNode actual = node(entry(output, expected.name + ".class"));
            assertArrayEquals(bytes(expected), bytes(actual), name);
        }
        assertArrayEquals(before, Files.readAllBytes(original));
    }

    @Test void keepsTheGuavaResourceSetAndDelegatedClassIdentityAfterScanViewClosureAndRepeatedScans() throws Exception {
        try (Runtime runtime = new Runtime(policyJar(true, false))) {
            Set<?> original = (Set<?>) runtime.scan(false, runtime.host).getClass().getMethod("getResources").invoke(runtime.scan(false, runtime.host));
            Object first = runtime.scan(true, runtime.host), second = runtime.scan(true, runtime.host);
            Set<?> corrected = (Set<?>) first.getClass().getMethod("getResources").invoke(first);
            assertEquals(original.stream().map(Object::toString).sorted().toList(), corrected.stream().map(Object::toString).sorted().toList());
            for (Object resource : corrected) {
                String name = (String) resource.getClass().getMethod("getResourceName").invoke(resource);
                assertEquals(runtime.host.getResource(name), resource.getClass().getMethod("url").invoke(resource));
            }
            Object a = infos(first).get(0), b = infos(second).get(0);
            assertSame(a.getClass().getMethod("load").invoke(a), b.getClass().getMethod("load").invoke(b));
            assertFalse(((Set<?>) first.getClass().getMethod("getAllClasses").invoke(first)).stream().anyMatch(info -> info.toString().startsWith("net.minecraft.")));
        }
    }

    public static final class OwnGame {
        public static final OwnGame INSTANCE = new OwnGame(); OwnServer server;
        public OwnServer func_71401_C() { return server; }
    }
    public record OwnServer(boolean lan) { public boolean func_71344_c() { return lan; } }
    public interface OwnHelperAccess { OwnGame getClient(); }
    public static final class OwnHelper { public static final OwnHelperAccess INSTANCE = () -> OwnGame.INSTANCE; }

    private final class Runtime implements AutoCloseable {
        final LibraryLoader library; final ClassLoader host; final Path input; final LaunchClassLoader legacy;
        Runtime(Path input) throws Exception {
            this.input = input; library = new LibraryLoader();
            Class<?> type = library.loadClass("net.fabricmc.loader.impl.launch.knot.KnotClassLoader");
            Class<?> environment = library.loadClass("net.fabricmc.api.EnvType"), provider = library.loadClass("net.fabricmc.loader.impl.game.GameProvider");
            var constructor = type.getDeclaredConstructor(boolean.class, environment, provider); constructor.setAccessible(true);
            host = (ClassLoader) constructor.newInstance(false, environment.getField("CLIENT").get(null), null);
            var add = type.getDeclaredMethod("addUrlFwd", URL.class); add.setAccessible(true); add.invoke(host, input.toUri().toURL());
            FabricLauncher launcher = (FabricLauncher) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{FabricLauncher.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getTargetClassLoader")) return host;
                throw new AssertionError(method.getName());
            });
            legacy = new LaunchClassLoader(new URL[0], launcher, mappings()); Launch.classLoader = legacy;
        }
        Object scan(boolean compatible, ClassLoader loader) throws Exception {
            Class<?> factory = library.loadClass(compatible ? BRIDGE.replace('/', '.') : CLASS_PATH.replace('/', '.'));
            return factory.getMethod("from", ClassLoader.class).invoke(null, loader);
        }
        @Override public void close() throws Exception {
            legacy.close(); ((URLClassLoader) host.getParent()).close();
            JarURLConnection metadata = (JarURLConnection) new URL("jar:" + input.toUri() + "!/").openConnection();
            metadata.getJarFile().close();
            library.close(); Launch.classLoader = null;
        }
    }

    private static final class LibraryLoader extends URLClassLoader {
        LibraryLoader() throws Exception { super(new URL[]{cached("com.google.guava/guava/17.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/guava-17.0.jar").toUri().toURL(), cached("net.fabricmc/fabric-loader/0.19.5/ff9e65cffca4a67f31523e1807fe0855940fcbfa/fabric-loader-0.19.5.jar").toUri().toURL()}, HostClassPathCompatibilityTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    if (name.equals(BRIDGE.replace('/', '.')) || name.startsWith(BRIDGE.replace('/', '.') + "$")) {
                        try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) { byte[] bytes = input.readAllBytes(); type = defineClass(name, bytes, 0, bytes.length); }
                        catch (java.io.IOException exception) { throw new ClassNotFoundException(name, exception); }
                    } else if (name.startsWith("com.google.common.") || name.startsWith("net.fabricmc.loader.") || name.startsWith("net.fabricmc.api.")) type = findClass(name);
                    else type = super.loadClass(name, false);
                }
                if (resolve) resolveClass(type); return type;
            }
        }
    }

    private Path policyJar(boolean unlimited, boolean patchManager) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of("FeatureManager", "FeatureManager$PolicySet", "FeatureManager$1", "Policy", "Feature", "impl/Unlimited")) {
            if (!unlimited && name.equals("impl/Unlimited")) continue;
            ClassNode source = node(entry(journeyMap(), "journeymap/client/feature/" + name + ".class"));
            if (name.equals("FeatureManager")) {
                source.methods.removeIf(method -> !Set.of("locatePolicySet", "createFairPlay", "<clinit>").contains(method.name));
                MethodNode constructor = new MethodNode(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null); constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); constructor.visitInsn(Opcodes.RETURN); source.methods.add(constructor);
                MethodNode select = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "selectFixturePolicy", "()L" + MANAGER + "$PolicySet;", null, null);
                select.visitTypeInsn(Opcodes.NEW, MANAGER); select.visitInsn(Opcodes.DUP); select.visitMethodInsn(Opcodes.INVOKESPECIAL, MANAGER, "<init>", "()V", false); select.visitMethodInsn(Opcodes.INVOKESPECIAL, MANAGER, "locatePolicySet", "()L" + MANAGER + "$PolicySet;", false); select.visitInsn(Opcodes.ARETURN); source.methods.add(select);
                if (patchManager) for (MethodInsnNode call : calls(method(source, "locatePolicySet"))) if (call.owner.equals(CLASS_PATH) && call.name.equals("from")) call.owner = BRIDGE;
            }
            ClassNode fixture = new ClassNode(); source.accept(new ClassRemapper(fixture, new Remapper(Opcodes.ASM9) {
                @Override public String map(String type) { return switch (type) {
                    case "net/minecraft/client/Minecraft" -> Type.getInternalName(OwnGame.class);
                    case "net/minecraft/server/integrated/IntegratedServer" -> Type.getInternalName(OwnServer.class);
                    case "journeymap/client/forge/helper/ForgeHelper" -> Type.getInternalName(OwnHelper.class);
                    case "journeymap/client/forge/helper/IForgeHelper" -> Type.getInternalName(OwnHelperAccess.class);
                    default -> type;
                }; }
            })); files.put(fixture.name + ".class", bytes(fixture));
        }
        files.put("policy-resource.txt", "policy-source 日本語 + 100%".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return jar(temporary.resolve("policy 日本語 + 100%-" + unlimited + "-" + patchManager + ".jar"), files);
    }

    private static List<?> infos(Object path) throws Exception { return new ArrayList<>((Set<?>) path.getClass().getMethod("getTopLevelClasses", String.class).invoke(path, IMPL)); }
    private static Path cached(String name) { Path path = Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1", name); Assumptions.assumeTrue(Files.isRegularFile(path)); return path; }
    private static Path journeyMap() { Path path = ForgeTestFixtures.optionalGuest("journeymap-1.8.9-5.1.5-unlimited.jar").toAbsolutePath().normalize(); Assumptions.assumeTrue(Files.isRegularFile(path)); return path; }
    private static Path jar(Path path, Map<String, byte[]> entries) throws Exception { try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) { for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); } } return path; }
    private static byte[] entry(Path path, String name) throws Exception { try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { return input.readAllBytes(); } }
    private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static List<MethodInsnNode> calls(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode).map(MethodInsnNode.class::cast).toList(); }
    private static byte[] methodBytes(MethodNode method) { ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Method", null, "java/lang/Object", null); method.accept(writer); writer.visitEnd(); return writer.toByteArray(); }
    private static ForgeMappings mappings() throws Exception { return new ForgeMappings(new MappingResolver() {
        @Override public List<String> getNamespaces() { return List.of("official", "intermediary"); }
        @Override public String getCurrentRuntimeNamespace() { return "intermediary"; }
        @Override public String mapClassName(String namespace, String name) { return name; }
        @Override public String unmapClassName(String namespace, String name) { return name; }
        @Override public String mapMethodName(String namespace, String owner, String name, String descriptor) { return name; }
        @Override public String mapFieldName(String namespace, String owner, String name, String descriptor) { return name; }
    }, new StringReader("CL: fixture/Placeholder net/minecraft/Placeholder\n")); }
}
