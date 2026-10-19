package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.Assumptions;
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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostSplashCompatibilityTest {
    private static final String SPLASH = "net/minecraftforge/fml/client/SplashProgress";
    private static final String PROGRESS = "net/minecraftforge/fml/common/ProgressManager";
    private static final String OWN = "fixture/splash/Splash";
    private static final String OWN_PROGRESS = "fixture/splash/Progress";
    @TempDir Path temporary;

    @Test void selectsOnlyTheSupportedFalseBranchAndRetainsEveryOtherActualForgeMethod() throws Exception {
        ClassNode source = node(forge(SPLASH)); Map<String, byte[]> before = new LinkedHashMap<>();
        for (MethodNode method : source.methods) before.put(method.name + method.desc, methodBytes(method));
        MethodNode start = method(source, "start"); int count = code(start).size(), locals = start.maxLocals;
        HostSplashCompatibility.normalize(source); assertEquals(count + 2, code(start).size()); assertEquals(locals, start.maxLocals);
        var stores = code(start).stream().filter(i -> i instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC && field.name.equals("enabled")).toList();
        assertEquals(1, stores.size()); int index = code(start).indexOf(stores.get(0));
        assertEquals(Opcodes.POP, code(start).get(index - 2).getOpcode()); assertEquals(Opcodes.ICONST_0, code(start).get(index - 1).getOpcode());
        for (MethodNode method : source.methods) if (method != start) assertArrayEquals(before.get(method.name + method.desc), methodBytes(method), method.name + method.desc);
        new Analyzer<>(new BasicVerifier()).analyze(source.name, start);
        byte[] once = bytes(source); HostSplashCompatibility.normalize(source); assertArrayEquals(once, bytes(source));
        assertTrue(calls(start).stream().noneMatch(call -> call.owner.startsWith("io/github/arcadiumclient/") || call.name.equals("setProperty")));
    }

    @Test void confirmsTheExactForgeNullTextureManagerAndNativeDirectBindMismatchWithoutDefiningGameClasses() throws Exception {
        ClassNode splashFont = node(forge(SPLASH + "$SplashFontRenderer")); List<AbstractInsnNode> constructor = code(method(splashFont, "<init>"));
        MethodInsnNode parent = calls(method(splashFont, "<init>")).stream().filter(call -> call.name.equals("<init>") && call.owner.equals("avn")).findFirst().orElseThrow();
        int index = constructor.indexOf(parent); assertEquals("(Lavh;Ljy;Lbmj;Z)V", parent.desc);
        assertEquals(Opcodes.ACONST_NULL, constructor.get(index - 2).getOpcode()); assertEquals(Opcodes.ICONST_0, constructor.get(index - 1).getOpcode());
        assertTrue(calls(method(splashFont, "bindTexture")).stream().anyMatch(call -> call.owner.equals(SPLASH + "$Texture") && call.name.equals("bind")));
        Path argentum = ForgeTestFixtures.nativeJar("argentum-1.0.1.jar").toAbsolutePath().normalize(); Assumptions.assumeTrue(Files.isRegularFile(argentum));
        ClassNode batcher = node(entry(argentum, "dev/rdh/argentum/impl/render/gui/TextBatcher.class"));
        assertTrue(calls(method(batcher, "useTexture")).stream().anyMatch(call -> call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals("net/minecraft/unmapped/C_82920792") && call.name.equals("m_69671809")));
        byte[] nativeBefore = bytes(batcher); HostSplashCompatibility.normalize(batcher); assertArrayEquals(nativeBefore, bytes(batcher));
        ClassNode callbacks = node(entry(argentum, "dev/rdh/argentum/mixin/features/font/TextRendererMixin.class")); byte[] callbacksBefore = bytes(callbacks);
        HostSplashCompatibility.normalize(callbacks); assertArrayEquals(callbacksBefore, bytes(callbacks));
        assertTrue(calls(method(callbacks, "argentum$drawBasicGlyph")).stream().anyMatch(call -> call.owner.equals(batcher.name) && call.name.equals("drawBasicGlyph")));
    }

    @Test void runsTheActualSettingsAndFalsePathWithConfigAndOptifineMatrixOnTheCallingThread() throws Exception {
        String noSplash = System.getProperty("fml.noSplash"); int cases = 0;
        for (boolean patched : List.of(false, true)) for (String enabled : Arrays.asList(null, "true", "false"))
            for (boolean optifine : List.of(false, true)) for (boolean compatible : List.of(false, true)) {
                OwnSupport.reset(); OwnClientHandler.optifine = optifine;
                if (compatible) OwnSupport.blackboard.put("optifine.ForgeSplashCompatible", true);
                Path game = Files.createDirectories(temporary.resolve("case-" + cases)); OwnGame.INSTANCE.v = game.toFile(); OwnGame.INSTANCE.draws = 0; OwnGame.INSTANCE.drawThread = null;
                Path config = game.resolve("config/splash.properties"); Files.createDirectories(config.getParent()); Properties initial = new Properties();
                if (enabled != null) initial.setProperty("enabled", enabled);
                initial.setProperty("fontTexture", "custom:font.png"); initial.setProperty("resourcePackPath", "resources 日本語 + 100%"); initial.setProperty("custom.option", "kept");
                try (Writer writer = Files.newBufferedWriter(config)) { initial.store(writer, "fixture"); }
                ClassNode source = node(forge(SPLASH)); if (patched) HostSplashCompatibility.normalize(source);
                Class<?> fixture = new OwnLoader(Map.of(OWN.replace('/', '.'), splashFixture(source))).loadClass(OWN.replace('/', '.'));
                fixture.getMethod("start").invoke(null);
                boolean platform = !System.getProperty("os.name").toLowerCase().contains("mac");
                boolean selected = !patched && (enabled == null ? platform : Boolean.parseBoolean(enabled)) && (!optifine || compatible);
                var selectedField = fixture.getDeclaredField("enabled"); selectedField.setAccessible(true); assertEquals(selected, selectedField.getBoolean(null));
                assertEquals(selected ? 1 : 0, OwnSupport.background);
                Properties stored = new Properties(); try (Reader reader = Files.newBufferedReader(config)) { stored.load(reader); }
                assertEquals(enabled == null ? Boolean.toString(platform) : enabled, stored.getProperty("enabled"));
                assertEquals("kept", stored.getProperty("custom.option")); assertEquals("custom:font.png", stored.getProperty("fontTexture"));
                assertEquals("resources 日本語 + 100%", stored.getProperty("resourcePackPath")); assertTrue(OwnLocation.created.contains("custom:font.png"));
                OwnTextures textures = new OwnTextures(); OwnLocation location = new OwnLocation("loading.png");
                fixture.getMethod("drawVanillaScreen", OwnTextures.class).invoke(null, textures);
                fixture.getMethod("clearVanillaResources", OwnTextures.class, OwnLocation.class).invoke(null, textures, location);
                assertEquals(selected ? 0 : 1, OwnGame.INSTANCE.draws); assertEquals(selected ? 0 : 1, textures.clears);
                if (!selected) {
                    assertSame(Thread.currentThread(), OwnGame.INSTANCE.drawThread); assertSame(location, textures.cleared);
                    for (String lifecycle : List.of("pause", "resume", "finish")) fixture.getMethod(lifecycle).invoke(null);
                    assertEquals(0, OwnSupport.background);
                }
                cases++;
            }
        assertEquals(24, cases); assertEquals(noSplash, System.getProperty("fml.noSplash"));
    }

    @Test void retainsAndExecutesRealProgressManagerPushStepPopAndValidationWithSplashDisabled() throws Exception {
        OwnSupport.reset(); Map<String, byte[]> classes = new LinkedHashMap<>();
        for (String name : List.of(PROGRESS, PROGRESS + "$ProgressBar", PROGRESS + "$1")) {
            ClassNode source = node(forge(name)); byte[] before = bytes(source); HostSplashCompatibility.normalize(source); assertArrayEquals(before, bytes(source));
            ClassNode own = translate(source); classes.put(own.name.replace('/', '.'), bytes(own));
        }
        OwnLoader loader = new OwnLoader(classes); Class<?> progress = loader.loadClass(OWN_PROGRESS.replace('/', '.'));
        Class<?> barType = loader.loadClass((OWN_PROGRESS + "$ProgressBar").replace('/', '.'));
        Object parent = progress.getMethod("push", String.class, int.class).invoke(null, "Resources", 2);
        Object child = progress.getMethod("push", String.class, int.class, boolean.class).invoke(null, "Textures", 1, true);
        assertEquals(List.of(parent, child), bars(progress));
        InvocationTargetException unfinished = assertThrows(InvocationTargetException.class, () -> progress.getMethod("pop", barType).invoke(null, child));
        assertInstanceOf(IllegalStateException.class, unfinished.getCause());
        barType.getMethod("step", String.class).invoke(child, "ready"); assertEquals(1, barType.getMethod("getStep").invoke(child)); assertEquals("ready", barType.getMethod("getMessage").invoke(child));
        progress.getMethod("pop", barType).invoke(null, child); assertEquals(List.of(parent), bars(progress));
        barType.getMethod("step", String.class).invoke(parent, "models"); barType.getMethod("step", String.class).invoke(parent, "fonts");
        InvocationTargetException excess = assertThrows(InvocationTargetException.class, () -> barType.getMethod("step", String.class).invoke(parent, "extra"));
        assertInstanceOf(IllegalStateException.class, excess.getCause()); progress.getMethod("pop", barType).invoke(null, parent);
        assertTrue(bars(progress).isEmpty()); assertEquals(7, OwnCommonHandler.windowMessages); assertTrue(OwnSupport.messages.size() >= 2);
    }

    @Test void appliesThroughTheRealApiCallbackOnColdAndWarmTransformerCache() throws Exception {
        Path input = temporary.resolve("splash-api.jar"); try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(input))) {
            zip.putNextEntry(new ZipEntry(SPLASH + ".class")); zip.write(forge(SPLASH)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry(PROGRESS + ".class")); zip.write(forge(PROGRESS)); zip.closeEntry();
        }
        byte[] original = Files.readAllBytes(input); ForgeMappings mappings = mappings(); ForgeJarTransformer transformer = new ForgeJarTransformer();
        byte[] last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Path converted = transformer.transform(input, temporary.resolve("cache"), mappings, true, ForgeCompatBootstrap.apiPatches(mappings, false, false, false));
            byte[] splash = entry(converted, SPLASH + ".class"); ClassNode node = node(splash); byte[] once = bytes(node);
            HostSplashCompatibility.normalize(node); assertArrayEquals(once, bytes(node));
            assertEquals(code(method(node(forge(SPLASH)), "start")).size() + 2, code(method(node, "start")).size());
            assertArrayEquals(methodBytes(method(node(forge(PROGRESS)), "barIterator")), methodBytes(method(node(entry(converted, PROGRESS + ".class")), "barIterator")));
            if (last != null) assertArrayEquals(last, splash); last = splash;
        }
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void leavesUnrelatedClassesUntouchedAndRejectsUnknownSelectionBeforeAnyMutation() throws Exception {
        ClassNode other = node(forge(SPLASH)); other.name = "fixture/Unrelated"; byte[] before = bytes(other);
        HostSplashCompatibility.normalize(other); assertArrayEquals(before, bytes(other));
        ClassNode changed = node(forge(SPLASH)); MethodNode start = method(changed, "start");
        FieldInsnNode store = (FieldInsnNode) code(start).stream().filter(i -> i instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC && field.name.equals("enabled")).findFirst().orElseThrow();
        store.name = "different"; byte[] unsupported = bytes(changed);
        assertThrows(IllegalStateException.class, () -> HostSplashCompatibility.normalize(changed)); assertArrayEquals(unsupported, bytes(changed));
        ClassNode missing = node(forge(SPLASH)); missing.methods.removeIf(method -> method.name.equals("start"));
        assertThrows(IllegalStateException.class, () -> HostSplashCompatibility.normalize(missing));
    }

    public interface OwnPack { }
    public static final class OwnDirectoryPack implements OwnPack { public OwnDirectoryPack(File path) { } }
    public static final class OwnFilePack implements OwnPack { public OwnFilePack(File path) { } }
    public static final class OwnLocation {
        static final List<String> created = new ArrayList<>(); public final String value;
        public OwnLocation(String value) { this.value = value; created.add(value); }
    }
    public static final class OwnTextures {
        int clears; OwnLocation cleared;
        public void c(OwnLocation location) { clears++; cleared = location; }
    }
    public static final class OwnGame {
        public static final OwnGame INSTANCE = new OwnGame(); public File v; int draws; Thread drawThread;
        public static OwnGame A() { return INSTANCE; }
        public void a(OwnTextures textures) { draws++; drawThread = Thread.currentThread(); }
    }
    public static final class OwnClientHandler {
        private static final OwnClientHandler INSTANCE = new OwnClientHandler(); static boolean optifine;
        public static OwnClientHandler instance() { return INSTANCE; }
        public boolean hasOptifine() { return optifine; }
    }
    public static final class OwnCommonHandler {
        private static final OwnCommonHandler INSTANCE = new OwnCommonHandler(); static int windowMessages;
        public static OwnCommonHandler instance() { return INSTANCE; }
        public void processWindowMessages() { windowMessages++; }
        public String stripSpecialChars(String value) { return value; }
    }
    public enum OwnLevel { ERROR }
    public static final class OwnSupport {
        public static final Map<String, Object> blackboard = new HashMap<>(); static final List<String> messages = new ArrayList<>(); static int background;
        public static void reset() { blackboard.clear(); messages.clear(); background = 0; OwnLocation.created.clear(); OwnCommonHandler.windowMessages = 0; }
        public static void background() { background++; }
        public static void info(String message, Object[] values) { messages.add(message); }
        public static void fine(String message, Object[] values) { messages.add(message); }
        public static void log(OwnLevel level, Throwable failure, String message, Object[] values) { messages.add(message); }
        public static void closeQuietly(Reader reader) { if (reader != null) try { reader.close(); } catch (IOException ignored) { } }
        public static void closeQuietly(Writer writer) { if (writer != null) try { writer.close(); } catch (IOException ignored) { } }
        public static String shortName(Class<?> type) { return type.getSimpleName(); }
    }

    private static byte[] splashFixture(ClassNode source) {
        Set<String> methods = Set.of("start", "getString", "getBool", "getInt", "getHex", "createResourcePack", "drawVanillaScreen", "clearVanillaResources", "pause", "resume", "finish");
        source.methods.removeIf(method -> !methods.contains(method.name));
        for (MethodNode method : source.methods) {
            if (Set.of("start", "pause", "resume", "finish").contains(method.name)) trimAtBackground(method);
            method.localVariables = null; method.exceptions.clear();
        }
        Set<String> fields = new java.util.HashSet<>();
        for (MethodNode method : source.methods) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals(SPLASH)) fields.add(field.name);
        source.fields.removeIf(field -> !fields.contains(field.name)); ClassNode result = translate(source);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String left, String right) { return "java/lang/Object"; }
        };
        result.accept(writer); return writer.toByteArray();
    }

    private static void trimAtBackground(MethodNode method) {
        List<AbstractInsnNode> instructions = code(method); JumpInsnNode conditional = null;
        for (int index = 0; index + 2 < instructions.size(); index++) if (instructions.get(index) instanceof FieldInsnNode field
            && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(SPLASH) && field.name.equals("enabled")
            && instructions.get(index + 1) instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.IFNE
            && instructions.get(index + 2).getOpcode() == Opcodes.RETURN) { conditional = branch; break; }
        assertNotNull(conditional); LabelNode background = conditional.label;
        int boundary = method.instructions.indexOf(background);
        method.tryCatchBlocks.removeIf(handler -> method.instructions.indexOf(handler.start) >= boundary || method.instructions.indexOf(handler.end) >= boundary || method.instructions.indexOf(handler.handler) >= boundary);
        for (AbstractInsnNode instruction = background.getNext(); instruction != null;) { AbstractInsnNode next = instruction.getNext(); method.instructions.remove(instruction); instruction = next; }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(OwnSupport.class), "background", "()V", false)); method.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private static ClassNode translate(ClassNode source) {
        ClassNode result = new ClassNode(); source.accept(new ClassRemapper(result, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                if (name.equals(PROGRESS) || name.startsWith(PROGRESS + "$")) return OWN_PROGRESS + name.substring(PROGRESS.length());
                return switch (name) {
                    case SPLASH -> OWN;
                    case "ave" -> Type.getInternalName(OwnGame.class);
                    case "bmj" -> Type.getInternalName(OwnTextures.class);
                    case "jy" -> Type.getInternalName(OwnLocation.class);
                    case "bnk" -> Type.getInternalName(OwnPack.class);
                    case "bnd" -> Type.getInternalName(OwnDirectoryPack.class);
                    case "bnc" -> Type.getInternalName(OwnFilePack.class);
                    case "net/minecraftforge/fml/client/FMLClientHandler" -> Type.getInternalName(OwnClientHandler.class);
                    case "net/minecraftforge/fml/common/FMLCommonHandler" -> Type.getInternalName(OwnCommonHandler.class);
                    case "net/minecraftforge/fml/common/FMLLog", "net/minecraftforge/fml/common/ClassNameUtils", "net/minecraft/launchwrapper/Launch", "org/apache/commons/io/IOUtils" -> Type.getInternalName(OwnSupport.class);
                    case "org/apache/logging/log4j/Level" -> Type.getInternalName(OwnLevel.class);
                    default -> name;
                };
            }
        })); return result;
    }

    private static final class OwnLoader extends ClassLoader {
        final Map<String, byte[]> definitions;
        OwnLoader(Map<String, byte[]> definitions) { super(HostSplashCompatibilityTest.class.getClassLoader()); this.definitions = definitions; }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException { byte[] bytes = definitions.get(name); if (bytes == null) throw new ClassNotFoundException(name); return defineClass(name, bytes, 0, bytes.length); }
    }
    private static List<Object> bars(Class<?> manager) throws Exception { List<Object> result = new ArrayList<>(); Iterator<?> iterator = (Iterator<?>) manager.getMethod("barIterator").invoke(null); iterator.forEachRemaining(result::add); return result; }
    private static Path forgeJar() { Path path = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar").toAbsolutePath().normalize(); Assumptions.assumeTrue(Files.isRegularFile(path)); return path; }
    private static byte[] forge(String name) throws Exception { return entry(forgeJar(), name + ".class"); }
    private static byte[] entry(Path path, String name) throws Exception { try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { return input.readAllBytes(); } }
    private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> code(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
    private static List<MethodInsnNode> calls(MethodNode method) { return code(method).stream().filter(instruction -> instruction instanceof MethodInsnNode).map(MethodInsnNode.class::cast).toList(); }
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
