package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.AccessTransformerRules;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostRegistryCompatibilityTest {
    private static final String FML = "net/minecraftforge/fml/common/registry/FMLControlledNamespacedRegistry";
    private static final String HELPER = Type.getInternalName(HostRegistryCompatibility.class);
    private static final String OSL = "net/ornithemc/osl/registries/impl/access/IdRegistryAccess";
    @TempDir Path temporary;

    public record OwnKey(String name) { }
    public interface OwnOslAccess { }
    public static final class OwnIds {
        public final Map<Object, Integer> ids = new IdentityHashMap<>();
        public final Map<Integer, Object> objects = new LinkedHashMap<>();
        public int puts;
        public void put(Object value, int id) { puts++; ids.put(value, id); objects.put(id, value); }
    }
    public static class OwnSimple<K, V> {
        public final Map<K, V> values = new LinkedHashMap<>();
        public final Map<V, K> inverse = new IdentityHashMap<>();
        public int puts;
        public RuntimeException putFailure;
        public void put(K key, V value) throws Throwable {
            if (putFailure != null) throw putFailure;
            puts++; values.put(key, value); inverse.put(value, key);
        }
    }
    public static class OwnNamespaced<K, V> extends OwnSimple<K, V> implements OwnOslAccess {
        public final OwnIds intMap = new OwnIds();
        public int nextId, oslCallbacks;
        public RuntimeException oslFailure;
        public void register(int id, K key, V value) throws Throwable {
            intMap.put(value, id);
            super.put(key, value);
            nextId = Math.max(nextId, id + 1);
            oslCallbacks++;
            if (oslFailure != null) throw oslFailure;
        }
        @Override public void put(K key, V value) throws Throwable { register(nextId, key, value); }
    }
    public static class OwnDefaulted<K, V> extends OwnNamespaced<K, V> {
        public int defaultedRegisters;
        @Override public void register(int id, K key, V value) throws Throwable {
            defaultedRegisters++;
            super.register(id, key, value);
        }
    }
    public static class OwnFml extends OwnDefaulted<OwnKey, Object> {
        public int attempts, addCallbacks;
        public final BitSet available = new BitSet();
        public RuntimeException addFailure;
        @Override public void register(int id, OwnKey key, Object value) throws Throwable {
            attempts++;
            if (intMap.ids.containsKey(value)) throw new IllegalArgumentException("Duplicate fixture value");
            addObjectRaw(id, key, value);
        }
        private void addObjectRaw(int id, OwnKey key, Object value) throws Throwable {
            if (key == null || value == null) throw new NullPointerException("Null fixture registration");
            intMap.put(value, id);
            super.put(key, value);
            available.set(id);
            addCallbacks++;
            if (addFailure != null) throw addFailure;
        }
    }
    private record Loaded(Class<?> type, OwnDefaulted<OwnKey, Object> registry) {
        void add(int id, OwnKey key, Object value) throws Throwable {
            try { type.getMethod("register", int.class, OwnKey.class, Object.class).invoke(registry, id, key, value); }
            catch (InvocationTargetException exception) { throw exception.getCause(); }
        }
        int count(String field) throws Exception { return type.getField(field).getInt(registry); }
        BitSet available() throws Exception { return (BitSet) type.getField("available").get(registry); }
    }

    @Test void reproducesTheReentryAndVerifiesTheTransformedFourLevelHierarchyOnTheJvm() throws Throwable {
        OwnFml broken = new OwnFml(); Object original = new Object(); OwnKey air = new OwnKey("minecraft:air");
        assertThrows(IllegalArgumentException.class, () -> broken.register(0, air, original));
        assertEquals(2, broken.attempts);
        assertEquals(1, broken.intMap.puts);
        assertEquals(0, broken.puts);
        assertEquals(0, broken.oslCallbacks);
        assertEquals(0, broken.addCallbacks);

        Loaded fixed = transformedFixture(); Object stone = new Object(); OwnKey stoneKey = new OwnKey("minecraft:stone");
        fixed.add(0, air, original); fixed.add(7, stoneKey, stone);
        assertEquals(2, fixed.count("attempts"));
        assertEquals(2, fixed.count("addCallbacks"));
        assertEquals(2, fixed.registry.intMap.puts);
        assertEquals(2, fixed.registry.puts);
        assertEquals(2, fixed.registry.oslCallbacks);
        assertEquals(0, fixed.registry.defaultedRegisters);
        assertEquals(8, fixed.registry.nextId);
        assertEquals(0, fixed.registry.intMap.ids.get(original));
        assertEquals(7, fixed.registry.intMap.ids.get(stone));
        assertSame(original, fixed.registry.intMap.objects.get(0));
        assertSame(stone, fixed.registry.intMap.objects.get(7));
        assertSame(original, fixed.registry.values.get(air));
        assertSame(stone, fixed.registry.values.get(stoneKey));
        assertEquals(air, fixed.registry.inverse.get(original));
        assertEquals(stoneKey, fixed.registry.inverse.get(stone));
        assertEquals(BitSet.valueOf(new long[] {129}), fixed.available());
        assertThrows(IllegalArgumentException.class, () -> fixed.add(0, air, original));
        assertEquals(2, fixed.registry.intMap.puts);
    }

    @Test void provesThatAnAncestorOwnerWithTheFmlSpecialCallerStillReenters() throws Throwable {
        OwnFml registry = new OwnFml(); Object value = new Object(); registry.intMap.put(value, 0);
        var wrong = MethodHandles.privateLookupIn(OwnFml.class, MethodHandles.lookup())
            .findSpecial(OwnSimple.class, "put", MethodType.methodType(void.class, Object.class, Object.class), OwnFml.class)
            .asType(MethodType.methodType(void.class, Object.class, Object.class, Object.class));
        Object receiver = registry; Object key = new OwnKey("minecraft:air");
        assertThrows(IllegalArgumentException.class, () -> { wrong.invokeExact(receiver, key, value); });
        assertEquals(1, registry.attempts);
        assertEquals(0, registry.puts);
        assertEquals(0, registry.oslCallbacks);
    }

    @Test void propagatesEachOriginalFailureWithoutRunningLaterCallbacks() throws Throwable {
        for (String phase : List.of("map", "osl", "fml")) {
            Loaded loaded = transformedFixture(); RuntimeException failure = new IllegalArgumentException(phase);
            if (phase.equals("map")) loaded.registry.putFailure = failure;
            else if (phase.equals("osl")) loaded.registry.oslFailure = failure;
            else loaded.type.getField("addFailure").set(loaded.registry, failure);
            assertSame(failure, assertThrows(RuntimeException.class, () -> loaded.add(9, new OwnKey("fixture:key"), new Object())));
            assertEquals(1, loaded.count("attempts"));
            assertEquals(1, loaded.registry.intMap.puts);
            assertEquals(phase.equals("map") ? 0 : 1, loaded.registry.puts);
            assertEquals(phase.equals("map") ? 0 : 1, loaded.registry.oslCallbacks);
            assertEquals(phase.equals("map") ? 0 : 10, loaded.registry.nextId);
            assertEquals(phase.equals("fml"), loaded.available().get(9));
            assertEquals(phase.equals("fml") ? 1 : 0, loaded.count("addCallbacks"));
            assertEquals(0, loaded.registry.defaultedRegisters);
        }
    }

    @Test void isIdempotentAndRejectsUnrecognizedInstructionsBeforeMutation() throws Exception {
        ForgeMappings mappings = fixtureMappings(); ClassNode node = fixtureNode();
        HostRegistryCompatibility.normalize(node, mappings); byte[] first = bytes(node);
        HostRegistryCompatibility.normalize(node, mappings); assertArrayEquals(first, bytes(node));
        ClassNode unrelated = new ClassNode(); unrelated.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/Other", null, "java/lang/Object", null);
        byte[] original = bytes(unrelated); HostRegistryCompatibility.normalize(unrelated, null); assertArrayEquals(original, bytes(unrelated));
        ClassNode malformed = fixtureNode(); MethodNode raw = raw(malformed);
        MethodInsnNode put = Arrays.stream(raw.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("put")).map(instruction -> (MethodInsnNode) instruction).findFirst().orElseThrow();
        put.name = "unrecognized"; byte[] before = bytes(malformed);
        assertThrows(IllegalStateException.class, () -> HostRegistryCompatibility.normalize(malformed, mappings));
        assertArrayEquals(before, bytes(malformed));
    }

    @Test void defersHostClassResolutionAndFailsClosedWithoutTheRealOslInterface() throws Exception {
        ForgeMappings mappings = fixtureMappings("fixture/AbsentOslAccess");
        assertDoesNotThrow(() -> HostRegistryCompatibility.normalize(fixtureNode(), mappings));
        OwnFml registry = new OwnFml();
        assertThrows(IllegalStateException.class, () -> HostRegistryCompatibility.registerVanilla(registry, 0, new OwnKey("fixture:key"), new Object()));
        assertEquals(0, registry.intMap.puts); assertEquals(0, registry.puts);
        HostRegistryCompatibility.normalize(fixtureNode(), fixtureMappings());
        assertThrows(IllegalStateException.class, () -> HostRegistryCompatibility.registerVanilla(new Object(), 0, new OwnKey("fixture:key"), new Object()));
    }

    @Test void changesOnlyTheNineActualForgeInstructionsAndKeepsTheOslRegistrationLifecycle() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Path tiny = client.resolveSibling("calamus-gen2-intermediary-v2.tiny");
        Path dependencies = root.resolve("build/forge-compat/dependencies");
        Path forge = dependencies.resolve("forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        Path osl = ForgeTestFixtures.nativeJar("ornithe-standard-libraries-0.22.0.jar");
        assertTrue(Files.isRegularFile(client)); assertTrue(Files.isRegularFile(tiny)); assertTrue(Files.isRegularFile(forge)); assertTrue(Files.isRegularFile(osl));
        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) { assertNotNull(input); Files.copy(input, patches); }
        Path patched = new ForgeGamePatcher().patch(client, patches, temporary.resolve("patched"));
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(dependencies.resolve("forge-1.8.9.srg"))) { mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader); }
        ForgeJarTransformer transformer = new ForgeJarTransformer(); transformer.index(patched, mappings, true); transformer.index(forge, mappings, true);
        ClassNode runtime = new ClassNode(); readClass(forge, FML + ".class").accept(new ClassRemapper(runtime, mappings.officialToRuntime()));
        MethodNode raw = raw(runtime); List<AbstractInsnNode> before = real(raw);
        MethodInsnNode superPut = before.stream().filter(instruction -> instruction instanceof MethodInsnNode call
            && call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals(runtime.superName)
            && call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)V")).map(instruction -> (MethodInsnNode) instruction).findFirst().orElseThrow();
        int end = before.indexOf(superPut), begin = end - 8;
        List<AbstractInsnNode> retained = new ArrayList<>(before); retained.subList(begin, end + 1).clear();
        List<AbstractInsnNode> metadata = Arrays.stream(raw.instructions.toArray()).filter(instruction -> instruction.getOpcode() < 0).toList();
        Map<String, byte[]> unchanged = new LinkedHashMap<>();
        for (MethodNode method : runtime.methods) if (method != raw) unchanged.put(method.name + method.desc, methodBytes(runtime.name, method));
        ClassNode mixin = actualOslMixin(osl); byte[] oslBefore = bytes(mixin);
        MethodNode redirect = method(mixin, "osl$registries$replaceThisPutWithSuperPut");
        assertTrue(real(redirect).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
            && call.owner.equals(mappings.classFromSrg("net/minecraft/util/RegistrySimple"))));
        MethodNode callback = method(mixin, "osl$registries$register");
        assertTrue(real(callback).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("valueRegistered")));
        assertTrue(real(callback).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/Math") && call.name.equals("max")));
        AnnotationNode injection = callback.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        assertNotNull(injection);
        HostRegistryCompatibility.normalize(runtime, mappings);
        List<AbstractInsnNode> after = real(raw); assertEquals(before.size() - 4, after.size());
        MethodInsnNode bridge = after.stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(HELPER)).map(instruction -> (MethodInsnNode) instruction).findFirst().orElseThrow();
        int bridgeIndex = after.indexOf(bridge); assertEquals(begin + 4, bridgeIndex);
        assertEquals(List.of(0, 1, 2, 3), after.subList(bridgeIndex - 4, bridgeIndex).stream().map(instruction -> ((VarInsnNode) instruction).var).toList());
        assertEquals(List.of(Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.ALOAD, Opcodes.ALOAD), after.subList(bridgeIndex - 4, bridgeIndex).stream().map(AbstractInsnNode::getOpcode).toList());
        List<AbstractInsnNode> retainedAfter = new ArrayList<>(after); retainedAfter.subList(bridgeIndex - 4, bridgeIndex + 1).clear();
        assertEquals(retained, retainedAfter);
        assertEquals(metadata, Arrays.stream(raw.instructions.toArray()).filter(instruction -> instruction.getOpcode() < 0).toList());
        for (MethodNode method : runtime.methods) if (method != raw) assertArrayEquals(unchanged.get(method.name + method.desc), methodBytes(runtime.name, method));
        assertArrayEquals(oslBefore, bytes(mixin));
        ClassNode serialized = new ClassNode(); new ClassReader(bytes(runtime)).accept(serialized, 0);
        for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method);
        System.out.println("Actual Forge raw register: nine instructions replaced; every other method, guard, FML callback and OSL callback unchanged");
    }

    @Test void installsTheRegistryBridgeInTheRealApiArchiveEvenWithAWarmCacheAndNestedKnotLoad() throws Exception {
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Path dependencies = Path.of("../build/forge-compat/dependencies").toAbsolutePath().normalize();
        Path forge = dependencies.resolve("forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(dependencies.resolve("forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(resolver(Files.readString(client.resolveSibling("calamus-gen2-intermediary-v2.tiny"))), reader);
        }
        ForgeJarTransformer transformer = new ForgeJarTransformer(); transformer.index(client, mappings, true); transformer.index(forge, mappings, true);
        Path cache = temporary.resolve("api");
        Path before = transformer.transform(forge, cache, mappings, true, ForgeCompatBootstrap.apiPatches(mappings, false, false, false));
        assertEquals(0, bridgeCalls(raw(readClass(before, FML + ".class"))));
        Path api = transformer.transform(forge, cache, mappings, true, ForgeCompatBootstrap.apiPatches(mappings, true, false, false));
        assertNotEquals(before, api);
        assertEquals(1, bridgeCalls(raw(readClass(api, FML + ".class"))));
        assertEquals(api, transformer.transform(forge, cache, mappings, true, ForgeCompatBootstrap.apiPatches(mappings, true, false, false)));
        assertEquals(before, transformer.transform(forge, cache, mappings, true, ForgeCompatBootstrap.apiPatches(mappings, false, false, false)));
        ClassNode tweaker = readClass(api, "net/minecraftforge/fml/common/launcher/FMLTweaker.class");
        assertTrue(real(method(tweaker, "<init>")).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call
            && call.owner.equals(Type.getInternalName(ForgeRuntimeContext.class)) && call.name.equals("verifyRuntimeSource")));
        GameTransformer gameTransformer = new GameTransformer();
        String outer = FML.replace('/', '.') + "$AddCallback";
        boolean[] nested = {false};
        IOExceptionForNestedLoad stop = new IOExceptionForNestedLoad();
        FabricLauncher launcher = (FabricLauncher) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {FabricLauncher.class}, (proxy, method, args) -> {
            if (!method.getName().equals("getClassByteArray") || !args[0].equals(outer)) throw new AssertionError("Unexpected launcher operation");
            assertNull(gameTransformer.transform(FML.replace('/', '.')));
            nested[0] = true;
            throw stop;
        });
        var constructor = ForgeClassPipeline.class.getDeclaredConstructor(FabricLauncher.class, ForgeMappings.class, AccessTransformerRules.class, Map.class, Set.class);
        constructor.setAccessible(true);
        ForgeClassPipeline pipeline = constructor.newInstance(launcher, mappings, new AccessTransformerRules(), new HashMap<>(), Set.of(FML, outer.replace('.', '/')));
        var field = GameTransformer.class.getDeclaredField("patchedClasses"); field.setAccessible(true); field.set(gameTransformer, pipeline);
        IllegalStateException reentry = assertThrows(IllegalStateException.class, () -> gameTransformer.transform(outer));
        assertSame(stop, reentry.getCause()); assertTrue(nested[0]);
        assertEquals(1, bridgeCalls(raw(readClass(api, FML + ".class"))), "Knot's raw fallback must already contain the registry bridge");
        int classes = 0;
        try (ZipFile zip = new ZipFile(api.toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement(); if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode(); new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node, 0);
                for (MethodNode method : node.methods) if ((method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                classes++;
            }
        }
        assertTrue(classes > 1000);
        System.out.println("Warm Forge API cache and nested real Knot GameTransformer fallback contain one bridge; verified " + classes + " API classes without defining them");
    }

    private static final class IOExceptionForNestedLoad extends java.io.IOException { }
    private static long bridgeCalls(MethodNode method) { return real(method).stream().filter(instruction -> instruction instanceof MethodInsnNode call
        && call.owner.equals(HELPER) && call.name.equals("registerVanilla")).count(); }

    private static Loaded transformedFixture() throws Exception {
        ClassNode node = fixtureNode(); HostRegistryCompatibility.normalize(node, fixtureMappings());
        String generated = "io/github/arcadiumclient/forgecompat/fixture/RegisteredFml";
        node = rename(node, FML, generated); node.nestHostClass = null; node.nestMembers = null;
        byte[] serialized = bytes(node);
        class FixtureLoader extends ClassLoader { Class<?> define() { return defineClass(generated.replace('/', '.'), serialized, 0, serialized.length); } }
        Class<?> type = new FixtureLoader().define();
        @SuppressWarnings("unchecked") OwnDefaulted<OwnKey, Object> registry = (OwnDefaulted<OwnKey, Object>) type.getConstructor().newInstance();
        return new Loaded(type, registry);
    }
    private static ClassNode fixtureNode() throws Exception {
        ClassNode node = new ClassNode();
        try (var input = HostRegistryCompatibilityTest.class.getResourceAsStream("/" + Type.getInternalName(OwnFml.class) + ".class")) {
            assertNotNull(input); new ClassReader(input.readAllBytes()).accept(node, 0);
        }
        return rename(node, node.name, FML);
    }
    private static ForgeMappings fixtureMappings() throws Exception { return fixtureMappings(Type.getInternalName(OwnOslAccess.class)); }
    private static ForgeMappings fixtureMappings(String oslMarker) throws Exception {
        String tiny = "tiny\t2\t0\tofficial\tintermediary\nc\tco\t" + Type.getInternalName(OwnDefaulted.class)
            + "\nc\tcx\t" + Type.getInternalName(OwnNamespaced.class) + "\n\tf\tLct;\ta\tintMap\n\tm\t(ILjava/lang/Object;Ljava/lang/Object;)V\ta\tregister"
            + "\nc\tdd\t" + Type.getInternalName(OwnSimple.class) + "\n\tm\t(Ljava/lang/Object;Ljava/lang/Object;)V\ta\tput"
            + "\nc\tct\t" + Type.getInternalName(OwnIds.class) + "\n\tm\t(Ljava/lang/Object;I)V\ta\tput"
            + "\nc\tjy\t" + Type.getInternalName(OwnKey.class) + "\nc\t" + OSL + "\t" + oslMarker + "\n";
        String srg = "CL: co net/minecraft/util/RegistryNamespacedDefaultedByKey\nCL: cx net/minecraft/util/RegistryNamespaced\nCL: dd net/minecraft/util/RegistrySimple\nCL: ct net/minecraft/util/ObjectIntIdentityMap\nCL: jy net/minecraft/util/ResourceLocation\n"
            + "FD: cx/a net/minecraft/util/RegistryNamespaced/field_148759_a\n"
            + "MD: cx/a (ILjava/lang/Object;Ljava/lang/Object;)V net/minecraft/util/RegistryNamespaced/func_177775_a (ILjava/lang/Object;Ljava/lang/Object;)V\n"
            + "MD: dd/a (Ljava/lang/Object;Ljava/lang/Object;)V net/minecraft/util/RegistrySimple/func_82595_a (Ljava/lang/Object;Ljava/lang/Object;)V\n"
            + "MD: ct/a (Ljava/lang/Object;I)V net/minecraft/util/ObjectIntIdentityMap/func_148746_a (Ljava/lang/Object;I)V\n";
        ForgeMappings mappings = new ForgeMappings(resolver(tiny), new StringReader(srg));
        mappings.indexOfficialHierarchy("co", "cx", List.of()); mappings.indexOfficialHierarchy("cx", "dd", List.of()); mappings.indexOfficialHierarchy("dd", "java/lang/Object", List.of());
        return mappings;
    }
    private static MappingResolver resolver(String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary");
    }
    private static ClassNode rename(ClassNode node, String from, String to) {
        ClassNode result = new ClassNode(); node.accept(new ClassRemapper(result, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return name.equals(from) ? to : name; } })); return result;
    }
    private static ClassNode actualOslMixin(Path path) throws Exception {
        try (ZipFile outer = new ZipFile(path.toFile())) {
            for (var entries = outer.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement(); if (!entry.getName().startsWith("META-INF/jars/osl-registries-") || !entry.getName().endsWith(".jar")) continue;
                try (ZipInputStream inner = new ZipInputStream(new ByteArrayInputStream(outer.getInputStream(entry).readAllBytes()))) {
                    for (var nested = inner.getNextEntry(); nested != null; nested = inner.getNextEntry()) if (nested.getName().equals("net/ornithemc/osl/registries/impl/mixin/common/IdRegistryMixin.class")) {
                        ClassNode node = new ClassNode(); new ClassReader(inner.readAllBytes()).accept(node, 0); return node;
                    }
                }
            }
        }
        throw new IllegalStateException("The real OSL registry mixin is missing");
    }
    private static ClassNode readClass(Path path, String name) throws Exception { try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static MethodNode raw(ClassNode node) { return method(node, "addObjectRaw"); }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> real(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node); }
}
