package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
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

class HostRegistryRegistrationCompatibilityTest {
    private static final String FML = "net/minecraftforge/fml/common/registry/FMLControlledNamespacedRegistry";
    private static final String LOADER = "net/minecraftforge/fml/common/Loader";
    private static final String WRAPPED = "net/ornithemc/osl/registries/impl/registry/WrappedIdRegistry";
    private static final String API = "net/ornithemc/osl/registries/api/registry/";
    private static final String IMPL = "net/ornithemc/osl/registries/impl/registry/";
    private static final String GENERATED = "io/github/arcadiumclient/forgecompat/fixture/Registration";
    public record OwnKey(String name) { }
    public record OwnIdentifier(String name) { }
    public interface OwnAccess { }
    public interface OwnCallback { void registered(int id, Object key, Object value) throws Throwable; }
    public interface OwnFrozen { OwnFrozen freeze(); }
    public interface OwnRegistryApi { Object get(OwnIdentifier id); }
    public static final class OwnIds { public final Map<Object, Integer> ids = new IdentityHashMap<>(); public int puts; public void put(Object value, int id) { ids.put(value, id); puts++; } }
    public static class OwnSimple<K, V> { public final Map<K, V> values = new LinkedHashMap<>(); public int puts; public void put(K key, V value) { values.put(key, value); puts++; } }
    public static class OwnNamespaced<K, V> extends OwnSimple<K, V> implements OwnAccess {
        public final OwnIds intMap = new OwnIds(); public int nextId, callbacks; public OwnCallback callback;
        public void register(int id, K key, V value) throws Throwable { intMap.put(value, id); super.put(key, value); nextId = Math.max(nextId, id + 1); callbacks++; if (callback != null) callback.registered(id, key, value); }
        @Override public void put(K key, V value) { throw new AssertionError("The OSL 2-argument override must be bypassed"); }
    }
    public static class OwnDefaulted<K, V> extends OwnNamespaced<K, V> { }
    public static class OwnFml extends OwnDefaulted<OwnKey, Object> {
        public int attempts, fmlCallbacks;
        public void add(int id, OwnKey key, Object value) throws Throwable { attempts++; if (intMap.ids.containsKey(value)) throw new IllegalArgumentException("duplicate"); addObjectRaw(id, key, value); }
        private void addObjectRaw(int id, OwnKey key, Object value) throws Throwable { intMap.put(value, id); super.put(key, value); fmlCallbacks++; }
    }
    public static class OwnWrapper implements OwnFrozen {
        public final OwnNamespaced<?, ?> registry; private boolean frozen; private int nextId; public final OwnIdentifier id;
        public OwnWrapper(OwnNamespaced<?, ?> registry, OwnIdentifier id) { this.registry = registry; this.id = id; registry.callback = this::valueRegistered; }
        private void valueRegistered(int id, Object key, Object value) { throw new AssertionError("The actual OSL callback was not installed"); }
        @Override public OwnFrozen freeze() { throw new AssertionError("The actual OSL freeze method was not installed"); }
        public OwnIdentifier identifier() { return id; }
        public boolean frozen() { return frozen; }
        public int nextId() { return nextId; }
    }
    public static final class OwnKeys { public static final OwnKey BLOCK = new OwnKey("block"), ITEM = new OwnKey("item"); }
    public static final class OwnCatalog { public static final Map<OwnKey, Object> entries = new LinkedHashMap<>(); public static Object get(OwnKey key) { return entries.get(key); } }
    public static final class OwnMappings {
        public final OwnNamespaced<?, ?> registry; public final Map<Object, Integer> baseline = new LinkedHashMap<>(); public int resets; public RuntimeException failure;
        public OwnMappings(OwnNamespaced<?, ?> registry) { this.registry = registry; }
        public void reset() { if (failure != null) throw failure; baseline.clear(); registry.values.forEach((key, value) -> baseline.put(key, registry.intMap.ids.get(value))); resets++; }
    }
    public static final class OwnSyncedRegistry { public final OwnMappings mappings; public OwnSyncedRegistry(OwnNamespaced<?, ?> registry) { mappings = new OwnMappings(registry); } public OwnMappings getMappings() { return mappings; } }
    public static final class OwnSyncedCatalog implements OwnRegistryApi { public static final OwnRegistryApi SYNCED_REGISTRIES = new OwnSyncedCatalog(); public static final Map<OwnIdentifier, Object> entries = new LinkedHashMap<>(); @Override public Object get(OwnIdentifier id) { return entries.get(id); } }
    public static final class OwnFreezer { public static int calls; public static void freezeData() { calls++; } }
    public static class OwnLoader { public void initializeMods() { OwnFreezer.freezeData(); } }
    private record Fixture(Class<?> registryType, Class<?> wrapper, Class<?> loader) {
        @SuppressWarnings("unchecked") OwnDefaulted<OwnKey, Object> registry() throws Exception { return (OwnDefaulted<OwnKey, Object>) registryType.getConstructor().newInstance(); }
        Object wrap(OwnDefaulted<OwnKey, Object> nativeRegistry, OwnKey kind) throws Exception { OwnIdentifier id = new OwnIdentifier(kind.name); Object value = wrapper.getConstructor(OwnNamespaced.class, OwnIdentifier.class).newInstance(nativeRegistry, id); wrapper.getMethod("freeze").invoke(value); OwnCatalog.entries.put(kind, value); OwnSyncedCatalog.entries.put(id, new OwnSyncedRegistry(nativeRegistry)); return value; }
        void add(OwnDefaulted<OwnKey, Object> target, int id, OwnKey key, Object value) throws Throwable { try { registryType.getMethod("add", int.class, OwnKey.class, Object.class).invoke(target, id, key, value); } catch (InvocationTargetException exception) { throw exception.getCause(); } }
        void finish() throws Throwable { try { loader.getMethod("initializeMods").invoke(loader.getConstructor().newInstance()); } catch (InvocationTargetException exception) { throw exception.getCause(); } }
        boolean frozen(Object value) throws Exception { return (boolean) wrapper.getMethod("frozen").invoke(value); }
        int nextId(Object value) throws Exception { return (int) wrapper.getMethod("nextId").invoke(value); }
        OwnMappings baseline(OwnKey key) { return ((OwnSyncedRegistry) OwnSyncedCatalog.entries.get(new OwnIdentifier(key.name))).mappings; }
    }

    @BeforeEach void before() { OwnCatalog.entries.clear(); OwnSyncedCatalog.entries.clear(); OwnFreezer.calls = 0; }
    @AfterEach void after() { OwnCatalog.entries.clear(); OwnSyncedCatalog.entries.clear(); }

    @Test void registersBlockAndItemWithoutLosingEntriesAndRefreshesOnlyAffectedBaselinesAtActualCompletion() throws Throwable {
        Fixture fixture = fixture(false); var blocks = fixture.registry(); var items = fixture.registry();
        Object vanilla = new Object(); fixture.add(blocks, 0, new OwnKey("minecraft:air"), vanilla);
        Object blockWrapper = fixture.wrap(blocks, OwnKeys.BLOCK), itemWrapper = fixture.wrap(items, OwnKeys.ITEM);
        var unrelated = fixture.registry(); fixture.wrap(unrelated, new OwnKey("biome"));
        Object block = new Object(), item = new Object(), blockItem = new Object();
        OwnKey b = new OwnKey("smoke:block"), i = new OwnKey("smoke:item"), ib = new OwnKey("smoke:block_item");
        fixture.add(blocks, 17, b, block); fixture.add(items, 300, i, item); fixture.add(items, 301, ib, blockItem);
        assertSame(vanilla, blocks.values.get(new OwnKey("minecraft:air"))); assertSame(block, blocks.values.get(b));
        assertSame(item, items.values.get(i)); assertSame(blockItem, items.values.get(ib));
        assertEquals(17, blocks.intMap.ids.get(block)); assertEquals(300, items.intMap.ids.get(item));
        assertEquals(18, fixture.nextId(blockWrapper)); assertEquals(302, fixture.nextId(itemWrapper));
        assertTrue(fixture.frozen(blockWrapper)); assertTrue(fixture.frozen(itemWrapper));
        assertEquals(2, blocks.callbacks); assertEquals(2, items.callbacks); assertEquals(2, blocks.intMap.puts); assertEquals(2, items.intMap.puts);
        assertEquals(0, fixture.baseline(OwnKeys.BLOCK).resets); assertEquals(0, fixture.baseline(OwnKeys.ITEM).resets);
        fixture.finish(); assertEquals(1, OwnFreezer.calls);
        assertEquals(1, fixture.baseline(OwnKeys.BLOCK).resets); assertEquals(1, fixture.baseline(OwnKeys.ITEM).resets);
        assertEquals(17, fixture.baseline(OwnKeys.BLOCK).baseline.get(b)); assertEquals(300, fixture.baseline(OwnKeys.ITEM).baseline.get(i));
        assertEquals(0, fixture.baseline(new OwnKey("biome")).resets);
        fixture.finish(); assertEquals(1, fixture.baseline(OwnKeys.BLOCK).resets);
        fixture.add(items, 302, new OwnKey("smoke:late"), new Object()); assertTrue(fixture.frozen(itemWrapper)); assertEquals(2, fixture.baseline(OwnKeys.ITEM).resets);
    }

    @Test void leavesCustomWrappersFrozenAndPreservesOriginalFmlDuplicateValidation() throws Throwable {
        Fixture fixture = fixture(false); var blocks = fixture.registry(); Object wrapper = fixture.wrap(blocks, OwnKeys.BLOCK); Object value = new Object();
        fixture.add(blocks, 12, new OwnKey("smoke:block"), value);
        assertThrows(IllegalArgumentException.class, () -> fixture.add(blocks, 13, new OwnKey("smoke:duplicate"), value));
        assertEquals(1, blocks.callbacks); assertEquals(1, blocks.intMap.puts); assertTrue(fixture.frozen(wrapper));
        var custom = fixture.registry(); Object customWrapper = fixture.wrap(custom, new OwnKey("biome"));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.add(custom, 14, new OwnKey("smoke:custom"), new Object()));
        assertEquals("registry is frozen!", failure.getMessage()); assertTrue(fixture.frozen(customWrapper));
    }

    @Test void restoresTheFreezeAndOriginalExceptionWhenARegistrationCallbackFails() throws Throwable {
        Fixture fixture = fixture(false); var blocks = fixture.registry(); Object wrapper = fixture.wrap(blocks, OwnKeys.BLOCK);
        RuntimeException failure = new IllegalArgumentException("Owned callback failure"); blocks.callback = (id, key, value) -> { assertFalse(fixture.frozen(wrapper)); throw failure; };
        assertSame(failure, assertThrows(RuntimeException.class, () -> fixture.add(blocks, 15, new OwnKey("smoke:failure"), new Object())));
        assertTrue(fixture.frozen(wrapper)); fixture.finish(); assertEquals(0, fixture.baseline(OwnKeys.BLOCK).resets);
    }

    @Test void doesNotInitializeOrResolveOslCatalogsDuringVanillaBootstrapWithoutACallback() throws Throwable {
        Fixture fixture = fixture(true); var early = fixture.registry();
        assertDoesNotThrow(() -> fixture.add(early, 0, new OwnKey("minecraft:air"), new Object()));
        assertEquals(1, early.callbacks); assertEquals(1, early.intMap.puts);
    }

    @Test void propagatesBaselineRefreshFailuresAndRetainsThemForARetry() throws Throwable {
        Fixture fixture = fixture(false); var items = fixture.registry(); Object wrapper = fixture.wrap(items, OwnKeys.ITEM);
        fixture.add(items, 16, new OwnKey("smoke:item"), new Object()); OwnMappings mappings = fixture.baseline(OwnKeys.ITEM);
        RuntimeException failure = new IllegalStateException("Owned baseline failure"); mappings.failure = failure;
        assertSame(failure, assertThrows(RuntimeException.class, fixture::finish)); assertTrue(fixture.frozen(wrapper));
        mappings.failure = null; fixture.finish(); assertEquals(1, mappings.resets);
    }

    @Test void addsOnlyTheCompletionHookToTheActualForgeLoaderAndPreservesAllOtherMethods() throws Exception {
        Path forge = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        ClassNode node = read(forge, LOADER + ".class"); Map<String, byte[]> before = new LinkedHashMap<>();
        for (MethodNode method : node.methods) before.put(method.name + method.desc, methodBytes(node.name, method));
        MethodNode initialize = method(node, "initializeMods"); List<AbstractInsnNode> original = Arrays.asList(initialize.instructions.toArray());
        HostRegistryRegistrationCompatibility.normalize(node); List<AbstractInsnNode> changed = Arrays.asList(initialize.instructions.toArray());
        assertEquals(original.size() + 2, changed.size()); assertTrue(changed.containsAll(original));
        for (MethodNode method : node.methods) if (method != initialize) assertArrayEquals(before.get(method.name + method.desc), methodBytes(node.name, method));
        verify(node); byte[] once = bytes(node); HostRegistryRegistrationCompatibility.normalize(node); assertArrayEquals(once, bytes(node));
    }

    private static Fixture fixture(boolean missingCatalog) throws Exception {
        ForgeMappings mappings = mappings(missingCatalog);
        ClassNode registry = rename(owned(OwnFml.class), Map.of(Type.getInternalName(OwnFml.class), FML)); HostRegistryCompatibility.normalize(registry, mappings);
        registry = rename(registry, Map.of(FML, GENERATED + "Fml")); registry.nestHostClass = null;
        ClassNode wrapper = owned(OwnWrapper.class), actual = osl(WRAPPED + ".class");
        ClassNode projected = rename(actual, Map.of(WRAPPED, Type.getInternalName(OwnWrapper.class), "net/minecraft/unmapped/C_80713356", Type.getInternalName(OwnNamespaced.class), API + "Registry", Type.getInternalName(OwnFrozen.class)));
        for (String name : List.of("freeze", "valueRegistered")) wrapper.methods.set(wrapper.methods.indexOf(method(wrapper, name)), method(projected, name));
        wrapper = rename(wrapper, Map.of(Type.getInternalName(OwnWrapper.class), GENERATED + "Wrapper")); wrapper.nestHostClass = null;
        String persistent = "net/minecraftforge/fml/common/registry/PersistentRegistryManager";
        ClassNode loader = rename(owned(OwnLoader.class), Map.of(Type.getInternalName(OwnLoader.class), LOADER, Type.getInternalName(OwnFreezer.class), persistent));
        HostRegistryRegistrationCompatibility.normalize(loader);
        loader = rename(loader, Map.of(LOADER, GENERATED + "Loader", persistent, Type.getInternalName(OwnFreezer.class))); loader.nestHostClass = null;
        Map<String, byte[]> definitions = Map.of(registry.name.replace('/', '.'), bytes(registry), wrapper.name.replace('/', '.'), bytes(wrapper), loader.name.replace('/', '.'), bytes(loader));
        ClassLoader child = new ClassLoader(HostRegistryRegistrationCompatibilityTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException { byte[] data = definitions.get(name); if (data == null) throw new ClassNotFoundException(name); return defineClass(name, data, 0, data.length); }
        };
        HostRegistryRegistrationCompatibility.configure(mappings);
        return new Fixture(Class.forName((GENERATED + "Fml").replace('/', '.'), true, child), Class.forName((GENERATED + "Wrapper").replace('/', '.'), true, child), Class.forName((GENERATED + "Loader").replace('/', '.'), true, child));
    }
    private static ForgeMappings mappings(boolean missingCatalog) throws Exception {
        String tiny = "tiny\t2\t0\tofficial\tintermediary\nc\tco\t" + Type.getInternalName(OwnDefaulted.class) + "\nc\tcx\t" + Type.getInternalName(OwnNamespaced.class)
            + "\n\tf\tLct;\ta\tintMap\n\tm\t(ILjava/lang/Object;Ljava/lang/Object;)V\ta\tregister\nc\tdd\t" + Type.getInternalName(OwnSimple.class)
            + "\n\tm\t(Ljava/lang/Object;Ljava/lang/Object;)V\ta\tput\nc\tct\t" + Type.getInternalName(OwnIds.class) + "\n\tm\t(Ljava/lang/Object;I)V\ta\tput\nc\tjy\t" + Type.getInternalName(OwnKey.class) + "\n";
        Map<String, String> aliases = Map.ofEntries(
            Map.entry("net/ornithemc/osl/registries/impl/access/IdRegistryAccess", Type.getInternalName(OwnAccess.class)),
            Map.entry("net/ornithemc/osl/registries/impl/access/IdRegistryAccess$RegisterCallback", Type.getInternalName(OwnCallback.class)),
            Map.entry(WRAPPED, GENERATED + "Wrapper"), Map.entry(API + "Registries", missingCatalog ? "fixture/AbsentCatalog" : Type.getInternalName(OwnCatalog.class)),
            Map.entry(API + "RegistryKeys", Type.getInternalName(OwnKeys.class)), Map.entry(API + "ResourceKey", Type.getInternalName(OwnKey.class)),
            Map.entry("net/ornithemc/osl/core/api/util/NamespacedIdentifier", Type.getInternalName(OwnIdentifier.class)), Map.entry(API + "Registry", Type.getInternalName(OwnRegistryApi.class)),
            Map.entry(IMPL + "SyncedRegistriesImpl", Type.getInternalName(OwnSyncedCatalog.class)), Map.entry(IMPL + "SyncedRegistry", Type.getInternalName(OwnSyncedRegistry.class)),
            Map.entry(IMPL + "sync/SerializableRegistryMappings", Type.getInternalName(OwnMappings.class)));
        for (var entry : aliases.entrySet()) tiny += "c\t" + entry.getKey() + "\t" + entry.getValue() + "\n";
        String srg = "CL: co net/minecraft/util/RegistryNamespacedDefaultedByKey\nCL: cx net/minecraft/util/RegistryNamespaced\nCL: dd net/minecraft/util/RegistrySimple\nCL: ct net/minecraft/util/ObjectIntIdentityMap\nCL: jy net/minecraft/util/ResourceLocation\n"
            + "FD: cx/a net/minecraft/util/RegistryNamespaced/field_148759_a\nMD: cx/a (ILjava/lang/Object;Ljava/lang/Object;)V net/minecraft/util/RegistryNamespaced/func_177775_a (ILjava/lang/Object;Ljava/lang/Object;)V\n"
            + "MD: dd/a (Ljava/lang/Object;Ljava/lang/Object;)V net/minecraft/util/RegistrySimple/func_82595_a (Ljava/lang/Object;Ljava/lang/Object;)V\nMD: ct/a (Ljava/lang/Object;I)V net/minecraft/util/ObjectIntIdentityMap/func_148746_a (Ljava/lang/Object;I)V\n";
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree); var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); ForgeMappings mappings = new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), new StringReader(srg));
        mappings.indexOfficialHierarchy("co", "cx", List.of()); mappings.indexOfficialHierarchy("cx", "dd", List.of()); return mappings;
    }
    private static ClassNode osl(String name) throws Exception { try (ZipFile outer = new ZipFile(ForgeTestFixtures.nativeJar("ornithe-standard-libraries-0.22.0.jar").toFile())) { try (ZipInputStream inner = new ZipInputStream(new ByteArrayInputStream(outer.getInputStream(outer.getEntry("META-INF/jars/osl-registries-0.2.0+mc14w27a-mc17w46a.jar")).readAllBytes()))) { for (var entry = inner.getNextEntry(); entry != null; entry = inner.getNextEntry()) if (entry.getName().equals(name)) { ClassNode node = new ClassNode(); new ClassReader(inner.readAllBytes()).accept(node, 0); return node; } } } throw new IllegalStateException("Actual OSL class missing: " + name); }
    private static ClassNode owned(Class<?> type) throws Exception { try (var input = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static ClassNode read(Path path, String name) throws Exception { try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static ClassNode rename(ClassNode node, Map<String, String> aliases) { ClassNode result = new ClassNode(); node.accept(new ClassRemapper(result, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return aliases.getOrDefault(name, name); } })); return result; }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node); }
    private static void verify(ClassNode node) throws Exception { ClassNode serialized = new ClassNode(); new ClassReader(bytes(node)).accept(serialized, 0); for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method); }
}
