package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.adjuster.tools.type.RemappableAnnotationNode;
import com.bawnorton.mixinsquared.reflection.StateExtension;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
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
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class HostGuiBackgroundCompatibilityTest {
    private static final String MIXIN = "eu/midnightdust/blur/mixin/ScreenMixin";
    private static final String BRIDGE = Type.getInternalName(HostGuiBackgroundCompatibility.class);
    private static final String GUI = "fixture/gui/Screen";
    private static final String CALLBACK = Type.getInternalName(CallbackInfo.class);
    private static final String EVENT = "net/minecraftforge/fml/common/eventhandler/Event";
    private static final String EVENT_BUS = "net/minecraftforge/fml/common/eventhandler/EventBus";
    private static final String GUI_EVENT = "net/minecraftforge/client/event/GuiScreenEvent";
    private static final String BACKGROUND = GUI_EVENT + "$BackgroundDrawnEvent";
    private static ForgeMappings mappings;
    private static String runtimeGui;
    private static ClassNode nativeMixin;
    private static HostGuiBackgroundCompatibility adjuster;

    @BeforeAll static void setup() throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(new StringReader(Files.readString(Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny"))), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var reader = Files.newBufferedReader(Path.of("../build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), reader); }
        runtimeGui = mappings.classFromSrg("net/minecraft/client/gui/GuiScreen");
        assertEquals("net/minecraft/unmapped/C_85740840", runtimeGui);
        assertEquals("m_34695786", mappings.methodFromSrg("net/minecraft/client/gui/GuiScreen", "func_146276_q_", "()V"));
        nativeMixin = read(blur(), MIXIN); adjuster = new HostGuiBackgroundCompatibility(mappings);
    }

    @Test void adjustsOnlyTheActualNativeCancelBranchAndPreservesEveryAnnotationSetting() throws Exception {
        MethodNode handler = handler(); AdjustableAnnotationNode annotation = adjustable(injector(handler));
        byte[] metadata = annotationBytes(annotation); int locals = handler.maxLocals, count = code(handler).size();
        assertEquals(List.of("m_34695786()V"), value(annotation, "method"));
        assertSame(annotation, adjuster.adjust(List.of(runtimeGui), MIXIN.replace('/', '.'), handler, annotation));
        assertEquals(count + 3, code(handler).size()); assertEquals(locals, handler.maxLocals); assertArrayEquals(metadata, annotationBytes(annotation));
        List<MethodInsnNode> hooks = calls(handler).stream().filter(call -> call.owner.equals(BRIDGE)).toList(); assertEquals(1, hooks.size());
        assertEquals("post", hooks.get(0).name); assertEquals("(Ljava/lang/Object;Ljava/lang/Class;)V", hooks.get(0).desc);
        assertTrue(hooks.get(0).getPrevious() instanceof LdcInsnNode literal && literal.cst.equals(Type.getObjectType(runtimeGui)));
        new Analyzer<>(new BasicVerifier()).analyze(nativeMixin.name, handler);
        byte[] once = methodBytes(handler); adjuster.adjust(List.of(runtimeGui), MIXIN.replace('/', '.'), handler, annotation); assertArrayEquals(once, methodBytes(handler));
        ClassNode renderer = read(blur(), "eu/midnightdust/blur/BlurRenderer"); byte[] rendererBefore = bytes(renderer);
        for (MethodNode method : renderer.methods) if (method.visibleAnnotations != null) for (AnnotationNode other : method.visibleAnnotations)
            adjuster.adjust(List.of(runtimeGui), renderer.name, method, adjustable(other));
        assertArrayEquals(rendererBefore, bytes(renderer));
        ClassNode install = library(Type.getInternalName(HostMixinCompatibility.class));
        assertTrue(Arrays.stream(method(install, "install").instructions.toArray()).anyMatch(instruction -> instruction instanceof TypeInsnNode type && type.desc.equals(BRIDGE)));
    }

    @Test void postsOneRealForgeBackgroundEventForBlurAndTheOriginalTailForEveryNonCancelRoute() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            List<Object> events = new ArrayList<>(); fixture.listen(events::add);
            for (int mode = 0; mode < 5; mode++) {
                OwnBlur.reset(); OwnBlur.enabled = mode != 0; OwnBlur.target = mode != 1; OwnBlur.gradient = mode != 2; OwnBlur.opacity = mode == 3 ? 0 : 1;
                Object screen = fixture.screen.getConstructor().newInstance(); events.clear();
                fixture.screen.getMethod("drawFixtureBackground").invoke(screen);
                boolean blurred = mode >= 3;
                assertEquals(1, events.size()); Object event = events.get(0);
                assertEquals(BACKGROUND.replace('/', '.'), event.getClass().getName()); assertSame(screen, event.getClass().getField("gui").get(event));
                assertEquals(500, event.getClass().getMethod("getMouseX").invoke(event)); assertEquals(389, event.getClass().getMethod("getMouseY").invoke(event));
                assertEquals(blurred ? 0 : 1, fixture.screen.getField("vanillaDraws").getInt(screen));
                CallbackInfo callback = (CallbackInfo) fixture.screen.getField("lastCallback").get(screen); assertEquals(blurred, callback.isCancelled());
                assertEquals(1, OwnBlur.calls); assertSame(screen, OwnBlur.screen);
            }
        }
    }

    @Test void avoidsTheForgeApiOnFalseBlurAndPreservesCancelAndRendererExceptions() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            OwnBlur.reset(); OwnBlur.enabled = false; Object screen = fixture.screen.getConstructor().newInstance();
            var nativeHandler = fixture.screen.getDeclaredMethod("blur$renderBackground", CallbackInfo.class); nativeHandler.setAccessible(true);
            CallbackInfo normal = new CallbackInfo("fixture", true); nativeHandler.invoke(screen, normal); assertFalse(normal.isCancelled()); assertEquals(0, fixture.forgeLoads);
            OwnBlur.enabled = true; RuntimeException renderFailure = new RuntimeException("native renderer"); OwnBlur.failure = renderFailure;
            InvocationTargetException render = assertThrows(InvocationTargetException.class, () -> nativeHandler.invoke(screen, new CallbackInfo("fixture", true)));
            assertSame(renderFailure, render.getCause()); assertEquals(0, fixture.forgeLoads);
            OwnBlur.failure = null;
            InvocationTargetException cancel = assertThrows(InvocationTargetException.class, () -> nativeHandler.invoke(screen, new CallbackInfo("fixture", false)));
            assertInstanceOf(CancellationException.class, cancel.getCause()); assertEquals(0, fixture.forgeLoads);
        }
    }

    @Test void retainsTheActualEventBusExceptionHandlerAndPropagatesTheOriginalListenerFailure() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            RuntimeException failure = new RuntimeException("background listener"); List<Object> delivered = new ArrayList<>();
            fixture.listen(event -> { delivered.add(event); throw failure; }); fixture.listen(event -> fail("The bus continued after a listener failure"));
            OwnBlur.reset(); Object screen = fixture.screen.getConstructor().newInstance();
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> fixture.screen.getMethod("drawFixtureBackground").invoke(screen));
            assertSame(failure, thrown.getCause()); assertEquals(1, fixture.exceptionCalls); assertSame(failure, fixture.exception);
            assertEquals(1, delivered.size()); assertSame(delivered.get(0), fixture.exceptionEvent);
            assertTrue(((CallbackInfo) fixture.screen.getField("lastCallback").get(screen)).isCancelled());
            assertEquals(0, fixture.screen.getField("vanillaDraws").getInt(screen));
        }
    }

    @Test void leavesOtherMixinsAndTargetsUntouchedAndRejectsChangedNativeMetadataBeforeMutation() throws Exception {
        MethodNode handler = handler(); AdjustableAnnotationNode annotation = adjustable(injector(handler)); byte[] before = methodBytes(handler);
        adjuster.adjust(List.of("fixture/Other"), MIXIN, handler, annotation); assertArrayEquals(before, methodBytes(handler));
        adjuster.adjust(List.of(runtimeGui), "fixture.OtherMixin", handler, annotation); assertArrayEquals(before, methodBytes(handler));
        annotation.set("require", 2); byte[] annotationBefore = annotationBytes(annotation);
        adjuster.adjust(List.of(runtimeGui), MIXIN, handler, annotation); assertArrayEquals(annotationBefore, annotationBytes(annotation)); assertEquals(2, value(annotation, "require"));
        MethodNode wrong = handler(); AdjustableAnnotationNode changed = adjustable(injector(wrong)); changed.set("method", List.of("unknown()V")); byte[] wrongBefore = methodBytes(wrong);
        assertThrows(IllegalStateException.class, () -> adjuster.adjust(List.of(runtimeGui), MIXIN, wrong, changed)); assertArrayEquals(wrongBefore, methodBytes(wrong));
        MethodNode altered = handler(); MethodInsnNode cancel = calls(altered).stream().filter(call -> call.name.equals("cancel")).findFirst().orElseThrow(); cancel.name = "unknown";
        byte[] body = methodBytes(altered); assertThrows(IllegalStateException.class, () -> adjuster.adjust(List.of(runtimeGui), MIXIN, altered, adjustable(injector(altered)))); assertArrayEquals(body, methodBytes(altered));
    }

    @Test void reachesThePinnedMixinStateCloneWithTheConditionalHookAndDynamicGuiTypeIntact() throws Exception {
        ClassNode template = new ClassNode(); template.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/BackgroundMixin", null, "java/lang/Object", null); template.methods.add(handler());
        Class<?> info = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo"), stateType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$State"), classInfo = Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo");
        var object = classInfo.getDeclaredField("OBJECT"); object.setAccessible(true);
        var constructor = stateType.getDeclaredConstructor(info, ClassNode.class, classInfo); constructor.setAccessible(true); Object state = constructor.newInstance(null, template, object.get(null));
        var clone = stateType.getDeclaredMethod("createClassNode", int.class); clone.setAccessible(true); ClassNode node = (ClassNode) clone.invoke(state, 0);
        MethodNode handler = node.methods.get(0); AdjustableAnnotationNode annotation = adjustable(injector(handler));
        adjuster.adjust(List.of(runtimeGui), MIXIN, handler, annotation); handler.visibleAnnotations.set(handler.visibleAnnotations.indexOf(injector(handler)), annotation);
        StateExtension.tryAs(state, extension -> extension.setClassNode(node)); ClassNode applied = (ClassNode) clone.invoke(state, 0);
        assertEquals(1, calls(applied.methods.get(0)).stream().filter(call -> call.owner.equals(BRIDGE)).count());
        assertEquals(0, calls(template.methods.get(0)).stream().filter(call -> call.owner.equals(BRIDGE)).count());
        assertEquals(List.of("m_34695786()V"), value(injector(applied.methods.get(0)), "method"));
        assertTrue(code(applied.methods.get(0)).stream().anyMatch(instruction -> instruction instanceof LdcInsnNode literal && literal.cst.equals(Type.getObjectType(runtimeGui))));
    }

    public static final class OwnGame { public static final OwnGame INSTANCE = new OwnGame(); public int d = 1920, e = 1080; }
    public static final class OwnScale { public OwnScale(OwnGame game) { } public int a() { return 960; } public int b() { return 540; } }
    public static final class OwnMouse { public static int getX() { return 1000; } public static int getY() { return 300; } }
    public static final class OwnBlur {
        static boolean enabled, target, gradient; static float opacity; static int calls; static Object screen; static RuntimeException failure;
        static void reset() { enabled = target = gradient = true; opacity = 1; calls = 0; screen = null; failure = null; }
        public static boolean renderBackground(Object value) { calls++; screen = value; if (failure != null) throw failure; return enabled && target && gradient; }
    }

    private static final class Fixture extends URLClassLoader {
        final Map<String, byte[]> definitions = new LinkedHashMap<>(); final Class<?> screen; Object bus; Class<?> eventType; int forgeLoads, exceptionCalls; Throwable exception; Object exceptionEvent;
        Fixture(boolean background) throws Exception {
            super(new URL[]{forge().toUri().toURL(), cached("com.google.guava/guava/17.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/guava-17.0.jar").toUri().toURL(), cached("org.apache.logging.log4j/log4j-api/2.19.0/ea1b37f38c327596b216542bc636cfdc0b8036fa/log4j-api-2.19.0.jar").toUri().toURL()}, HostGuiBackgroundCompatibilityTest.class.getClassLoader());
            definitions.put(GUI.replace('/', '.'), guiFixture(background));
            for (String name : List.of(GUI_EVENT, BACKGROUND)) {
                ClassNode event = read(forge(), name), mapped = new ClassNode(); event.accept(new ClassRemapper(mapped, new Remapper(Opcodes.ASM9) {
                    @Override public String map(String type) { return switch (type) {
                        case "axu" -> GUI;
                        case "ave" -> Type.getInternalName(OwnGame.class);
                        case "avr" -> Type.getInternalName(OwnScale.class);
                        case "org/lwjgl/input/Mouse" -> Type.getInternalName(OwnMouse.class);
                        default -> type;
                    }; }
                })); definitions.put(name.replace('/', '.'), bytes(mapped));
            }
            ClassNode holder = new ClassNode(); holder.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/minecraftforge/common/MinecraftForge", null, "java/lang/Object", null);
            holder.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "EVENT_BUS", "L" + EVENT_BUS + ";", null, null)); definitions.put(holder.name.replace('/', '.'), bytes(holder));
            screen = loadClass(GUI.replace('/', '.'));
            if (background) {
                eventType = loadClass(EVENT.replace('/', '.')); Class<?> busType = loadClass(EVENT_BUS.replace('/', '.')), error = loadClass("net.minecraftforge.fml.common.eventhandler.IEventExceptionHandler");
                Object handler = Proxy.newProxyInstance(this, new Class<?>[]{error}, (proxy, method, args) -> { if (method.getName().equals("handleException")) { exceptionCalls++; exceptionEvent = args[1]; exception = (Throwable) args[4]; } return null; });
                bus = busType.getConstructor(error).newInstance(handler); loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").set(null, bus);
            }
        }
        void listen(Consumer<Object> listener) throws Exception {
            Object event = eventType.getConstructor().newInstance(), list = eventType.getMethod("getListenerList").invoke(event);
            var id = bus.getClass().getDeclaredField("busID"); id.setAccessible(true);
            Class<?> type = loadClass("net.minecraftforge.fml.common.eventhandler.IEventListener"), priority = loadClass("net.minecraftforge.fml.common.eventhandler.EventPriority");
            Object callback = Proxy.newProxyInstance(this, new Class<?>[]{type}, (proxy, method, args) -> { if (method.getName().equals("invoke")) listener.accept(args[0]); return null; });
            list.getClass().getMethod("register", int.class, priority, type).invoke(list, id.getInt(bus), priority.getField("NORMAL").get(null), callback);
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    byte[] bytes = definitions.get(name);
                    if (bytes != null) { if (name.startsWith("net.minecraftforge.")) forgeLoads++; type = defineClass(name, bytes, 0, bytes.length); }
                    else if (name.startsWith("net.minecraftforge.") || name.startsWith("com.google.common.") || name.startsWith("org.apache.logging.log4j.")) { if (name.startsWith("net.minecraftforge.")) forgeLoads++; type = findClass(name); }
                    else type = super.loadClass(name, false);
                }
                if (resolve) resolveClass(type); return type;
            }
        }
    }

    private static byte[] guiFixture(boolean background) {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, GUI, null, "java/lang/Object", null);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "j", Type.getDescriptor(OwnGame.class), null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "vanillaDraws", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "lastCallback", "L" + CALLBACK + ";", null, null));
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(OwnGame.class), "INSTANCE", Type.getDescriptor(OwnGame.class)); constructor.visitFieldInsn(Opcodes.PUTFIELD, GUI, "j", Type.getDescriptor(OwnGame.class)); constructor.visitInsn(Opcodes.RETURN); node.methods.add(constructor);
        MethodNode nativeHandler = handler(); adjuster.adjust(List.of(runtimeGui), MIXIN, nativeHandler, adjustable(injector(nativeHandler)));
        ClassNode holder = new ClassNode(); holder.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, GUI, null, "java/lang/Object", null); holder.methods.add(nativeHandler);
        ClassNode mapped = new ClassNode(); holder.accept(new ClassRemapper(mapped, new Remapper(Opcodes.ASM9) {
            @Override public String map(String type) { if (type.equals(MIXIN) || type.equals(runtimeGui)) return GUI; if (type.equals("eu/midnightdust/blur/BlurRenderer")) return Type.getInternalName(OwnBlur.class); return type; }
        }));
        MethodNode handler = mapped.methods.get(0); for (MethodInsnNode call : calls(handler)) if (call.owner.equals(Type.getInternalName(OwnBlur.class))) call.desc = "(Ljava/lang/Object;)Z";
        handler.localVariables = null; node.methods.add(handler);
        if (background) {
            MethodNode draw = new MethodNode(Opcodes.ACC_PUBLIC, "drawFixtureBackground", "()V", null, null);
            draw.visitTypeInsn(Opcodes.NEW, CALLBACK); draw.visitInsn(Opcodes.DUP); draw.visitLdcInsn("background"); draw.visitInsn(Opcodes.ICONST_1); draw.visitMethodInsn(Opcodes.INVOKESPECIAL, CALLBACK, "<init>", "(Ljava/lang/String;Z)V", false); draw.visitVarInsn(Opcodes.ASTORE, 1);
            draw.visitVarInsn(Opcodes.ALOAD, 0); draw.visitVarInsn(Opcodes.ALOAD, 1); draw.visitFieldInsn(Opcodes.PUTFIELD, GUI, "lastCallback", "L" + CALLBACK + ";");
            draw.visitVarInsn(Opcodes.ALOAD, 0); draw.visitVarInsn(Opcodes.ALOAD, 1); draw.visitMethodInsn(Opcodes.INVOKESPECIAL, GUI, handler.name, handler.desc, false);
            draw.visitVarInsn(Opcodes.ALOAD, 1); draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CALLBACK, "isCancelled", "()Z", false); LabelNode original = new LabelNode(); draw.instructions.add(new JumpInsnNode(Opcodes.IFEQ, original)); draw.visitInsn(Opcodes.RETURN); draw.instructions.add(original);
            draw.visitVarInsn(Opcodes.ALOAD, 0); draw.visitInsn(Opcodes.DUP); draw.visitFieldInsn(Opcodes.GETFIELD, GUI, "vanillaDraws", "I"); draw.visitInsn(Opcodes.ICONST_1); draw.visitInsn(Opcodes.IADD); draw.visitFieldInsn(Opcodes.PUTFIELD, GUI, "vanillaDraws", "I");
            draw.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS", "L" + EVENT_BUS + ";"); draw.visitTypeInsn(Opcodes.NEW, BACKGROUND); draw.visitInsn(Opcodes.DUP); draw.visitVarInsn(Opcodes.ALOAD, 0); draw.visitMethodInsn(Opcodes.INVOKESPECIAL, BACKGROUND, "<init>", "(L" + GUI + ";)V", false); draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT_BUS, "post", "(L" + EVENT + ";)Z", false); draw.visitInsn(Opcodes.POP); draw.visitInsn(Opcodes.RETURN); node.methods.add(draw);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static Path blur() { return ForgeTestFixtures.nativeJar("LegacyGuiBlur-6.3.4+mc1.8.9-fabric.jar").toAbsolutePath().normalize(); }
    private static Path forge() { return Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar").toAbsolutePath().normalize(); }
    private static Path cached(String name) { Path path = Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1", name); assertTrue(Files.isRegularFile(path)); return path; }
    private static ClassNode read(Path path, String name) throws Exception { ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static ClassNode library(String name) throws Exception { ClassNode node = new ClassNode(); try (var input = HostGuiBackgroundCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode handler() { MethodNode source = method(nativeMixin, "blur$renderBackground"), result = new MethodNode(source.access, source.name, source.desc, source.signature, source.exceptions.toArray(String[]::new)); source.accept(result); return result; }
    private static AnnotationNode injector(MethodNode handler) { return handler.visibleAnnotations.stream().filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow(); }
    private static AdjustableAnnotationNode adjustable(AnnotationNode annotation) { AdjustableAnnotationNode node = AdjustableAnnotationNode.fromNode(annotation); if (node instanceof RemappableAnnotationNode remappable) remappable.setRemapper(value -> value.applyRefmap(UnaryOperator.identity())); return node; }
    private static Object value(AnnotationNode annotation, String key) { if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1); return null; }
    private static List<AbstractInsnNode> code(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
    private static List<MethodInsnNode> calls(MethodNode method) { return code(method).stream().filter(instruction -> instruction instanceof MethodInsnNode).map(MethodInsnNode.class::cast).toList(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(MethodNode method) { ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "fixture/Method", null, "java/lang/Object", null); method.accept(writer); writer.visitEnd(); return writer.toByteArray(); }
    private static byte[] annotationBytes(AnnotationNode annotation) { MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "metadata", "()V", null, null); method.visibleAnnotations = new ArrayList<>(List.of(annotation)); method.visitInsn(Opcodes.RETURN); return methodBytes(method); }
}
