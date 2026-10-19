package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.*;

class HostRecipeOverlayCompatibilityTest {
    private static final String OVERLAY = "mezz/jei/gui/ItemListOverlay";
    private static final String GENERATED = "io/github/arcadiumclient/forgecompat/fixture/CooperativeRecipeOverlay";
    private static ForgeMappings mappings;
    private static ClassNode source;
    private static String screen;

    @BeforeAll static void setup() throws Exception {
        ForgeTestFixtures.optionalGuest("jei_1.8.9-2.28.18.187.jar");
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(new StringReader(Files.readString(client.resolveSibling("calamus-gen2-intermediary-v2.tiny"))), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var reader = Files.newBufferedReader(Path.of("../build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), reader); }
        Path jei = ForgeTestFixtures.optionalGuest("jei_1.8.9-2.28.18.187.jar");
        ForgeJarTransformer index = new ForgeJarTransformer(); index.index(client, mappings, true); index.index(jei, mappings, false);
        try (ZipFile zip = new ZipFile(jei.toFile()); var input = zip.getInputStream(zip.getEntry(OVERLAY + ".class"))) {
            source = new ClassNode(); new ClassReader(input.readAllBytes()).accept(new ClassRemapper(source, mappings.srgToRuntime()), 0);
        }
        screen = mappings.classFromSrg("net/minecraft/client/gui/GuiScreen");
    }

    @Test void guardsTheActualJeiEntryPointsAndPreservesEveryOriginalInstructionAndOtherMethod() {
        ClassNode changed = copy(source); HostRecipeOverlayCompatibility.normalize(changed, mappings);
        assertEquals(source.methods.size(), changed.methods.size());
        int guarded = 0;
        for (MethodNode original : source.methods) {
            MethodNode actual = method(changed, original.name, original.desc);
            if (List.of("initGui", "updateGui").contains(original.name) && original.desc.equals("(L" + screen + ";)V")) {
                List<AbstractInsnNode> code = code(actual); assertEquals(Opcodes.ALOAD, code.get(0).getOpcode());
                MethodInsnNode predicate = assertInstanceOf(MethodInsnNode.class, code.get(1));
                assertEquals(Type.getInternalName(HostRecipeOverlayCompatibility.class), predicate.owner);
                assertEquals("ownsRecipeScreen", predicate.name); assertEquals("(Ljava/lang/Object;)Z", predicate.desc);
                MethodInsnNode close = assertInstanceOf(MethodInsnNode.class, code.get(4));
                assertEquals(OVERLAY, close.owner); assertEquals("close", close.name); assertEquals("()V", close.desc);
                for (int count = 0; count < 8; count++) actual.instructions.remove(actual.instructions.getFirst());
                assertArrayEquals(methodBytes(original), methodBytes(actual)); guarded++;
            } else assertArrayEquals(methodBytes(original), methodBytes(actual));
        }
        assertEquals(2, guarded);
        ClassNode once = copy(source); HostRecipeOverlayCompatibility.normalize(once, mappings); byte[] bytes = bytes(once, 0);
        HostRecipeOverlayCompatibility.normalize(once, mappings); assertArrayEquals(bytes, bytes(once, 0));
    }

    @Test void classifiesRecipeSubclassesWithoutLoadingAnOptionalModAndRejectsSimilarNames() {
        String recipe = OwnNeiRecipe.class.getName();
        assertTrue(HostRecipeOverlayCompatibility.recipeType(OwnNeiRecipe.class, recipe));
        assertTrue(HostRecipeOverlayCompatibility.recipeType(OwnExtendedRecipe.class, recipe));
        assertFalse(HostRecipeOverlayCompatibility.recipeType(OwnScreen.class, recipe));
        assertFalse(HostRecipeOverlayCompatibility.recipeType(OwnNeiRecipeSimilar.class, recipe));
        assertFalse(HostRecipeOverlayCompatibility.recipeType(null, recipe));
        assertFalse(HostRecipeOverlayCompatibility.ownsRecipeScreen(null));
        assertFalse(HostRecipeOverlayCompatibility.ownsRecipeScreen(new OwnScreen()));
    }

    @Test void yieldsOnlyTheForeignRecipeAndRestoresOrdinaryAndJeiScreensWithoutChangingSearchOrUserPreference() throws Exception {
        Fixture fixture = new Fixture(); Object overlay = fixture.overlay;
        OwnScreen inventory = new OwnScreen(), jeiRecipe = new OwnJeiRecipe();
        fixture.invoke("initGui", inventory); assertTrue(fixture.bool("open")); assertEquals(1, fixture.number("normalInitializations"));
        fixture.invoke("initGui", new OwnNeiRecipe()); assertFalse(fixture.bool("open")); assertEquals(1, fixture.number("closes"));
        assertEquals(1, fixture.number("normalInitializations"));
        fixture.invoke("updateGui", new OwnExtendedRecipe()); assertFalse(fixture.bool("open")); assertEquals(0, fixture.number("normalUpdates"));
        fixture.invoke("initGui", inventory); assertTrue(fixture.bool("open")); assertEquals(2, fixture.number("normalInitializations"));
        fixture.invoke("initGui", jeiRecipe); fixture.invoke("updateGui", jeiRecipe);
        assertTrue(fixture.bool("open")); assertEquals(3, fixture.number("normalInitializations")); assertEquals(1, fixture.number("normalUpdates"));
        assertSame(jeiRecipe, fixture.type.getField("current").get(overlay));
        assertEquals("diamond", fixture.type.getField("filterText").get(overlay)); assertTrue(fixture.bool("configuredEnabled"));
    }

    @Test void propagatesCloseAndOriginalBodyExceptionsWithoutContinuingOrResettingUserState() throws Exception {
        Fixture fixture = new Fixture(); RuntimeException closeFailure = new IllegalStateException("owned close failure");
        fixture.type.getField("closeFailure").set(fixture.overlay, closeFailure);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> fixture.invoke("initGui", new OwnNeiRecipe()));
        assertSame(closeFailure, failure.getCause()); assertEquals(0, fixture.number("normalInitializations"));
        fixture.type.getField("closeFailure").set(fixture.overlay, null);
        RuntimeException originalFailure = new IllegalArgumentException("owned init failure"); fixture.type.getField("originalFailure").set(fixture.overlay, originalFailure);
        failure = assertThrows(InvocationTargetException.class, () -> fixture.invoke("initGui", new OwnScreen()));
        assertSame(originalFailure, failure.getCause()); assertTrue(fixture.bool("configuredEnabled"));
        assertEquals("diamond", fixture.type.getField("filterText").get(fixture.overlay));
    }

    @Test void refusesUnknownJeiBodiesAtomicallyAndLeavesOtherClassesUntouched() {
        ClassNode wrong = copy(source); MethodNode update = method(wrong, "updateGui", "(L" + screen + ";)V");
        update.instructions.insertBefore(update.instructions.getFirst(), new InsnNode(Opcodes.NOP)); byte[] before = bytes(wrong, 0);
        assertThrows(IllegalStateException.class, () -> HostRecipeOverlayCompatibility.normalize(wrong, mappings)); assertArrayEquals(before, bytes(wrong, 0));
        ClassNode missingClose = copy(source); missingClose.methods.remove(method(missingClose, "close", "()V")); before = bytes(missingClose, 0);
        assertThrows(IllegalStateException.class, () -> HostRecipeOverlayCompatibility.normalize(missingClose, mappings)); assertArrayEquals(before, bytes(missingClose, 0));
        ClassNode unrelated = copy(source); unrelated.name = "fixture/UnrelatedOverlay"; before = bytes(unrelated, 0);
        HostRecipeOverlayCompatibility.normalize(unrelated, mappings); assertArrayEquals(before, bytes(unrelated, 0));
    }

    public static class OwnScreen { }
    public static final class OwnJeiRecipe extends OwnScreen { }
    public static class OwnNeiRecipe extends OwnScreen { }
    public static final class OwnExtendedRecipe extends OwnNeiRecipe { }
    public static final class OwnNeiRecipeSimilar extends OwnScreen { }
    public static final class RecipePredicate {
        public static boolean ownsRecipeScreen(Object value) { return value != null && HostRecipeOverlayCompatibility.recipeType(value.getClass(), OwnNeiRecipe.class.getName()); }
    }
    public static class OwnOverlay {
        public boolean open, configuredEnabled = true;
        public int normalInitializations, normalUpdates, closes;
        public String filterText = "diamond";
        public OwnScreen current;
        public RuntimeException closeFailure, originalFailure;
        public void initGui(OwnScreen screen) { if (originalFailure != null) throw originalFailure; normalInitializations++; current = screen; open = configuredEnabled; }
        public void updateGui(OwnScreen screen) { normalUpdates++; current = screen; }
        public void close() { if (closeFailure != null) throw closeFailure; closes++; open = false; }
    }
    private static final class Fixture {
        final Class<?> type; final Object overlay;
        Fixture() throws Exception {
            ClassNode original = owned(OwnOverlay.class), guarded = copy(source); HostRecipeOverlayCompatibility.normalize(guarded, mappings);
            for (String name : List.of("initGui", "updateGui")) {
                MethodNode target = method(original, name, "(L" + Type.getInternalName(OwnScreen.class) + ";)V");
                MethodNode actual = method(guarded, name, "(L" + screen + ";)V");
                JumpInsnNode jump = assertInstanceOf(JumpInsnNode.class, code(actual).get(2));
                var labels = new HashMap<LabelNode, LabelNode>(); labels.put(jump.label, new LabelNode()); InsnList prefix = new InsnList();
                for (AbstractInsnNode instruction = actual.instructions.getFirst(); instruction != jump.label; instruction = instruction.getNext()) {
                    AbstractInsnNode cloned = instruction.clone(labels);
                    if (cloned instanceof MethodInsnNode call) call.owner = call.name.equals("close") ? original.name : Type.getInternalName(RecipePredicate.class);
                    prefix.add(cloned);
                }
                prefix.add(labels.get(jump.label)); target.instructions.insertBefore(target.instructions.getFirst(), prefix);
            }
            ClassNode renamed = new ClassNode(); original.accept(new ClassRemapper(renamed, new Remapper(Opcodes.ASM9) {
                @Override public String map(String name) { return name.equals(original.name) ? GENERATED : name; }
            })); renamed.nestHostClass = null;
            byte[] bytes = bytes(renamed, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassLoader loader = new ClassLoader(HostRecipeOverlayCompatibilityTest.class.getClassLoader()) { Class<?> define() { return defineClass(GENERATED.replace('/', '.'), bytes, 0, bytes.length); } };
            var define = loader.getClass().getDeclaredMethod("define"); define.setAccessible(true); type = (Class<?>) define.invoke(loader); overlay = type.getConstructor().newInstance();
        }
        void invoke(String name, OwnScreen screen) throws Exception { type.getMethod(name, OwnScreen.class).invoke(overlay, screen); }
        boolean bool(String name) throws Exception { return type.getField(name).getBoolean(overlay); }
        int number(String name) throws Exception { return type.getField(name).getInt(overlay); }
    }
    private static ClassNode owned(Class<?> type) throws Exception { try (var input = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static ClassNode copy(ClassNode source) { ClassNode node = new ClassNode(); new ClassReader(bytes(source, 0)).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node, int flags) { ClassWriter writer = new ClassWriter(flags); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(MethodNode method) { ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, OVERLAY, null, "java/lang/Object", null); method.accept(writer); writer.visitEnd(); return writer.toByteArray(); }
    private static MethodNode method(ClassNode node, String name, String descriptor) { return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> code(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
}
