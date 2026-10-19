package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostRegistrySnapshotCompatibilityTest {
    private static final String FML = "net/minecraftforge/fml/common/registry/FMLControlledNamespacedRegistry";
    private static final String WRAPPED = "net/ornithemc/osl/registries/impl/registry/WrappedIdRegistry";
    private static final String REGISTRIES = "net/ornithemc/osl/registries/api/registry/Registries";
    private static final String OWN_FML = "io/github/arcadiumclient/forgecompat/fixture/SnapshotRegistry";
    private static final String OWN_WRAPPED = "io/github/arcadiumclient/forgecompat/fixture/SnapshotWrapper";
    @TempDir Path temporary;

    public record OwnKey(String name) { }
    public interface OwnFrozen { OwnFrozen freeze(); }
    public interface OwnCallback { void registered(int id, Object key, Object value) throws Throwable; }
    public static final class OwnCatalog { public static final List<Object> REGISTRY = new ArrayList<>(); }
    public static final class OwnIds {
        public final Map<Object, Integer> ids = new IdentityHashMap<>();
        public void put(Object value, int id) { ids.put(value, id); }
    }
    public static class OwnSimple { public final Map<OwnKey, Object> values = new LinkedHashMap<>(); }
    public static class OwnBase extends OwnSimple {
        public OwnIds intMap = new OwnIds();
        public int nextId, callbacks, fmlCallbacks, clears;
        public OwnCallback callback;
        public RuntimeException failure;
        public void raw(int id, OwnKey key, Object value) throws Throwable {
            intMap.put(value, id); values.put(key, value); nextId = Math.max(nextId, id + 1); callbacks++;
            if (callback != null) callback.registered(id, key, value);
            fmlCallbacks++;
            if (failure != null) throw failure;
        }
        public void clearOsl() { values.clear(); intMap.ids.clear(); nextId = 0; clears++; }
    }
    public static class OwnFml extends OwnBase {
        public Class<?> superType = Object.class;
        public void set(OwnFml source) throws Throwable {
            if (superType != source.superType) throw new IllegalArgumentException("incompatible registry");
            intMap = new OwnIds();
            values.clear();
            for (Map.Entry<OwnKey, Object> entry : source.values.entrySet()) raw(source.intMap.ids.get(entry.getValue()), entry.getKey(), entry.getValue());
        }
    }
    public static class OwnWrapper implements OwnFrozen {
        public final OwnBase registry;
        private boolean frozen;
        private int nextId;
        public OwnWrapper(OwnBase registry) { this.registry = registry; registry.callback = this::valueRegistered; }
        private void valueRegistered(int id, Object key, Object value) { throw new AssertionError("Actual OSL callback bytecode was not installed"); }
        public void clear() { throw new AssertionError("Actual OSL clear bytecode was not installed"); }
        @Override public OwnFrozen freeze() { throw new AssertionError("Actual OSL freeze bytecode was not installed"); }
        public boolean frozen() { return frozen; }
        public int nextId() { return nextId; }
    }
    private record Fixture(Class<?> registryType, Class<?> wrapperType) {
        OwnBase registry() throws Exception { return (OwnBase) registryType.getConstructor().newInstance(); }
        Object wrap(OwnBase registry) throws Exception { Object wrapper = wrapperType.getConstructor(OwnBase.class).newInstance(registry); OwnCatalog.REGISTRY.add(wrapper); return wrapper; }
        void set(OwnBase registry, OwnBase source) throws Throwable { try { registryType.getMethod("set", registryType).invoke(registry, source); } catch (InvocationTargetException exception) { throw exception.getCause(); } }
        boolean frozen(Object wrapper) throws Exception { return (boolean) wrapperType.getMethod("frozen").invoke(wrapper); }
        int next(Object wrapper) throws Exception { return (int) wrapperType.getMethod("nextId").invoke(wrapper); }
        void freeze(Object wrapper) throws Exception { wrapperType.getMethod("freeze").invoke(wrapper); }
    }

    @BeforeEach void before() { OwnCatalog.REGISTRY.clear(); }
    @AfterEach void after() { OwnCatalog.REGISTRY.clear(); }

    @Test void reproducesTheFreezeFailureUsingTheActualOslCallbackBytecode() throws Throwable {
        Fixture fixture = fixture(false); OwnBase registry = fixture.registry(), source = fixture.registry();
        Object air = new Object(); OwnKey key = new OwnKey("minecraft:air"); registry.raw(0, key, air); source.raw(0, key, air);
        Object wrapper = fixture.wrap(registry); fixture.freeze(wrapper);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.set(registry, source));
        assertEquals("registry is frozen!", failure.getMessage()); assertTrue(fixture.frozen(wrapper));
    }

    @Test void remapsAndRestoresIdsWithActualOslLifecycleCodeAndFreezesOnlyTheTarget() throws Throwable {
        Fixture fixture = fixture(true); OwnBase registry = fixture.registry(), original = fixture.registry(), remapped = fixture.registry(), unrelated = fixture.registry();
        Object air = new Object(), stone = new Object(); OwnKey a = new OwnKey("minecraft:air"), s = new OwnKey("minecraft:stone");
        original.raw(0, a, air); original.raw(1, s, stone); remapped.raw(3, a, air); remapped.raw(9, s, stone);
        registry.raw(0, a, air); registry.raw(1, s, stone);
        Object wrapper = fixture.wrap(registry), other = fixture.wrap(unrelated); fixture.freeze(wrapper); fixture.freeze(other);
        int callbacks = registry.callbacks, fml = registry.fmlCallbacks;
        fixture.set(registry, remapped);
        assertEquals(Map.of(a, air, s, stone), registry.values); assertEquals(3, registry.intMap.ids.get(air)); assertEquals(9, registry.intMap.ids.get(stone));
        assertEquals(10, registry.nextId); assertEquals(10, fixture.next(wrapper)); assertTrue(fixture.frozen(wrapper)); assertTrue(fixture.frozen(other));
        assertEquals(1, registry.clears); assertEquals(0, unrelated.clears); assertEquals(callbacks + 2, registry.callbacks); assertEquals(fml + 2, registry.fmlCallbacks);
        assertThrows(IllegalStateException.class, () -> registry.callback.registered(11, new OwnKey("late:value"), new Object()));
        fixture.set(registry, original);
        assertEquals(0, registry.intMap.ids.get(air)); assertEquals(1, registry.intMap.ids.get(stone));
        assertEquals(2, registry.nextId); assertEquals(2, fixture.next(wrapper)); assertTrue(fixture.frozen(wrapper));
        assertEquals(2, registry.clears); assertEquals(callbacks + 4, registry.callbacks); assertEquals(fml + 4, registry.fmlCallbacks);
    }

    @Test void refreezesOnFailureWithoutWrappingTheOriginalExceptionAndPreservesUnfrozenRegistries() throws Throwable {
        Fixture fixture = fixture(true); OwnBase source = fixture.registry(); source.raw(4, new OwnKey("fixture:entry"), new Object());
        for (boolean frozen : List.of(true, false)) {
            OwnBase target = fixture.registry(); Object wrapper = fixture.wrap(target); if (frozen) fixture.freeze(wrapper);
            RuntimeException failure = new IllegalArgumentException("Owned callback failure"); target.failure = failure;
            assertSame(failure, assertThrows(RuntimeException.class, () -> fixture.set(target, source)));
            assertEquals(frozen, fixture.frozen(wrapper)); assertEquals(1, target.clears); assertEquals(1, target.callbacks); assertEquals(1, target.fmlCallbacks);
        }
        OwnBase noWrapper = fixture.registry(); fixture.set(noWrapper, source); assertEquals(0, noWrapper.clears); assertEquals(1, noWrapper.callbacks);
    }

    @Test void validatesTheOriginalRegistryTypeBeforeClearingAndLeavesAnUnfrozenSuccessUnfrozen() throws Throwable {
        Fixture fixture = fixture(true); OwnBase target = fixture.registry(), source = fixture.registry(); Object wrapper = fixture.wrap(target); fixture.freeze(wrapper);
        fixture.registryType.getField("superType").set(source, String.class);
        assertThrows(IllegalArgumentException.class, () -> fixture.set(target, source)); assertEquals(0, target.clears); assertTrue(fixture.frozen(wrapper));
        OwnBase open = fixture.registry(), valid = fixture.registry(); Object openWrapper = fixture.wrap(open);
        valid.raw(6, new OwnKey("fixture:entry"), new Object()); fixture.set(open, valid); assertFalse(fixture.frozen(openWrapper)); assertEquals(7, fixture.next(openWrapper));
    }

    @Test void documentsTheSeparateNormalForgeRegistrationLifecycleWithoutBypassingItsFreeze() throws Throwable {
        Path forge = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        ClassNode gameRegistry = read(forge, "net/minecraftforge/fml/common/registry/GameRegistry.class");
        ClassNode data = read(forge, "net/minecraftforge/fml/common/registry/GameData.class");
        for (String name : List.of("registerBlock", "registerItem")) {
            assertTrue(gameRegistry.methods.stream().filter(method -> method.name.equals(name)).flatMap(method -> real(method).stream())
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(data.name) && call.name.equals(name)));
            assertTrue(real(method(data, name)).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(FML) && call.name.equals("add")));
        }
        MethodNode add = method(read(forge, FML + ".class"), "add");
        assertTrue(real(add).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("addObjectRaw")));
        assertFalse(real(add).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("set")));
        Fixture fixture = fixture(true); OwnBase registry = fixture.registry(); Object wrapper = fixture.wrap(registry); fixture.freeze(wrapper);
        assertThrows(IllegalStateException.class, () -> registry.raw(17, new OwnKey("fixture:late_registration"), new Object()));
        assertTrue(fixture.frozen(wrapper)); assertEquals(0, registry.clears);
        System.out.println("Remaining lifecycle audit: normal GameRegistry add bypasses set and stays blocked after OSL freeze; snapshot compatibility does not grant late registration");
    }

    @Test void normalizesTheActualForgeSnapshotAndMatchesOslsOwnRemapLifecycleWithoutChangingOtherMethods() throws Exception {
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Path forge = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        ForgeMappings mappings;
        try (var input = Files.newBufferedReader(Path.of("../build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings(resolver(Files.readString(client.resolveSibling("calamus-gen2-intermediary-v2.tiny"))), input); }
        ForgeJarTransformer transformer = new ForgeJarTransformer(); transformer.index(client, mappings, true); transformer.index(forge, mappings, true);
        ClassNode original = read(forge, FML + ".class"), runtime = new ClassNode(); original.accept(new ClassRemapper(runtime, mappings.officialToRuntime()));
        Map<String, byte[]> before = new LinkedHashMap<>(); for (MethodNode method : runtime.methods) before.put(method.name + method.desc, methodBytes(runtime.name, method));
        MethodNode setBefore = method(runtime, "set"); long instructions = real(setBefore).size();
        HostRegistrySnapshotCompatibility.normalize(runtime, mappings);
        MethodNode set = method(runtime, "set"); assertEquals(1, calls(set, "beginSnapshot")); assertEquals(2, calls(set, "endSnapshot"));
        assertEquals(setBefore.tryCatchBlocks.size() + 1, set.tryCatchBlocks.size()); assertTrue(real(set).size() > instructions);
        for (MethodNode method : runtime.methods) if (!method.name.equals("set")) assertArrayEquals(before.get(method.name + method.desc), methodBytes(runtime.name, method));
        verify(runtime); byte[] once = bytes(runtime); HostRegistrySnapshotCompatibility.normalize(runtime, mappings); assertArrayEquals(once, bytes(runtime));
        ClassNode mapper = osl("net/ornithemc/osl/registries/impl/registry/sync/RegistryMapper.class");
        for (String name : List.of("apply", "undo")) {
            List<MethodInsnNode> lifecycle = real(method(mapper, name)).stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.endsWith("/ClearableRegistry") && (call.name.equals("clear") || call.name.equals("freeze"))).map(instruction -> (MethodInsnNode) instruction).toList();
            assertTrue(lifecycle.stream().anyMatch(call -> call.name.equals("clear"))); assertEquals("freeze", lifecycle.get(lifecycle.size() - 1).name);
        }
        Path cached = transformer.transform(forge, temporary.resolve("actual-api"), mappings, true, ForgeCompatBootstrap.apiPatches(mappings, true, false, false));
        ClassNode installed = read(cached, FML + ".class"); assertEquals(1, calls(method(installed, "set"), "beginSnapshot")); verify(installed);
        System.out.println("Actual Forge set lifecycle and OSL apply/undo verified; owned JVM uses unmodified OSL clear/freeze/valueRegistered method bodies with aliased fixture types");
    }

    private static Fixture fixture(boolean patch) throws Exception {
        ClassNode original = rename(owned(OwnFml.class), Map.of(Type.getInternalName(OwnFml.class), FML)); ClassNode transformed = new ClassNode(); new ClassReader(bytes(original)).accept(transformed, 0);
        HostRegistrySnapshotCompatibility.normalize(transformed, fixtureMappings());
        ClassNode registry = rename(patch ? transformed : original, Map.of(FML, OWN_FML));
        ClassNode wrapper = owned(OwnWrapper.class), actual = osl(WRAPPED + ".class");
        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                return switch (name) {
                    case WRAPPED -> Type.getInternalName(OwnWrapper.class);
                    case "net/minecraft/unmapped/C_80713356" -> Type.getInternalName(OwnBase.class);
                    case "net/ornithemc/osl/registries/api/registry/Registry" -> Type.getInternalName(OwnFrozen.class);
                    default -> name;
                };
            }
            @Override public String mapMethodName(String owner, String name, String descriptor) { return name.equals("osl$registries$clear") ? "clearOsl" : name; }
        };
        ClassNode projected = new ClassNode(); actual.accept(new ClassRemapper(projected, remapper));
        for (String name : List.of("clear", "freeze", "valueRegistered")) { MethodNode target = method(wrapper, name); wrapper.methods.set(wrapper.methods.indexOf(target), method(projected, name)); }
        wrapper = rename(wrapper, Map.of(Type.getInternalName(OwnWrapper.class), OWN_WRAPPED));
        registry.nestHostClass = null; wrapper.nestHostClass = null;
        Map<String, byte[]> definitions = Map.of(OWN_FML.replace('/', '.'), bytes(registry), OWN_WRAPPED.replace('/', '.'), bytes(wrapper));
        ClassLoader loader = new ClassLoader(HostRegistrySnapshotCompatibilityTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException { byte[] data = definitions.get(name); if (data == null) throw new ClassNotFoundException(name); return defineClass(name, data, 0, data.length); }
        };
        return new Fixture(Class.forName(OWN_FML.replace('/', '.'), true, loader), Class.forName(OWN_WRAPPED.replace('/', '.'), true, loader));
    }
    private static ForgeMappings fixtureMappings() throws Exception {
        String tiny = "tiny\t2\t0\tofficial\tintermediary\nc\tcx\t" + Type.getInternalName(OwnBase.class) + "\n\tf\tLct;\ta\tintMap\nc\tdd\t" + Type.getInternalName(OwnSimple.class)
            + "\n\tf\tLjava/util/Map;\tc\tvalues\nc\tct\t" + Type.getInternalName(OwnIds.class) + "\nc\t" + WRAPPED + "\t" + OWN_WRAPPED + "\nc\t" + REGISTRIES + "\t" + Type.getInternalName(OwnCatalog.class) + "\n";
        String srg = "CL: cx net/minecraft/util/RegistryNamespaced\nCL: dd net/minecraft/util/RegistrySimple\nCL: ct net/minecraft/util/ObjectIntIdentityMap\nFD: cx/a net/minecraft/util/RegistryNamespaced/field_148759_a\nFD: dd/c net/minecraft/util/RegistrySimple/field_82596_a\n";
        return new ForgeMappings(resolver(tiny), new StringReader(srg));
    }
    private static MappingResolver resolver(String tiny) throws Exception { MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree); var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary"); }
    private static ClassNode owned(Class<?> type) throws Exception { ClassNode node = new ClassNode(); try (var input = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) { assertNotNull(input); new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static ClassNode rename(ClassNode node, Map<String, String> names) { ClassNode result = new ClassNode(); node.accept(new ClassRemapper(result, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return names.getOrDefault(name, name); } })); return result; }
    private static ClassNode osl(String name) throws Exception {
        try (ZipFile outer = new ZipFile(ForgeTestFixtures.nativeJar("ornithe-standard-libraries-0.22.0.jar").toFile())) {
            try (ZipInputStream inner = new ZipInputStream(new ByteArrayInputStream(outer.getInputStream(outer.getEntry("META-INF/jars/osl-registries-0.2.0+mc14w27a-mc17w46a.jar")).readAllBytes()))) {
                for (var entry = inner.getNextEntry(); entry != null; entry = inner.getNextEntry()) if (entry.getName().equals(name)) { ClassNode node = new ClassNode(); new ClassReader(inner.readAllBytes()).accept(node, 0); return node; }
            }
        }
        throw new IllegalStateException("The actual OSL registry fixture is missing: " + name);
    }
    private static ClassNode read(Path path, String name) throws Exception { try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> real(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
    private static long calls(MethodNode method, String name) { return real(method).stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(Type.getInternalName(HostRegistrySnapshotCompatibility.class)) && call.name.equals(name)).count(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node); }
    private static void verify(ClassNode node) throws Exception { ClassNode serialized = new ClassNode(); new ClassReader(bytes(node)).accept(serialized, 0); for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0) new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method); }
}
