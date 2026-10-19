package net.minecraft.launchwrapper;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LaunchClassLoaderTest {
    private static final String SRG = "net.minecraft.test.Target";
    private static final String RUNTIME = "fixture.runtime.Target";
    @TempDir Path temporary;
    private MockLauncher host;
    private ForgeMappings mappings;
    private LaunchClassLoader previousLoader;
    private File previousGame;
    private File previousAssets;
    private Map<String, Object> previousBlackboard;

    @BeforeEach void setup() throws Exception {
        previousLoader = Launch.classLoader;
        previousGame = Launch.minecraftHome;
        previousAssets = Launch.assetsDir;
        previousBlackboard = Launch.blackboard;
        Launch.classLoader = null;
        Launch.minecraftHome = null;
        Launch.assetsDir = null;
        Launch.blackboard = new HashMap<>();
        host = new MockLauncher();
        mappings = new ForgeMappings(new MockResolver(), new StringReader("""
            CL: a net/minecraft/test/Target
            CL: b net/minecraft/test/Other
            FD: a/x net/minecraft/test/Target/field_1
            MD: a/a (La;)Lb; net/minecraft/test/Target/func_1 (Lnet/minecraft/test/Target;)Lnet/minecraft/test/Other;
            """));
        host.classes.put(RUNTIME, TargetFixture.class);
        host.bytes.put(RUNTIME, runtimeBytes(RUNTIME.replace('.', '/')));
        GuestTransformer.calls = 0;
    }

    @AfterEach void teardown() throws Exception {
        if (Launch.classLoader != null && Launch.classLoader != previousLoader) Launch.classLoader.close();
        Launch.classLoader = previousLoader;
        Launch.minecraftHome = previousGame;
        Launch.assetsDir = previousAssets;
        Launch.blackboard = previousBlackboard;
        host.target.close();
    }

    private LaunchClassLoader loader() {
        return new LaunchClassLoader(new URL[0], host, mappings);
    }

    private Path jar(String filename, Map<String, byte[]> contents) throws IOException {
        Path file = temporary.resolve(filename);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : contents.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return file;
    }

    private static byte[] runtimeBytes(String internal) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "fieldRuntime", "Lfixture/runtime/Target;", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "methodRuntime",
            "(Lfixture/runtime/Target;)Lfixture/runtime/Other;", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test void preservesTheExactPublicLegacyInterfacesAndStaticFieldDescriptors() throws Exception {
        assertEquals(4, ITweaker.class.getDeclaredMethods().length);
        assertEquals(void.class, ITweaker.class.getMethod("acceptOptions", List.class, File.class, File.class, String.class).getReturnType());
        assertEquals(void.class, ITweaker.class.getMethod("injectIntoClassLoader", LaunchClassLoader.class).getReturnType());
        assertEquals(String.class, ITweaker.class.getMethod("getLaunchTarget").getReturnType());
        assertEquals(String[].class, ITweaker.class.getMethod("getLaunchArguments").getReturnType());
        assertEquals(1, IClassTransformer.class.getDeclaredMethods().length);
        assertEquals(byte[].class, IClassTransformer.class.getMethod("transform", String.class, String.class, byte[].class).getReturnType());
        assertEquals(2, IClassNameTransformer.class.getDeclaredMethods().length);
        assertFalse(IClassTransformer.class.isAssignableFrom(IClassNameTransformer.class));
        for (var entry : Map.of("minecraftHome", File.class, "assetsDir", File.class,
                "blackboard", Map.class, "classLoader", LaunchClassLoader.class).entrySet()) {
            var field = Launch.class.getField(entry.getKey());
            assertEquals(entry.getValue(), field.getType());
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertFalse(Modifier.isFinal(field.getModifiers()));
        }
        assertEquals(4096, LaunchClassLoader.BUFFER_SIZE);
        assertThrows(UnsupportedOperationException.class, () -> Launch.main(new String[0]));
    }

    @Test void delegatesSrgOfficialAndRuntimeNamesToTheSingleHostClassLoader() throws Exception {
        try (var loader = loader()) {
            assertInstanceOf(URLClassLoader.class, loader);
            assertSame(host.target, loader.getParent());
            assertSame(TargetFixture.class, loader.loadClass(SRG));
            assertSame(TargetFixture.class, loader.findClass("a"));
            assertSame(TargetFixture.class, loader.loadClass(RUNTIME));
            assertEquals(List.of(RUNTIME, RUNTIME, RUNTIME), host.loaded);
            assertNotSame(loader, TargetFixture.class.getClassLoader());
            assertEquals(0, GuestTransformer.calls);
        }
    }

    @Test void refusesToDefineAClassEvenWhenItsBytesExistInItsOwnUrlSources() throws Exception {
        Path jar = jar("guest.jar", Map.of("guest/Ghost.class", runtimeBytes("guest/Ghost")));
        try (var loader = loader()) {
            loader.addURL(jar.toUri().toURL());
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("guest.Ghost"));
            assertThrows(ClassNotFoundException.class, () -> loader.findClass("guest.Ghost"));
            assertEquals(List.of("guest.Ghost"), host.loaded);
        }
    }

    @Test void addsRealLocalClasspathEntriesToKnotAndReportsNormalizedImmutableSources() throws Exception {
        Path jar = jar("guest with spaces.jar", Map.of("data.txt", new byte[]{42}));
        try (var loader = loader()) {
            loader.addURL(jar.toUri().toURL());
            loader.addURL(new URL("jar:" + jar.toUri().toURL() + "!/"));
            assertEquals(List.of(jar.toRealPath()), host.added);
            assertEquals(List.of(jar.toRealPath().toUri().toURL()), loader.getSources());
            assertArrayEquals(loader.getSources().toArray(URL[]::new), loader.getURLs());
            assertThrows(UnsupportedOperationException.class, () -> loader.getSources().clear());
            try (var input = loader.getResourceAsStream("data.txt")) {
                assertNotNull(input);
                assertEquals(42, input.read());
            }
            assertEquals(loader.getResource("data.txt"), loader.findResource("data.txt"));
            assertTrue(loader.getResources("data.txt").hasMoreElements());
        }
    }

    @Test void rejectsUnsupportedUrlsAndMissingLocalSourcesInsteadOfIgnoringThem() throws Exception {
        try (var loader = loader()) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                () -> loader.addURL(new URL("https://example.invalid/mod.jar"))).getMessage().contains("local"));
            Path jar = jar("valid.jar", Map.of());
            assertThrows(IllegalArgumentException.class,
                () -> loader.addURL(new URL("jar:" + jar.toUri().toURL() + "!/nested/")));
            assertThrows(IllegalArgumentException.class, () -> loader.addURL(temporary.resolve("missing.jar").toUri().toURL()));
            assertTrue(host.added.isEmpty());
        }
    }

    @Test void reusesAnExistingHostClasspathFileThroughAHardLinkWithoutMutatingTheFixedHost() throws Exception {
        Path source = jar("original.jar", Map.of("data.txt", new byte[]{42}));
        Path alias = Files.createLink(temporary.resolve("alias.jar"), source);
        assertTrue(Files.isSameFile(source, alias));
        assertNotEquals(source.toRealPath(), alias.toAbsolutePath().normalize());
        host.paths.add(alias); host.target.add(alias); host.fixed = true;
        try (var loader = new LaunchClassLoader(new URL[]{source.toUri().toURL()}, host, mappings)) {
            assertTrue(host.added.isEmpty()); assertEquals(List.of(alias), host.paths);
            assertEquals(List.of(source.toRealPath().toUri().toURL()), loader.getSources());
            loader.addURL(alias.toUri().toURL());
            assertEquals(1, loader.getSources().size()); assertEquals(1, loader.getURLs().length);
            try (var input = loader.getResourceAsStream("data.txt")) { assertNotNull(input); assertEquals(42, input.read()); }
        }
    }

    @Test void ignoresMissingUnrelatedHostEntriesAndStillAddsASeparateFileWithIdenticalBytes() throws Exception {
        Path source = jar("existing.jar", Map.of("data.txt", new byte[]{42}));
        Path alias = Files.createLink(temporary.resolve("existing-alias.jar"), source);
        Path separate = Files.copy(source, temporary.resolve("separate.jar"));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(separate));
        assertFalse(Files.isSameFile(source, separate));
        host.paths.add(temporary.resolve("missing.jar")); host.paths.add(alias); host.target.add(alias);
        try (var loader = loader()) {
            loader.addURL(source.toUri().toURL()); assertTrue(host.added.isEmpty());
            loader.addURL(separate.toUri().toURL());
            assertEquals(List.of(separate.toRealPath()), host.added);
            assertEquals(List.of(source.toRealPath().toUri().toURL(), separate.toRealPath().toUri().toURL()), loader.getSources());
        }
    }

    @Test void readsRawHostBytesAsSrgWithoutLoadingClassesOrInvokingTransformers() throws Exception {
        try (var loader = loader()) {
            loader.registerTransformer(GuestTransformer.class.getName());
            host.loaded.clear();
            byte[] bytes = loader.getClassBytes(SRG);
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            assertEquals("net/minecraft/test/Target", node.name);
            assertEquals("field_1", node.fields.get(0).name);
            assertEquals("Lnet/minecraft/test/Target;", node.fields.get(0).desc);
            assertEquals("func_1", node.methods.get(0).name);
            assertEquals("(Lnet/minecraft/test/Target;)Lnet/minecraft/test/Other;", node.methods.get(0).desc);
            assertTrue(host.loaded.isEmpty());
            assertEquals(0, GuestTransformer.calls);
            assertEquals(List.of(false), host.transformFlags);
            bytes[0] = 0;
            assertEquals((byte) 0xCA, loader.getClassBytes(RUNTIME)[0]);
            assertEquals(1, host.reads);
        }
    }

    @Test void cachesMissesAndClearsThemUsingEitherNamespaceOrNewClasspathEntries() throws Exception {
        try (var loader = loader()) {
            host.bytes.remove(RUNTIME);
            assertNull(loader.getClassBytes(SRG));
            host.bytes.put(RUNTIME, runtimeBytes(RUNTIME.replace('.', '/')));
            assertNull(loader.getClassBytes(RUNTIME));
            assertEquals(1, host.reads);
            loader.clearNegativeEntries(Set.of("net/minecraft/test/Target"));
            assertNotNull(loader.getClassBytes(SRG));
            assertEquals(2, host.reads);
            assertNull(loader.getClassBytes("guest.Missing"));
            host.bytes.put("guest.Missing", runtimeBytes("guest/Missing"));
            loader.addURL(temporary.toUri().toURL());
            assertNotNull(loader.getClassBytes("guest.Missing"));
        }
    }

    @Test void storesRealGuestTransformersForTheHostPipelineAndNeverFiltersByNameSubstring() throws Exception {
        try (var loader = loader()) {
            loader.registerTransformer(GuestPatchingTransformer.class.getName());
            assertEquals(1, loader.getTransformers().size());
            assertInstanceOf(GuestPatchingTransformer.class, loader.getTransformers().get(0));
            assertEquals(0, GuestTransformer.calls);
            byte[] bytes = loader.getClassBytes(SRG);
            for (IClassTransformer transformer : loader.getTransformers()) bytes = transformer.transform("a", SRG, bytes);
            ClassNode transformed = new ClassNode();
            new ClassReader(bytes).accept(transformed, 0);
            assertTrue(transformed.fields.stream().anyMatch(field -> field.name.equals("guestMarker")));
            assertEquals(1, GuestTransformer.calls);
            assertThrows(UnsupportedOperationException.class, () -> loader.getTransformers().clear());
        }
    }

    @Test void explicitlyRecordsOnlyTheTwoForgeTransformersAlreadyAppliedByTheHost() throws Exception {
        try (var loader = loader()) {
            for (String name : LaunchClassLoader.getHostHandledTransformers()) loader.registerTransformer(name);
            assertEquals(Set.of("net.minecraftforge.fml.common.asm.transformers.PatchingTransformer",
                "net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer"), loader.getHandledTransformers());
            assertTrue(loader.getTransformers().isEmpty());
            assertTrue(host.loaded.isEmpty());
        }
    }

    @Test void registrationFailuresAreConcreteAndDoNotLeavePlaceholderTransformers() throws Exception {
        try (var loader = loader()) {
            assertThrows(IllegalStateException.class, () -> loader.registerTransformer("guest.MissingTransformer"));
            assertThrows(IllegalArgumentException.class, () -> loader.registerTransformer(String.class.getName()));
            assertTrue(assertThrows(IllegalStateException.class,
                () -> loader.registerTransformer(BrokenTransformer.class.getName())).getMessage().contains("BrokenTransformer"));
            assertTrue(loader.getTransformers().isEmpty());
        }
    }

    @Test void honorsNameTransformersAndExclusionsAcrossRuntimeAndSrgNames() throws Exception {
        try (var loader = loader()) {
            loader.registerTransformer(NameTransformer.class.getName());
            assertInstanceOf(NameTransformer.class, loader.getNameTransformer());
            assertSame(TargetFixture.class, loader.loadClass("a"));
            assertNotNull(loader.getClassBytes(SRG));
            loader.addTransformerExclusion("net/minecraft/test/");
            assertTrue(loader.isTransformationExcluded(RUNTIME));
            assertTrue(loader.isTransformationExcluded(SRG));
            assertTrue(loader.isTransformationExcluded("javax.crypto.Cipher"));
            assertTrue(loader.isTransformationExcluded("java.lang.String"));
            assertFalse(loader.isTransformationExcluded("guest.RegularMod"));
            loader.addClassLoaderExclusion("guest.private.");
            assertTrue(loader.isTransformationExcluded("guest.private.Helper"));
            assertThrows(IllegalArgumentException.class, () -> loader.addTransformerExclusion("*"));
        }
    }

    @Test void keepsReflectiveLegacyCachesTiedToHostLoadsAndHonorsInvalidClassVetoes() throws Exception {
        try (var loader = loader()) {
            assertSame(TargetFixture.class, loader.loadClass(SRG));
            var cached = LaunchClassLoader.class.getDeclaredField("cachedClasses");
            cached.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, Class<?>> classes = (Map<String, Class<?>>) cached.get(loader);
            assertSame(TargetFixture.class, classes.get(SRG));
            assertSame(TargetFixture.class, classes.get(RUNTIME));
            classes.put(SRG, String.class);
            assertSame(TargetFixture.class, loader.loadClass(SRG));
            var invalid = LaunchClassLoader.class.getDeclaredField("invalidClasses");
            invalid.setAccessible(true);
            @SuppressWarnings("unchecked") Set<String> vetoed = (Set<String>) invalid.get(loader);
            vetoed.add(SRG);
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(RUNTIME));
            loader.clearNegativeEntries(Set.of(RUNTIME));
            assertSame(TargetFixture.class, loader.loadClass(SRG));
        }
    }

    @Test void definesGenuineGuestHelpersWithRuntimeReferencesAndKeepsMinecraftInKnot() throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "guest/Generated", null, "java/lang/Object", null);
        var message = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "message", "()Ljava/lang/String;", null, null);
        message.visitCode();
        message.visitLdcInsn("helper-ok");
        message.visitInsn(Opcodes.ARETURN);
        message.visitMaxs(1, 0);
        message.visitEnd();
        var reference = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "unusedMinecraftReference", "()Ljava/lang/Class;", null, null);
        reference.visitCode();
        reference.visitLdcInsn(Type.getObjectType(SRG.replace('.', '/')));
        reference.visitInsn(Opcodes.ARETURN);
        reference.visitMaxs(1, 0);
        reference.visitEnd();
        writer.visitEnd();
        byte[] srgBytes = writer.toByteArray();
        try (var loader = loader()) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("guest.Generated"));
            assertNull(loader.getClassBytes("guest.Generated"));
            Class<?> generated = loader.defineLegacyHelperClass("guest.Generated", srgBytes);
            assertSame(loader, generated.getClassLoader());
            assertSame(generated, loader.loadClass("guest.Generated"));
            assertSame(generated, loader.defineLegacyHelperClass("guest.Generated", srgBytes));
            assertEquals("helper-ok", generated.getMethod("message").invoke(null));
            ClassNode runtime = new ClassNode();
            try (var input = loader.getResourceAsStream("guest/Generated.class")) {
                assertNotNull(input);
                new ClassReader(input.readAllBytes()).accept(runtime, 0);
            }
            assertEquals(RUNTIME.replace('.', '/'),
                ((Type) ((LdcInsnNode) runtime.methods.get(1).instructions.get(0)).cst).getInternalName());
            ClassNode legacy = new ClassNode();
            new ClassReader(loader.getClassBytes("guest.Generated")).accept(legacy, 0);
            assertEquals(SRG.replace('.', '/'),
                ((Type) ((LdcInsnNode) legacy.methods.get(1).instructions.get(0)).cst).getInternalName());
            assertFalse(host.loaded.contains(RUNTIME));
            assertThrows(IllegalArgumentException.class, () -> loader.defineLegacyHelperClass(SRG, host.bytes.get(RUNTIME)));
            assertThrows(IllegalArgumentException.class, () -> loader.defineLegacyHelperClass("a", host.bytes.get(RUNTIME)));
            assertThrows(IllegalArgumentException.class, () -> loader.defineLegacyHelperClass(RUNTIME, host.bytes.get(RUNTIME)));
        }
    }

    @Test void reusesAlreadyHostAvailableGuestClassesInsteadOfDefiningCopies() throws Exception {
        try (var loader = loader()) {
            host.classes.put("guest.Existing", TargetFixture.class);
            assertSame(TargetFixture.class, loader.defineLegacyHelperClass("guest.Existing", runtimeBytes("guest/Existing")));
            assertNotSame(loader, loader.loadClass("guest.Existing").getClassLoader());
            assertThrows(IllegalArgumentException.class, () -> loader.defineLegacyHelperClass("guest.Different", runtimeBytes("wrong/Name")));
        }
    }

    @Test void exposesRawClassReadFailuresAndRejectsCorruptOrMismatchedClassBytes() throws Exception {
        try (var loader = loader()) {
            host.early = true;
            assertTrue(assertThrows(IOException.class, () -> loader.getClassBytes(SRG)).getMessage().contains("Knot unlocks"));
            host.early = false;
            host.bytes.put(RUNTIME, new byte[]{0, 1, 2});
            assertTrue(assertThrows(IOException.class, () -> loader.getClassBytes(SRG)).getMessage().contains("SRG"));
            host.bytes.put(RUNTIME, runtimeBytes("wrong/Owner"));
            assertTrue(assertThrows(IOException.class, () -> loader.getClassBytes(SRG)).getMessage().contains("different class"));
        }
    }

    @Test void configuresSharedLaunchStateWithTheRealAssetsPathAndPreservesItOnRepeatCalls() throws Exception {
        Path jar = jar("prepared-runtime.jar", Map.of());
        Path assets = temporary.resolve("shared-assets");
        Launch.configure(host, mappings, temporary, assets, List.of(jar));
        assertEquals(temporary.toFile(), Launch.minecraftHome);
        assertEquals(assets.toFile(), Launch.assetsDir);
        assertSame(host.target, Launch.classLoader.getParent());
        assertInstanceOf(List.class, Launch.blackboard.get("TweakClasses"));
        assertInstanceOf(List.class, Launch.blackboard.get("Tweaks"));
        assertInstanceOf(List.class, Launch.blackboard.get("ArgumentList"));
        Launch.blackboard.put("guestValue", 42);
        LaunchClassLoader configured = Launch.classLoader;
        Launch.configure(host, mappings, temporary, assets, List.of(jar));
        assertSame(configured, Launch.classLoader);
        assertEquals(42, Launch.blackboard.get("guestValue"));
        assertEquals(1, host.added.size());
        assertThrows(IllegalStateException.class,
            () -> Launch.configure(host, mappings, temporary.resolve("other"), assets, List.of()));
        try (var legacy = new LaunchClassLoader(new URL[0])) {
            assertSame(TargetFixture.class, legacy.loadClass(SRG));
        }
    }

    @Test void legacyConstructionRequiresConfigurationAndFourArgumentConfigureUsesTheStandardAssetsFolder() throws Exception {
        assertThrows(IllegalStateException.class, () -> new LaunchClassLoader(new URL[0]));
        Launch.configure(host, mappings, temporary, List.of());
        assertEquals(temporary.resolve("assets").toFile(), Launch.assetsDir);
    }

    public static final class TargetFixture {}

    public static class GuestTransformer implements IClassTransformer {
        static int calls;
        @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
            calls++;
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "guestMarker", "I", null, null));
            ClassWriter output = new ClassWriter(0);
            node.accept(output);
            return output.toByteArray();
        }
    }

    public static final class GuestPatchingTransformer extends GuestTransformer {}

    public static final class NameTransformer extends GuestTransformer implements IClassNameTransformer {
        @Override public String unmapClassName(String name) { return name.equals(SRG) ? "a" : name; }
        @Override public String remapClassName(String name) { return name.equals("a") ? SRG : name; }
    }

    public static final class BrokenTransformer extends GuestTransformer {
        public BrokenTransformer() { throw new IllegalStateException("Synthetic transformer constructor failed"); }
    }

    private static final class MockResolver implements MappingResolver {
        @Override public Collection<String> getNamespaces() { return Set.of("official", "intermediary"); }
        @Override public String getCurrentRuntimeNamespace() { return "intermediary"; }
        @Override public String mapClassName(String namespace, String name) {
            return switch (name) { case "a" -> RUNTIME; case "b" -> "fixture.runtime.Other"; default -> name; };
        }
        @Override public String unmapClassName(String namespace, String name) {
            return switch (name) { case RUNTIME -> "a"; case "fixture.runtime.Other" -> "b"; default -> name; };
        }
        @Override public String mapFieldName(String namespace, String owner, String name, String descriptor) {
            return owner.equals("a") && name.equals("x") ? "fieldRuntime" : name;
        }
        @Override public String mapMethodName(String namespace, String owner, String name, String descriptor) {
            return owner.equals("a") && name.equals("a") ? "methodRuntime" : name;
        }
    }

    private static final class TargetLoader extends URLClassLoader {
        TargetLoader() { super(new URL[0], LaunchClassLoaderTest.class.getClassLoader()); }
        void add(Path path) throws IOException { super.addURL(path.toUri().toURL()); }
    }

    private static final class MockLauncher implements FabricLauncher {
        final TargetLoader target = new TargetLoader();
        final List<Path> paths = new ArrayList<>();
        final List<Path> added = new ArrayList<>();
        final List<String> loaded = new ArrayList<>();
        final List<Boolean> transformFlags = new ArrayList<>();
        final Map<String, Class<?>> classes = new HashMap<>();
        final Map<String, byte[]> bytes = new HashMap<>();
        int reads;
        boolean early, fixed;
        @Override public void addToClassPath(Path path, String... prefixes) {
            if (fixed) throw new UnsupportedOperationException("The trusted probe classpath is fixed");
            paths.add(path);
            added.add(path);
            try { target.add(path); } catch (IOException cause) { throw new IllegalArgumentException(cause); }
        }
        @Override public ClassLoader getTargetClassLoader() { return target; }
        @Override public List<Path> getClassPath() { return List.copyOf(paths); }
        @Override public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
            loaded.add(name);
            if (classes.containsKey(name)) return classes.get(name);
            if (name.startsWith("guest.") || name.startsWith("fixture.runtime.")) throw new ClassNotFoundException(name);
            return LaunchClassLoaderTest.class.getClassLoader().loadClass(name);
        }
        @Override public byte[] getClassByteArray(String name, boolean runTransformers) {
            if (early) throw new IllegalStateException("early getClassByteArray access");
            reads++;
            transformFlags.add(runTransformers);
            return bytes.get(name);
        }
        @Override public InputStream getResourceAsStream(String name) { return target.getResourceAsStream(name); }
        @Override public boolean isClassLoaded(String name) { return classes.containsKey(name); }
        @Override public EnvType getEnvironmentType() { return EnvType.CLIENT; }
        @Override public boolean isDevelopment() { return false; }
        @Override public String getEntrypoint() { throw new UnsupportedOperationException("Unused fixture entrypoint"); }
        @Override public MappingConfiguration getMappingConfiguration() { throw new UnsupportedOperationException("Unused fixture mapping configuration"); }
        @Override public Manifest getManifest(Path origin) { throw new UnsupportedOperationException("Unused fixture manifest"); }
        @Override public void setAllowedPrefixes(Path path, String... prefixes) { throw new UnsupportedOperationException("Unused fixture prefix policy"); }
        @Override public void setValidParentClassPath(Collection<Path> paths) { throw new UnsupportedOperationException("Unused fixture parent policy"); }
    }
}
