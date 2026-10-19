package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class NeiSpawnerCompatibilityTest {
    private static final String SHA = "1375fee5865235bcd0d95243fa1c95a99c478ef647d2ce486e805b6baa77fa24";
    private static final String OWNER = "codechicken/nei/ItemMobSpawner";
    private static ForgeMappings mappings;
    private static ClassNode original;
    private static int fixtureIndex;

    @BeforeAll static void setup() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path jar = root.resolve("forge-mod-test/NotEnoughItems-1.8.9-2.0.2.null-universal.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Assumptions.assumeTrue(Files.isRegularFile(jar) && Files.isRegularFile(tiny)); assertEquals(SHA, ForgeModScanner.sha256(jar));
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(Files.readString(tiny)), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var reader = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), reader); }
        ForgeJarTransformer transformer = new ForgeJarTransformer();
        Path gamePatches = root.resolve("build/forge-compat/runtime-qa/20261015-164533-cdc5d41aa0/all/game/.arcadium/forge-compat/game");
        Assumptions.assumeTrue(Files.isDirectory(gamePatches));
        try (var files = Files.list(gamePatches)) { transformer.index(files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow(), mappings, true); }
        transformer.index(root.resolve("build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"), mappings, true);
        transformer.index(jar, mappings, false);
        ClassWriter remapped = new ClassWriter(0); read(jar, OWNER).accept(new ClassRemapper(remapped, mappings.srgToRuntime()));
        original = new ClassNode(); new ClassReader(remapped.toByteArray()).accept(original, 0);
    }

    @BeforeEach void reset() {
        OwnEntityList.CLASS_NAMES.clear(); OwnEntityList.CLASS_IDS.clear(); OwnEntityList.ID_CLASSES.clear();
        OwnMinecraft.INSTANCE.world = new OwnWorldClient(); OwnConfig.smp = true; OwnConfig.errors = 0;
    }

    @Test void reproducesTheActualEarlyTooltipTagCorruptionAndRecursiveDefaultEntityFailure() throws Exception {
        register(91, "Pig", OwnPig.class); register(72, "Zombie", OwnZombie.class);
        Class<?> type = fixture(false);
        OwnStack stack = new OwnStack(new OwnItem(), 1, 72);
        var tag = type.getDeclaredMethod("setDefaultTag", OwnStack.class); tag.setAccessible(true); tag.invoke(null, stack);
        assertEquals(0, stack.metadata());
        AtomicReference<Throwable> result = new AtomicReference<>();
        Thread thread = new Thread(null, () -> { try { type.getMethod("getEntity", int.class).invoke(null, 0); }
            catch (Throwable failure) { result.set(failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure); } }, "nei-spawner-reproduction", 256 * 1024);
        thread.setDaemon(true); thread.start(); thread.join(5000);
        assertFalse(thread.isAlive()); assertInstanceOf(StackOverflowError.class, result.get()); assertTrue(OwnConfig.errors > 1);
    }

    @Test void lazilyRunsTheOriginalCatalogBeforeEarlyJeiReadsAndPreservesSubtypesTooltipsAndCachedWorldEntities() throws Exception {
        register(91, "Pig", OwnPig.class); register(72, "Zombie", OwnZombie.class); register(63, "EnderDragon", OwnDragon.class);
        register(12, "NonLiving", OwnEntity.class); register(51, "Broken", OwnBroken.class);
        Class<?> type = fixture(true); Object item = type.getConstructor().newInstance();
        OwnStack stack = new OwnStack((OwnItem)item, 1, 72);
        var tag = type.getDeclaredMethod("setDefaultTag", OwnStack.class); tag.setAccessible(true); tag.invoke(null, stack);
        assertEquals(72, stack.metadata()); assertEquals(91, type.getField("idPig").getInt(null));
        List<OwnStack> subtypes = new ArrayList<>(); type.getMethod("subtypes", OwnItem.class, OwnTabs.class, List.class).invoke(item, item, new OwnTabs(), subtypes);
        assertEquals(Set.of(91,72), new HashSet<>(subtypes.stream().map(OwnStack::metadata).toList()));
        List<String> tooltip = new ArrayList<>(); type.getMethod("tooltip", OwnStack.class, OwnPlayer.class, List.class, boolean.class).invoke(item, stack, null, tooltip, false);
        assertEquals(List.of("\u00a74Zombie"), tooltip);
        OwnLiving entity = (OwnLiving) type.getMethod("getEntity", int.class).invoke(null, 72);
        assertInstanceOf(OwnZombie.class, entity); assertSame(OwnMinecraft.INSTANCE.world, entity.world);
        Map<Class<?>,Integer> counts = new HashMap<>(OwnMinecraft.INSTANCE.world.created);
        assertSame(entity, type.getMethod("getEntity", int.class).invoke(null, 72)); tag.invoke(null, stack);
        assertEquals(counts, OwnMinecraft.INSTANCE.world.created);
        OwnStack invalid = new OwnStack((OwnItem)item, 1, 12345); tag.invoke(null, invalid); assertEquals(91, invalid.metadata());
        OwnConfig.smp = false; subtypes.clear(); type.getMethod("subtypes", OwnItem.class, OwnTabs.class, List.class).invoke(item, item, new OwnTabs(), subtypes);
        assertEquals(1, subtypes.size()); assertEquals(0, subtypes.get(0).metadata());
    }

    @Test void acceptsARealRegistryPigIdOfZeroAndPreservesTheOriginalUnknownEntityFallback() throws Exception {
        register(0, "Pig", OwnPig.class); register(72, "Zombie", OwnZombie.class);
        Class<?> type = fixture(true);
        Object pig = type.getMethod("getEntity", int.class).invoke(null, 0); assertInstanceOf(OwnPig.class, pig);
        assertSame(pig, type.getMethod("getEntity", int.class).invoke(null, 9876));
        assertSame(pig, type.getMethod("getEntity", int.class).invoke(null, 9876)); assertEquals(1, OwnConfig.errors);
    }

    @Test void boundsARealPigConstructionFailureInsteadOfRecursivelyFallingBackToItself() throws Exception {
        register(91, "Pig", OwnPig.class); Class<?> type = fixture(true);
        type.getMethod("getEntity", int.class).invoke(null, 91);
        var cache = type.getDeclaredField("entityHashMap"); cache.setAccessible(true); ((Map<?,?>)cache.get(null)).clear();
        OwnEntityList.ID_CLASSES.put(91, OwnFailingPig.class);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> type.getMethod("getEntity", int.class).invoke(null, 91));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("default Pig spawner")); assertNotNull(failure.getCause().getCause());
        assertEquals(1, OwnConfig.errors);
    }

    @Test void leavesTheCatalogRetryableUntilTheWorldAndEntityRegistryAreAvailable() throws Exception {
        Class<?> type = fixture(true); OwnMinecraft.INSTANCE.world = null;
        InvocationTargetException noRegistry = assertThrows(InvocationTargetException.class, () -> type.getMethod("getEntity", int.class).invoke(null, 91));
        assertInstanceOf(IllegalStateException.class, noRegistry.getCause());
        register(91, "Pig", OwnPig.class);
        InvocationTargetException noWorld = assertThrows(InvocationTargetException.class, () -> type.getMethod("getEntity", int.class).invoke(null, 91));
        assertTrue(noWorld.getCause().getMessage().contains("active client world"));
        OwnMinecraft.INSTANCE.world = new OwnWorldClient();
        assertInstanceOf(OwnPig.class, type.getMethod("getEntity", int.class).invoke(null, 91));
    }

    @Test void limitsThePatchToThePinnedNeiArchiveAndKeepsTheOriginalCatalogBodyAndFrames() throws Exception {
        assertNull(ForgeRuntimePatches.forMod("0".repeat(64), mappings));
        Consumer<ClassNode> patch = ForgeRuntimePatches.forMod(SHA, mappings); assertNotNull(patch);
        ClassNode node = copy(original); MethodNode load = node.methods.stream().filter(method -> method.name.equals("loadSpawners")).findFirst().orElseThrow();
        List<AbstractInsnNode> body = List.of(load.instructions.toArray()); byte[] before = bytes(node);
        patch.accept(node); assertEquals(body, List.of(load.instructions.toArray()));
        assertFalse(Arrays.equals(before, bytes(node))); byte[] once = bytes(node); patch.accept(node); assertArrayEquals(once, bytes(node));
        for (MethodNode method : node.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        ClassNode other = copy(original); other.name = "fixture/UnrelatedSpawner"; before = bytes(other); patch.accept(other); assertArrayEquals(before, bytes(other));
    }

    public static class OwnWorld { final Map<Class<?>,Integer> created = new HashMap<>(); }
    public static class OwnWorldClient extends OwnWorld { }
    public static class OwnEntity { }
    public static class OwnLiving extends OwnEntity {
        final OwnWorld world;
        public OwnLiving(OwnWorld world) { this.world = world; if (world != null) world.created.merge(getClass(), 1, Integer::sum); }
        public boolean isChild() { return false; }
    }
    public static class OwnPig extends OwnLiving { public OwnPig(OwnWorld world) { super(world); } }
    public interface OwnMob { }
    public static class OwnZombie extends OwnLiving implements OwnMob { public OwnZombie(OwnWorld world) { super(world); } }
    public static class OwnDragon extends OwnLiving { public OwnDragon(OwnWorld world) { super(world); } }
    public static class OwnPlayer extends OwnLiving { public OwnPlayer(OwnWorld world) { super(world); } }
    public static class OwnBroken extends OwnLiving { private OwnBroken(OwnWorld world) { super(world); } }
    public static class OwnFailingPig extends OwnLiving { public OwnFailingPig(OwnWorld world) { super(world); throw new IllegalArgumentException("Owned fixture constructor failure"); } }
    public static final class OwnEntityList {
        public static final Map<Class<?>,String> CLASS_NAMES = new HashMap<>();
        public static final Map<Class<?>,Integer> CLASS_IDS = new HashMap<>();
        public static final Map<Integer,Class<?>> ID_CLASSES = new HashMap<>();
    }
    public static final class OwnMinecraft { public static final OwnMinecraft INSTANCE = new OwnMinecraft(); public OwnWorldClient world; public static OwnMinecraft instance() { return INSTANCE; } }
    public static class OwnBlock { }
    public static final class OwnBlocks { public static final OwnBlock SPAWNER = new OwnBlock(); }
    public static class OwnItem { public OwnItem setHasSubtypes(boolean value) { return this; } }
    public static class OwnItemBlock extends OwnItem { public OwnItemBlock(OwnBlock block) { } }
    public static class OwnTabs { }
    public static class OwnStack {
        private int metadata;
        public OwnStack(OwnItem item) { }
        public OwnStack(OwnItem item, int count, int metadata) { this.metadata = metadata; }
        public int metadata() { return metadata; }
        public void metadata(int value) { metadata = value; }
    }
    public interface OwnLogger { void error(String message); void error(String message, Throwable failure); }
    public static final class OwnConfig {
        static boolean smp; static int errors;
        public static final OwnLogger logger = new OwnLogger() { @Override public void error(String message) { errors++; } @Override public void error(String message, Throwable failure) { errors++; } };
        public static boolean hasSMPCounterPart() { return smp; }
    }

    private static void register(int id, String name, Class<?> entity) { OwnEntityList.CLASS_NAMES.put(entity,name); OwnEntityList.CLASS_IDS.put(entity,id); OwnEntityList.ID_CLASSES.put(id,entity); }
    private static Class<?> fixture(boolean patched) {
        ClassNode node = copy(original);
        if (patched) ForgeRuntimePatches.forMod(SHA, mappings).accept(node);
        String subtypeName = mappings.methodFromSrg("net/minecraft/item/Item", "func_150895_a", "(Lnet/minecraft/item/Item;Lnet/minecraft/creativetab/CreativeTabs;Ljava/util/List;)V");
        String tooltipName = mappings.methodFromSrg("net/minecraft/item/Item", "func_77624_a", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Ljava/util/List;Z)V");
        node.methods.removeIf(method -> !Set.of("<init>","<clinit>","getEntity","setDefaultTag","loadSpawners",subtypeName,tooltipName).contains(method.name));
        String fixtureName = "fixture/NeiSpawner" + fixtureIndex++;
        Map<String,String> types = new HashMap<>();
        types.put(OWNER,fixtureName);
        Map<String,Class<?>> mapped = Map.ofEntries(
            Map.entry("net/minecraft/world/World",OwnWorld.class),Map.entry("net/minecraft/client/multiplayer/WorldClient",OwnWorldClient.class),
            Map.entry("net/minecraft/client/Minecraft",OwnMinecraft.class),Map.entry("net/minecraft/entity/Entity",OwnEntity.class),
            Map.entry("net/minecraft/entity/EntityLiving",OwnLiving.class),Map.entry("net/minecraft/entity/passive/EntityPig",OwnPig.class),
            Map.entry("net/minecraft/entity/player/EntityPlayer",OwnPlayer.class),Map.entry("net/minecraft/entity/EntityList",OwnEntityList.class),
            Map.entry("net/minecraft/item/Item",OwnItem.class),Map.entry("net/minecraft/item/ItemBlock",OwnItemBlock.class),Map.entry("net/minecraft/block/Block",OwnBlock.class),
            Map.entry("net/minecraft/init/Blocks",OwnBlocks.class),Map.entry("net/minecraft/item/ItemStack",OwnStack.class),Map.entry("net/minecraft/creativetab/CreativeTabs",OwnTabs.class),
            Map.entry("net/minecraft/entity/monster/IMob",OwnMob.class));
        mapped.forEach((source,type) -> types.put(mappings.classFromSrg(source),Type.getInternalName(type)));
        types.put("codechicken/nei/NEIClientConfig",Type.getInternalName(OwnConfig.class)); types.put("org/apache/logging/log4j/Logger",Type.getInternalName(OwnLogger.class));
        Map<String,String> fields = new HashMap<>();
        fields.put(mappings.fieldFromSrg("net/minecraft/entity/EntityList","field_75626_c","Ljava/util/Map;"),"CLASS_NAMES");
        fields.put(mappings.fieldFromSrg("net/minecraft/entity/EntityList","field_75624_e","Ljava/util/Map;"),"CLASS_IDS");
        fields.put(mappings.fieldFromSrg("net/minecraft/entity/EntityList","field_75623_d","Ljava/util/Map;"),"ID_CLASSES");
        fields.put(mappings.fieldFromSrg("net/minecraft/client/Minecraft","field_71441_e","Lnet/minecraft/client/multiplayer/WorldClient;"),"world");
        fields.put(mappings.fieldFromSrg("net/minecraft/init/Blocks","field_150474_ac","Lnet/minecraft/block/Block;"),"SPAWNER");
        Map<String,String> methods = new HashMap<>();
        methods.put(subtypeName,"subtypes"); methods.put(tooltipName,"tooltip");
        methods.put(mappings.methodFromSrg("net/minecraft/item/Item","func_77627_a","(Z)Lnet/minecraft/item/Item;"),"setHasSubtypes");
        methods.put(mappings.methodFromSrg("net/minecraft/item/ItemStack","func_77952_i","()I"),"metadata");
        methods.put(mappings.methodFromSrg("net/minecraft/item/ItemStack","func_77964_b","(I)V"),"metadata");
        methods.put(mappings.methodFromSrg("net/minecraft/entity/EntityLiving","func_70631_g_","()Z"),"isChild");
        methods.put(mappings.methodFromSrg("net/minecraft/client/Minecraft","func_71410_x","()Lnet/minecraft/client/Minecraft;"),"instance");
        ClassWriter writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer,new Remapper() {
            @Override public String map(String type) { return types.getOrDefault(type,type); }
            @Override public String mapFieldName(String owner,String name,String descriptor) { return fields.getOrDefault(name,name); }
            @Override public String mapMethodName(String owner,String name,String descriptor) { return methods.getOrDefault(name,name); }
        }));
        class Loader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass(fixtureName.replace('/','.'),bytes,0,bytes.length); } }
        return new Loader().define(writer.toByteArray());
    }
    private static ClassNode read(Path jar,String name) throws Exception { ClassNode node=new ClassNode();try(ZipFile zip=new ZipFile(jar.toFile());var input=zip.getInputStream(zip.getEntry(name+".class"))){new ClassReader(input.readAllBytes()).accept(node,0);}return node; }
    private static ClassNode copy(ClassNode node) { ClassNode copy=new ClassNode();node.accept(copy);return copy; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray(); }
}
