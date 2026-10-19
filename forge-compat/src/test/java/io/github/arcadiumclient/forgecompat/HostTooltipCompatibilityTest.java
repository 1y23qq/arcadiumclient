package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.points.BeforeConstant;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class HostTooltipCompatibilityTest {
    private static final String UTILS = "net/minecraftforge/fml/client/config/GuiUtils";
    private static final String SCREEN = "io/github/arcadiumclient/forgecompat/fixture/TooltipScreen";
    private static final String FONT = HostTooltipCompatibilityTest.OwnFont.class.getName().replace('.', '/');
    private static final String THREE = "(Ljava/util/List;II)V";
    private static final int BACKGROUND = -267386864;
    @TempDir Path temporary;

    public static final class OwnFont { }
    public static final class Recorder {
        static Object font;
        static int x, y, width, height, maximum, background;
        static RuntimeException failure;
        static Runnable nested;
        public static void draw(Object current, List<?> text, int currentX, int currentY, int screenWidth,
                                int screenHeight, int maxWidth, int color) {
            font = current; x = currentX; y = currentY; width = screenWidth; height = screenHeight; maximum = maxWidth; background = color;
            if (failure != null) throw failure;
            Runnable callback = nested; nested = null;
            if (callback != null) callback.run();
        }
    }

    @Test void verifiesBothRoutesAndRestoresCustomFontAfterSuccessFailureAndReentryOnTheJvm() throws Exception {
        ClassNode node = screen();
        HostTooltipCompatibility.normalize(node, fixtureMappings(), launcher(bytes(template(), true)));
        class FixtureLoader extends ClassLoader {
            Class<?> define(byte[] source) { return defineClass(SCREEN.replace('/', '.'), source, 0, source.length); }
        }
        Class<?> loaded = new FixtureLoader().define(bytes(node, false));
        Object screen = loaded.getConstructor().newInstance();
        OwnFont normal = new OwnFont(), custom = new OwnFont(), inner = new OwnFont();
        loaded.getField("font").set(screen, normal);
        loaded.getField("width").setInt(screen, 640); loaded.getField("height").setInt(screen, 360);
        var simple = loaded.getDeclaredMethod("tooltip", List.class, int.class, int.class); simple.setAccessible(true);
        var four = loaded.getDeclaredMethod("drawHoveringText", List.class, int.class, int.class, OwnFont.class); four.setAccessible(true);
        var state = loaded.getDeclaredField("arcadium$forgeTooltipFont"); state.setAccessible(true);
        simple.invoke(screen, List.of("default"), 20, 30);
        assertSame(normal, Recorder.font); assertNull(state.get(screen));
        assertEquals(32, Recorder.x); assertEquals(18, Recorder.y);
        assertEquals(640, Recorder.width); assertEquals(360, Recorder.height); assertEquals(-1, Recorder.maximum);
        assertEquals(BACKGROUND, Recorder.background);
        four.invoke(screen, List.of("custom"), 20, 30, custom);
        assertSame(custom, Recorder.font); assertNull(state.get(screen));
        RuntimeException failure = new IllegalArgumentException("owned fixture render failure");
        Recorder.failure = failure;
        try {
            assertSame(failure, assertThrows(InvocationTargetException.class,
                () -> four.invoke(screen, List.of("failure"), 20, 30, custom)).getCause());
            assertNull(state.get(screen));
        } finally { Recorder.failure = null; }
        Recorder.nested = () -> {
            try {
                assertSame(custom, state.get(screen));
                four.invoke(screen, List.of("inner"), 10, 40, inner);
                assertSame(inner, Recorder.font);
                assertSame(custom, state.get(screen));
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        };
        four.invoke(screen, List.of("outer"), 20, 30, custom);
        assertNull(state.get(screen));
        simple.invoke(screen, List.of("default-after"), 20, 30);
        assertSame(normal, Recorder.font);
    }

    @Test void preservesTheExplicitNullFontContractAndIsIdempotent() throws Exception {
        Object fallback = new Object(), custom = new Object();
        assertSame(fallback, HostTooltipCompatibility.selectFont(null, fallback));
        assertSame(custom, HostTooltipCompatibility.selectFont(HostTooltipCompatibility.fontToken(custom), fallback));
        assertNull(HostTooltipCompatibility.selectFont(HostTooltipCompatibility.fontToken(null), fallback));
        ClassNode node = screen(); var mappings = fixtureMappings();
        FabricLauncher loader = launcher(bytes(template(), true));
        HostTooltipCompatibility.normalize(node, mappings, loader);
        byte[] first = bytes(node, false);
        HostTooltipCompatibility.normalize(node, mappings, loader);
        assertArrayEquals(first, bytes(node, false));
    }

    @Test void rejectsUnexpectedWrappersWithoutPartiallyChangingTheScreen() throws Exception {
        ClassNode node = screen();
        MethodNode four = method(node, "drawHoveringText", "(Ljava/util/List;IIL" + FONT + ";)V");
        AbstractInsnNode constant = Arrays.stream(four.instructions.toArray()).filter(value -> value.getOpcode() == Opcodes.ICONST_M1).findFirst().orElseThrow();
        four.instructions.set(constant, new InsnNode(Opcodes.ICONST_0));
        byte[] before = bytes(node, false);
        assertThrows(IllegalStateException.class, () -> HostTooltipCompatibility.normalize(node, fixtureMappings(), launcher(bytes(template(), true))));
        assertArrayEquals(before, bytes(node, false));
    }

    @Test void restoresActualScrollAndArgentumAnchorsWithTheOriginalForgeBodyAndLocalOrdinals() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Path dependencies = root.resolve("build/forge-compat/dependencies");
        Path tooltip = ForgeTestFixtures.nativeJar("Tooltip-Scroll-Ornithe-1.0.0.jar");
        Assumptions.assumeTrue(Files.isRegularFile(client) && Files.isRegularFile(tiny) && Files.isRegularFile(tooltip));
        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) { assertNotNull(input); Files.copy(input, patches); }
        Path patched = new ForgeGamePatcher().patch(client, patches, temporary.resolve("patched"));
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(dependencies.resolve("forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader);
        }
        new ForgeJarTransformer().index(patched, mappings, true);
        ClassNode screen = runtime(readClass(patched, "axu.class"), mappings);
        ClassNode utils = runtime(readClass(dependencies.resolve("forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"), UTILS + ".class"), mappings);
        byte[] originalUtils = bytes(utils, false);
        ClassNode mixin = readClass(tooltip, "com/provismet/tooltipscroll/mixin/ScreenMixin.class");
        MethodNode edit = mixin.methods.stream().filter(method -> method.name.equals("tooltipscroll$editXY")).findFirst().orElseThrow();
        AnnotationNode injection = edit.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        String target = ((List<?>) value(injection, "method")).get(0).toString();
        String name = target.substring(0, target.indexOf('('));
        assertEquals(3, value(edit.invisibleParameterAnnotations[4].get(0), "ordinal"));
        assertEquals(4, value(edit.invisibleParameterAnnotations[5].get(0), "ordinal"));
        AnnotationNode at = (AnnotationNode) ((List<?>) value(injection, "at")).get(0);
        assertEquals(List.of("intValue=" + BACKGROUND), value(at, "args"));
        BeforeConstant point = new BeforeConstant(new InjectionPointData(HostCompatibilityPatchesTest.context(screen.name), "CONSTANT",
            List.of("intValue=" + BACKGROUND), "", "", -1, -1, "native-tooltip-scroll", 0));
        MethodNode before = method(screen, name, THREE);
        List<AbstractInsnNode> anchors = new ArrayList<>(); point.find(THREE, before.instructions, anchors);
        assertTrue(anchors.isEmpty());
        HostTooltipCompatibility.normalize(screen, mappings, launcher(originalUtils));
        MethodNode after = method(screen, name, THREE);
        point.find(THREE, after.instructions, anchors); assertEquals(1, anchors.size(), "Scroll offset must be applied once");
        var frames = new Analyzer<>(new BasicInterpreter()).analyze(screen.name, after);
        int atIndex = after.instructions.indexOf(anchors.get(0));
        var frame = frames[atIndex];
        List<Integer> integerSlots = new ArrayList<>();
        for (int i = 0; i < frame.getLocals(); i++) if (BasicValue.INT_VALUE.equals(frame.getLocal(i))) integerSlots.add(i);
        assertEquals(List.of(2, 3, 4, 5, 6), integerSlots.subList(0, 5));
        assertEquals(5, integerSlots.get(3)); assertEquals(6, integerSlots.get(4));
        var x = after.localVariables.stream().filter(local -> local.index == 5 && after.instructions.indexOf(local.start) <= atIndex
            && after.instructions.indexOf(local.end) > atIndex).findFirst().orElseThrow();
        var y = after.localVariables.stream().filter(local -> local.index == 6 && after.instructions.indexOf(local.start) <= atIndex
            && after.instructions.indexOf(local.end) > atIndex).findFirst().orElseThrow();
        assertEquals("tooltipX", x.name); assertEquals("tooltipY", y.name);
        assertEquals(1, Arrays.stream(after.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.name.equals("m_27673420") && call.desc.equals("(Ljava/lang/String;FFI)I")).count(), "Argentum pre-text flush anchor must survive");
        assertArrayEquals(originalUtils, bytes(utils, false));
        assertEquals(4, mixin.methods.stream().filter(method -> method.name.startsWith("tooltipscroll$")).count());
        for (MethodNode method : screen.methods) if (method.name.equals(name) && method.desc.equals(THREE) || method.name.equals("drawHoveringText")) {
            new Analyzer<>(new BasicVerifier()).analyze(screen.name, method);
        }
        System.out.println("Real TooltipScroll anchor 0→1; int ordinals3/4=tooltipX(slot5)/tooltipY(slot6); Argentum font anchor1; GuiUtils unchanged");
    }

    private static ClassNode screen() {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, SCREEN, null, "java/lang/Object", null);
        for (String field : List.of("width", "height")) node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, field, "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "font", "L" + FONT + ";", null, null));
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); constructor.visitInsn(Opcodes.RETURN);
        node.methods.add(constructor);
        String fourDescriptor = "(Ljava/util/List;IIL" + FONT + ";)V";
        MethodNode three = new MethodNode(Opcodes.ACC_PROTECTED, "tooltip", THREE, null, null);
        three.visitVarInsn(Opcodes.ALOAD, 0); three.visitVarInsn(Opcodes.ALOAD, 1); three.visitVarInsn(Opcodes.ILOAD, 2); three.visitVarInsn(Opcodes.ILOAD, 3);
        three.visitVarInsn(Opcodes.ALOAD, 0); three.visitFieldInsn(Opcodes.GETFIELD, SCREEN, "font", "L" + FONT + ";");
        three.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SCREEN, "drawHoveringText", fourDescriptor, false); three.visitInsn(Opcodes.RETURN);
        node.methods.add(three);
        MethodNode four = new MethodNode(Opcodes.ACC_PROTECTED, "drawHoveringText", fourDescriptor, null, null);
        four.visitVarInsn(Opcodes.ALOAD, 1); four.visitVarInsn(Opcodes.ILOAD, 2); four.visitVarInsn(Opcodes.ILOAD, 3);
        four.visitVarInsn(Opcodes.ALOAD, 0); four.visitFieldInsn(Opcodes.GETFIELD, SCREEN, "width", "I");
        four.visitVarInsn(Opcodes.ALOAD, 0); four.visitFieldInsn(Opcodes.GETFIELD, SCREEN, "height", "I"); four.visitInsn(Opcodes.ICONST_M1);
        four.visitVarInsn(Opcodes.ALOAD, 4); four.visitMethodInsn(Opcodes.INVOKESTATIC, UTILS, "drawHoveringText", bodyDescriptor(FONT), false); four.visitInsn(Opcodes.RETURN);
        node.methods.add(four);
        ClassNode result = new ClassNode(); new ClassReader(bytes(node, true)).accept(result, 0); return result;
    }

    private static ClassNode template() {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, UTILS, null, "java/lang/Object", null);
        MethodNode body = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "drawHoveringText", bodyDescriptor(FONT), null, null);
        var done = new org.objectweb.asm.Label();
        body.visitVarInsn(Opcodes.ALOAD, 0); body.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true); body.visitJumpInsn(Opcodes.IFNE, done);
        for (int i : List.of(7, 8, 9, 12, 13)) { body.visitInsn(Opcodes.ICONST_0); body.visitVarInsn(Opcodes.ISTORE, i); }
        body.visitVarInsn(Opcodes.ILOAD, 1); body.visitIntInsn(Opcodes.BIPUSH, 12); body.visitInsn(Opcodes.IADD); body.visitVarInsn(Opcodes.ISTORE, 10);
        body.visitVarInsn(Opcodes.ILOAD, 2); body.visitIntInsn(Opcodes.BIPUSH, 12); body.visitInsn(Opcodes.ISUB); body.visitVarInsn(Opcodes.ISTORE, 11);
        body.visitLdcInsn(BACKGROUND); body.visitVarInsn(Opcodes.ISTORE, 14);
        body.visitVarInsn(Opcodes.ALOAD, 6); body.visitVarInsn(Opcodes.ALOAD, 0); body.visitVarInsn(Opcodes.ILOAD, 10); body.visitVarInsn(Opcodes.ILOAD, 11);
        body.visitVarInsn(Opcodes.ILOAD, 3); body.visitVarInsn(Opcodes.ILOAD, 4); body.visitVarInsn(Opcodes.ILOAD, 5); body.visitLdcInsn(BACKGROUND);
        body.visitMethodInsn(Opcodes.INVOKESTATIC, Recorder.class.getName().replace('.', '/'), "draw", "(Ljava/lang/Object;Ljava/util/List;IIIIII)V", false);
        body.visitLabel(done); body.visitInsn(Opcodes.RETURN); node.methods.add(body);
        return node;
    }

    private static ForgeMappings fixtureMappings() throws Exception {
        String tiny = "tiny\t2\t0\tofficial\tintermediary\nc\taxu\t" + SCREEN + "\n\tf\tI\tl\twidth\n\tf\tI\tm\theight\n\tf\tLavn;\tq\tfont\n\tm\t" + THREE + "\ta\ttooltip\nc\tavn\t" + FONT + "\n";
        String srg = "CL: axu net/minecraft/client/gui/GuiScreen\nCL: avn net/minecraft/client/gui/FontRenderer\nFD: axu/l net/minecraft/client/gui/GuiScreen/field_146294_l\nFD: axu/m net/minecraft/client/gui/GuiScreen/field_146295_m\nFD: axu/q net/minecraft/client/gui/GuiScreen/field_146289_q\nMD: axu/a " + THREE + " net/minecraft/client/gui/GuiScreen/func_146283_a " + THREE + "\n";
        return new ForgeMappings(resolver(tiny), new StringReader(srg));
    }
    private static MappingResolver resolver(String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary");
    }
    private static FabricLauncher launcher(byte[] utils) {
        return (FabricLauncher) java.lang.reflect.Proxy.newProxyInstance(FabricLauncher.class.getClassLoader(), new Class<?>[]{FabricLauncher.class}, (proxy, method, args) -> {
            if (method.getName().equals("getClassByteArray") && args[0].equals(UTILS.replace('/', '.')) && args[1].equals(false)) return utils;
            throw new UnsupportedOperationException(method.getName());
        });
    }
    private static String bodyDescriptor(String font) { return "(Ljava/util/List;IIIIIL" + font + ";)V"; }
    private static MethodNode method(ClassNode node, String name, String descriptor) { return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow(); }
    private static byte[] bytes(ClassNode node, boolean frames) { ClassWriter writer = new ClassWriter(frames ? ClassWriter.COMPUTE_FRAMES : 0); node.accept(writer); return writer.toByteArray(); }
    private static ClassNode readClass(Path jar, String name) throws Exception {
        ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node;
    }
    private static ClassNode runtime(ClassNode source, ForgeMappings mappings) { ClassNode target = new ClassNode(); source.accept(new ClassRemapper(target, mappings.officialToRuntime())); return target; }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1); return null;
    }
}
