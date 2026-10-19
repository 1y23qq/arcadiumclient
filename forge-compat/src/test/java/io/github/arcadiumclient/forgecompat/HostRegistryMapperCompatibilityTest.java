package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostRegistryMapperCompatibilityTest {
    private static final String FML = "net/minecraftforge/fml/common/registry/FMLControlledNamespacedRegistry";
    private static final String PREFIX = "net/ornithemc/osl/registries/";
    private static final String WRAPPER = PREFIX + "impl/registry/WrappedIdRegistry";
    private static final String MAPPER = PREFIX + "impl/registry/sync/RegistryMapper";
    private static final String GENERATED = "io/github/arcadiumclient/forgecompat/fixture/Mapper";
    public record OwnKey(String name) { }
    public interface OwnRegistry extends Iterable<Object> { OwnKey getKey(Object value); int getId(Object value); OwnRegistry freeze(); }
    public interface OwnWritable extends OwnRegistry { Object register(int id, OwnKey key, Object value); }
    public interface OwnClearable extends OwnWritable { void clear(); }
    public interface OwnMappings { int remap(OwnKey key); }
    public interface OwnCallback { void valueRegistered(int id, Object key, Object value); }
    public static class OwnNative {
        public final Map<OwnKey, Object> values = new LinkedHashMap<>(); public final Map<Object, Integer> ids = new IdentityHashMap<>();
        public int clears, callbacks, nextId; public OwnCallback callback;
        public void register(int id, OwnKey key, Object value) { values.put(key, value); ids.put(value, id); nextId = Math.max(nextId, id + 1); callbacks++; if (callback != null) callback.valueRegistered(id, key, value); }
        public void osl$registries$clear() { values.clear(); ids.clear(); nextId = 0; clears++; }
    }
    public static class OwnDefaulted extends OwnNative {
        private final BitSet availabilityMap = new BitSet(); private final Set<Integer> blockedIds = new LinkedHashSet<>();
        public final Map<Integer, Object> refData = new LinkedHashMap<>(); public int fmlCallbacks;
        @Override public void register(int id, OwnKey key, Object value) {
            if (id < 0 || availabilityMap.get(id)) id = availabilityMap.nextClearBit(0);
            if (values.containsKey(key) || ids.containsKey(value)) throw new IllegalArgumentException("duplicate fixture entry");
            raw(id, key, value);
        }
        public void raw(int id, OwnKey key, Object value) { super.register(id, key, value); availabilityMap.set(id); refData.put(id, value); fmlCallbacks++; }
        public Iterable<Object> typeSafeIterable() { return values.values(); }
        public int getId(Object value) { return ids.getOrDefault(value, -1); }
        public BitSet mask() { return availabilityMap; }
        public void reserve(int id, boolean blocked) { availabilityMap.set(id); if (blocked) blockedIds.add(id); }
    }
    public static class OwnFml extends OwnDefaulted { }
    public static class OwnWrapper implements OwnClearable {
        public final OwnNative registry; private boolean frozen; private int nextId;
        public OwnWrapper(OwnNative registry) { this.registry = registry; registry.callback = this::valueRegistered; }
        private void valueRegistered(int id, Object key, Object value) { throw new AssertionError("Actual OSL callback missing"); }
        @Override public void clear() { throw new AssertionError("Actual OSL clear missing"); }
        @Override public OwnRegistry freeze() { throw new AssertionError("Actual OSL freeze missing"); }
        @Override public Object register(int id, OwnKey key, Object value) { registry.register(id, key, value); return value; }
        @Override public java.util.Iterator<Object> iterator() { return registry.values.values().iterator(); }
        @Override public int getId(Object value) { return registry.ids.getOrDefault(value, -1); }
        @Override public OwnKey getKey(Object value) { return registry.values.entrySet().stream().filter(entry -> entry.getValue() == value).findFirst().orElseThrow().getKey(); }
        public boolean frozen() { return frozen; }
        public int nextId() { return nextId; }
    }
    public static class OwnBackup implements OwnClearable {
        private final Map<OwnKey, Object> values = new LinkedHashMap<>(); private final Map<Object, Integer> ids = new IdentityHashMap<>(); private boolean frozen;
        @Override public void clear() { values.clear(); ids.clear(); frozen = false; }
        @Override public Object register(int id, OwnKey key, Object value) { if (frozen) throw new IllegalStateException("backup frozen"); values.put(key, value); ids.put(value, id); return value; }
        @Override public java.util.Iterator<Object> iterator() { return values.values().iterator(); }
        @Override public int getId(Object value) { return ids.getOrDefault(value, -1); }
        @Override public OwnKey getKey(Object value) { return values.entrySet().stream().filter(entry -> entry.getValue() == value).findFirst().orElseThrow().getKey(); }
        @Override public OwnRegistry freeze() { frozen = true; return this; }
    }
    public static class OwnMapper {
        private final OwnClearable registry, backup; private boolean applied;
        public OwnMapper(OwnClearable registry, OwnClearable backup) { this.registry = registry; this.backup = backup; }
        public void apply(OwnMappings mappings) { throw new AssertionError("Actual OSL apply missing"); }
        public void undo(OwnMappings mappings) { throw new AssertionError("Actual OSL undo missing"); }
        private static void register(OwnWritable registry, int id, OwnKey key, Object value) { throw new AssertionError("Actual OSL register helper missing"); }
    }
    private record Fixture(OwnDefaulted registry, OwnClearable wrapper, Object mapper, Class<?> wrapperType) {
        void apply(OwnMappings mappings) throws Throwable { invoke("apply", mappings); }
        void undo() throws Throwable { invoke("undo", (OwnMappings) key -> -1); }
        void invoke(String name, OwnMappings mappings) throws Throwable { try { mapper.getClass().getMethod(name, OwnMappings.class).invoke(mapper, mappings); } catch (InvocationTargetException failure) { throw failure.getCause(); } }
        boolean frozen() throws Exception { return (boolean) wrapperType.getMethod("frozen").invoke(wrapper); }
        int nextId() throws Exception { return (int) wrapperType.getMethod("nextId").invoke(wrapper); }
    }

    @Test void reproducesActualOslApplyAndUndoAllocatingDifferentIdsOnTheOriginalFmlMask() throws Throwable {
        Fixture fixture = fixture(false); OwnKey a = new OwnKey("minecraft:air"), s = new OwnKey("minecraft:stone"); Object air = new Object(), stone = new Object();
        fixture.registry.raw(0, a, air); fixture.registry.raw(1, s, stone); fixture.wrapper.freeze();
        fixture.apply(key -> key.equals(a) ? 0 : 1);
        assertEquals(2, fixture.registry.getId(air)); assertEquals(3, fixture.registry.getId(stone)); assertTrue(fixture.frozen());
        fixture.undo(); assertEquals(4, fixture.registry.getId(air)); assertEquals(5, fixture.registry.getId(stone));
        System.out.println("Actual OSL apply/undo repro: requested original IDs 0/1 became 2/3 then 4/5 with stale FML occupied bits");
    }

    @Test void preservesExactRemapRestoreIdsReservationsCallbacksRefDataAndNativeClear() throws Throwable {
        Fixture fixture = fixture(true); OwnKey a = new OwnKey("minecraft:air"), s = new OwnKey("minecraft:stone"); Object air = new Object(), stone = new Object();
        fixture.registry.raw(0, a, air); fixture.registry.raw(1, s, stone); fixture.registry.reserve(6, false); fixture.registry.reserve(8, true); fixture.wrapper.freeze();
        BitSet identity = fixture.registry.mask(); Map<OwnKey, Object> values = fixture.registry.values; Map<Object, Integer> ids = fixture.registry.ids;
        fixture.apply(key -> key.equals(a) ? 3 : 7);
        assertEquals(3, fixture.registry.getId(air)); assertEquals(7, fixture.registry.getId(stone));
        assertEquals(BitSet.valueOf(new long[] {(1L << 3) | (1L << 6) | (1L << 7) | (1L << 8)}), fixture.registry.mask());
        assertSame(air, fixture.registry.refData.get(3)); assertSame(stone, fixture.registry.refData.get(7)); assertTrue(fixture.frozen()); assertEquals(8, fixture.nextId());
        fixture.undo(); assertEquals(0, fixture.registry.getId(air)); assertEquals(1, fixture.registry.getId(stone));
        assertEquals(BitSet.valueOf(new long[] {3 | (1L << 6) | (1L << 8)}), fixture.registry.mask());
        assertEquals(Map.of(a, air, s, stone), fixture.registry.values); assertSame(air, fixture.registry.refData.get(0)); assertSame(stone, fixture.registry.refData.get(1));
        assertEquals(6, fixture.registry.callbacks); assertEquals(6, fixture.registry.fmlCallbacks); assertEquals(2, fixture.registry.clears); assertEquals(2, fixture.nextId());
        assertSame(identity, fixture.registry.mask()); assertSame(values, fixture.registry.values); assertSame(ids, fixture.registry.ids); assertTrue(fixture.frozen());
    }

    @Test void keepsBlockedIdsEvenWhenTheyPreviouslyHeldAnEntryAndPreservesVacantReservations() throws Throwable {
        Fixture fixture = fixture(true); OwnKey key = new OwnKey("fixture:entry"); Object value = new Object(); fixture.registry.raw(4, key, value); fixture.registry.reserve(4, true); fixture.registry.reserve(10, false);
        fixture.wrapper.clear(); assertTrue(fixture.registry.mask().get(4)); assertTrue(fixture.registry.mask().get(10));
        fixture.registry.register(4, key, value); assertEquals(0, fixture.registry.getId(value));
        assertTrue(fixture.registry.mask().get(4)); assertTrue(fixture.registry.mask().get(10));
    }

    @Test void preservesOriginalFmlSetReservationsInANestedSnapshotClearScopeAndAfterExceptions() throws Throwable {
        Fixture fixture = fixture(true); OwnKey key = new OwnKey("fixture:entry"); Object value = new Object(); fixture.registry.raw(5, key, value); fixture.registry.reserve(10, false); BitSet before = (BitSet) fixture.registry.mask().clone();
        HostRegistryMapperCompatibility.beginSnapshotClear();
        try { HostRegistryMapperCompatibility.beginSnapshotClear(); try { fixture.wrapper.clear(); } finally { HostRegistryMapperCompatibility.endSnapshotClear(); } }
        finally { HostRegistryMapperCompatibility.endSnapshotClear(); }
        assertEquals(before, fixture.registry.mask()); fixture.registry.raw(5, key, value); fixture.wrapper.freeze();
        RuntimeException failure = new IllegalArgumentException("Owned snapshot failure");
        assertSame(failure, assertThrows(RuntimeException.class, () -> {
            HostRegistryMapperCompatibility.beginSnapshotClear();
            try { fixture.wrapper.clear(); throw failure; }
            finally { HostRegistryMapperCompatibility.endSnapshotClear(); }
        }));
        assertEquals(before, fixture.registry.mask()); fixture.registry.raw(5, key, value);
        fixture.wrapper.clear(); assertFalse(fixture.registry.mask().get(5)); assertTrue(fixture.registry.mask().get(10));
    }

    @Test void usesTheFmlDeclaringClassEvenWhenASubclassShadowsItsReservationFields() throws Throwable {
        Fixture fixture = fixture(true, true); OwnKey key = new OwnKey("fixture:entry"); Object value = new Object();
        fixture.registry.raw(0, key, value); fixture.registry.reserve(6, false); fixture.wrapper.freeze();
        BitSet shadow = (BitSet) fixture.registry.getClass().getField("availabilityMap").get(fixture.registry);
        BitSet original = (BitSet) shadow.clone();
        fixture.apply(ignored -> 0); assertEquals(0, fixture.registry.getId(value)); assertTrue(fixture.registry.mask().get(6));
        assertEquals(original, shadow); fixture.undo(); assertEquals(0, fixture.registry.getId(value)); assertEquals(original, shadow);
    }

    @Test void addsOnlyTheCanonicalClearOverrideToTheActualForgeClassAndRejectsUnknownOverrides() throws Exception {
        ClassNode original = readForge(); Map<String, byte[]> before = new LinkedHashMap<>(); for (MethodNode method : original.methods) before.put(method.name + method.desc, methodBytes(original.name, method));
        HostRegistryMapperCompatibility.normalize(original, realMappings());
        assertEquals(before.size() + 1, original.methods.size()); for (MethodNode method : original.methods) if (!method.name.equals("osl$registries$clear")) assertArrayEquals(before.get(method.name + method.desc), methodBytes(original.name, method));
        verify(original); byte[] once = bytes(original); HostRegistryMapperCompatibility.normalize(original, realMappings()); assertArrayEquals(once, bytes(original));
        ClassNode invalid = readForge(); invalid.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "osl$registries$clear", "()V", null, null));
        assertThrows(IllegalStateException.class, () -> HostRegistryMapperCompatibility.normalize(invalid, realMappings()));
    }

    private static Fixture fixture(boolean patch) throws Exception { return fixture(patch, false); }
    private static Fixture fixture(boolean patch, boolean shadow) throws Exception {
        ClassNode registry = rename(owned(OwnFml.class), Map.of(Type.getInternalName(OwnFml.class), FML));
        if (patch) HostRegistryMapperCompatibility.normalize(registry, fixtureMappings());
        registry = rename(registry, Map.of(FML, GENERATED + "Fml")); registry.nestHostClass = null;
        Map<String, String> aliases = Map.ofEntries(Map.entry(WRAPPER, Type.getInternalName(OwnWrapper.class)), Map.entry(MAPPER, Type.getInternalName(OwnMapper.class)),
            Map.entry("net/minecraft/unmapped/C_80713356", Type.getInternalName(OwnNative.class)), Map.entry(PREFIX + "api/registry/Registry", Type.getInternalName(OwnRegistry.class)),
            Map.entry(PREFIX + "api/registry/WritableRegistry", Type.getInternalName(OwnWritable.class)), Map.entry(PREFIX + "api/registry/ResourceKey", Type.getInternalName(OwnKey.class)),
            Map.entry(PREFIX + "api/registry/sync/RegistryMappings", Type.getInternalName(OwnMappings.class)), Map.entry(PREFIX + "impl/registry/ClearableRegistry", Type.getInternalName(OwnClearable.class)));
        ClassNode wrapper = owned(OwnWrapper.class), projected = rename(osl(WRAPPER + ".class"), aliases);
        for (String name : List.of("clear", "freeze", "valueRegistered")) wrapper.methods.set(wrapper.methods.indexOf(method(wrapper, name)), method(projected, name));
        wrapper = rename(wrapper, Map.of(Type.getInternalName(OwnWrapper.class), GENERATED + "Wrapper")); wrapper.nestHostClass = null;
        ClassNode mapper = owned(OwnMapper.class); projected = rename(osl(MAPPER + ".class"), aliases);
        for (String name : List.of("apply", "undo", "register")) mapper.methods.set(mapper.methods.indexOf(method(mapper, name)), method(projected, name));
        mapper = rename(mapper, Map.of(Type.getInternalName(OwnMapper.class), GENERATED + "Apply")); mapper.nestHostClass = null;
        Map<String, byte[]> definitions = new LinkedHashMap<>(Map.of(registry.name.replace('/', '.'), bytes(registry), wrapper.name.replace('/', '.'), bytes(wrapper), mapper.name.replace('/', '.'), bytes(mapper)));
        if (shadow) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, GENERATED + "Derived", null, GENERATED + "Fml", null);
            writer.visitField(Opcodes.ACC_PUBLIC, "availabilityMap", "Ljava/util/BitSet;", null, null).visitEnd(); writer.visitField(Opcodes.ACC_PUBLIC, "blockedIds", "Ljava/util/Set;", null, null).visitEnd();
            var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitMethodInsn(Opcodes.INVOKESPECIAL, GENERATED + "Fml", "<init>", "()V", false);
            init.visitVarInsn(Opcodes.ALOAD, 0); init.visitTypeInsn(Opcodes.NEW, "java/util/BitSet"); init.visitInsn(Opcodes.DUP); init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/BitSet", "<init>", "()V", false); init.visitFieldInsn(Opcodes.PUTFIELD, GENERATED + "Derived", "availabilityMap", "Ljava/util/BitSet;");
            init.visitVarInsn(Opcodes.ALOAD, 0); init.visitFieldInsn(Opcodes.GETFIELD, GENERATED + "Derived", "availabilityMap", "Ljava/util/BitSet;"); init.visitIntInsn(Opcodes.BIPUSH, 100); init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/BitSet", "set", "(I)V", false);
            init.visitVarInsn(Opcodes.ALOAD, 0); init.visitTypeInsn(Opcodes.NEW, "java/util/HashSet"); init.visitInsn(Opcodes.DUP); init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false); init.visitFieldInsn(Opcodes.PUTFIELD, GENERATED + "Derived", "blockedIds", "Ljava/util/Set;"); init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd(); writer.visitEnd();
            definitions.put((GENERATED + "Derived").replace('/', '.'), writer.toByteArray());
        }
        ClassLoader loader = new ClassLoader(HostRegistryMapperCompatibilityTest.class.getClassLoader()) { @Override protected Class<?> findClass(String name) throws ClassNotFoundException { byte[] data = definitions.get(name); if (data == null) throw new ClassNotFoundException(name); return defineClass(name, data, 0, data.length); } };
        OwnDefaulted nativeRegistry = (OwnDefaulted) Class.forName((GENERATED + (shadow ? "Derived" : "Fml")).replace('/', '.'), true, loader).getConstructor().newInstance();
        Class<?> wrapperType = Class.forName((GENERATED + "Wrapper").replace('/', '.'), true, loader); OwnClearable wrapped = (OwnClearable) wrapperType.getConstructor(OwnNative.class).newInstance(nativeRegistry);
        Object mapped = Class.forName((GENERATED + "Apply").replace('/', '.'), true, loader).getConstructor(OwnClearable.class, OwnClearable.class).newInstance(wrapped, new OwnBackup());
        return new Fixture(nativeRegistry, wrapped, mapped, wrapperType);
    }
    private static ForgeMappings fixtureMappings() throws Exception { return mappings("tiny\t2\t0\tofficial\tintermediary\nc\tco\t" + Type.getInternalName(OwnDefaulted.class) + "\n"); }
    private static ForgeMappings realMappings() throws Exception { return mappings("tiny\t2\t0\tofficial\tintermediary\nc\tco\tco\n"); }
    private static ForgeMappings mappings(String tiny) throws Exception { MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree); var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true); return new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), new StringReader("CL: co net/minecraft/util/RegistryNamespacedDefaultedByKey\n")); }
    private static ClassNode readForge() throws Exception { try (ZipFile zip = new ZipFile("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"); var input = zip.getInputStream(zip.getEntry(FML + ".class"))) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static ClassNode osl(String name) throws Exception { try (ZipFile outer = new ZipFile(ForgeTestFixtures.nativeJar("ornithe-standard-libraries-0.22.0.jar").toFile())) { try (ZipInputStream inner = new ZipInputStream(new ByteArrayInputStream(outer.getInputStream(outer.getEntry("META-INF/jars/osl-registries-0.2.0+mc14w27a-mc17w46a.jar")).readAllBytes()))) { for (var entry = inner.getNextEntry(); entry != null; entry = inner.getNextEntry()) if (entry.getName().equals(name)) { ClassNode node = new ClassNode(); new ClassReader(inner.readAllBytes()).accept(node, 0); return node; } } } throw new IllegalStateException("Actual OSL fixture missing: " + name); }
    private static ClassNode owned(Class<?> type) throws Exception { try (var input = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static ClassNode rename(ClassNode node, Map<String, String> aliases) { ClassNode result = new ClassNode(); node.accept(new ClassRemapper(result, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return aliases.getOrDefault(name, name); } })); return result; }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node); }
    private static void verify(ClassNode node) throws Exception { ClassNode serialized = new ClassNode(); new ClassReader(bytes(node)).accept(serialized, 0); for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method); }
}
