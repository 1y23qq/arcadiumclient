package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.adjuster.tools.type.RemappableAnnotationNode;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;
import org.spongepowered.asm.mixin.injection.struct.Target;

import static org.junit.jupiter.api.Assertions.*;

class HostCreativeTabCompatibilityTest {
    private static final String MIXIN = "dev/rdh/argentum/mixin/features/hud/CreativeInventoryScreenMixin";
    private static final String PREFIX = "textures/gui/container/creative_inventory/tab_";
    private static final String GENERATED = "io/github/arcadiumclient/forgecompat/fixture/CreativeBatch";
    private static ForgeMappings mappings;
    private static ClassNode forge, vanilla, nativeMixin;
    private static String creative, render, textures, resource, bind;

    @BeforeAll static void setup(@TempDir Path directory) throws Exception {
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Path api = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(Files.readString(client.resolveSibling("calamus-gen2-intermediary-v2.tiny"))), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var reader = Files.newBufferedReader(Path.of("../build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), reader); }
        Path patches = directory.resolve("patches.jar");
        try (var input = HostCreativeTabCompatibilityTest.class.getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) { assertNotNull(input); Files.copy(input, patches); }
        Path patched = new ForgeGamePatcher().patch(client, patches, directory.resolve("patched"));
        ForgeJarTransformer transformer = new ForgeJarTransformer(); transformer.index(patched, mappings, true); transformer.index(api, mappings, true);
        forge = new ClassNode(); read(patched, "ayu.class").accept(new ClassRemapper(forge, mappings.officialToRuntime()));
        vanilla = new ClassNode(); read(client, "ayu.class").accept(new ClassRemapper(vanilla, mappings.officialToRuntime()));
        nativeMixin = read(ForgeTestFixtures.nativeJar("argentum-1.0.1.jar"), MIXIN + ".class");
        creative = mappings.classFromSrg("net/minecraft/client/gui/inventory/GuiContainerCreative"); render = mappings.methodFromSrg("net/minecraft/client/gui/inventory/GuiContainerCreative", "func_146976_a", "(FII)V");
        textures = mappings.classFromSrg("net/minecraft/client/renderer/texture/TextureManager"); resource = mappings.classFromSrg("net/minecraft/util/ResourceLocation");
        bind = mappings.methodFromSrg("net/minecraft/client/renderer/texture/TextureManager", "func_110577_a", "(Lnet/minecraft/util/ResourceLocation;)V");
        assertEquals("net/minecraft/unmapped/C_70699216", creative); assertEquals("m_98661498", render);
    }

    @Test void relocatesTheRealNativeSelectorToTheActualPanelUsingThePinnedMixinSliceEngine() throws Exception {
        AtomicInteger reads = new AtomicInteger(); HostCreativeTabCompatibility adjuster = new HostCreativeTabCompatibility(mappings, name -> { reads.incrementAndGet(); return bytes(forge); });
        MethodNode handler = method(copy(nativeMixin), "argentum$drawTabBatch"); AdjustableAnnotationNode annotation = adjustable(injector(handler));
        byte[] body = bodyBytes(handler), originalGame = bytes(forge); Object require = value(annotation, "require");
        MethodInsnNode old = assertInstanceOf(MethodInsnNode.class, selected(forge, draw(forge), annotation).get(0));
        assertEquals(1, textureCalls(draw(forge)).indexOf(old)); assertEquals(1, selected(vanilla, draw(vanilla), annotation).size());
        adjuster.adjust(List.of(creative), MIXIN, handler, annotation);
        List<AbstractInsnNode> found = selected(forge, draw(forge), annotation); assertEquals(1, found.size());
        assertEquals(3, textureCalls(draw(forge)).indexOf(found.get(0))); assertEquals(3, adjuster.validatedOrdinal());
        assertNotSame(old, found.get(0)); assertEquals(require, value(annotation, "require")); assertEquals(0, value(at(annotation), "ordinal"));
        assertEquals("arcadium$creativePanel", value(at(annotation), "slice")); assertEquals(1, reads.get());
        assertArrayEquals(body, bodyBytes(handler)); assertArrayEquals(originalGame, bytes(forge));
        byte[] once = annotationBytes(annotation); adjuster.adjust(List.of(creative), MIXIN, handler, annotation); assertArrayEquals(once, annotationBytes(annotation)); assertEquals(1, reads.get());
        System.out.println("Actual Argentum ordinal1 hit Forge page-only bind; validated main bind ordinal3 selected by constant slice, handler/body/require unchanged");
    }

    @Test void keepsSelectingThePanelWhenAnotherTextureBindIsInsertedBeforeThePrefix() throws Exception {
        ClassNode extra = copy(forge); MethodNode method = draw(extra); MethodInsnNode first = textureCalls(method).get(0);
        List<AbstractInsnNode> real = code(method); int end = real.indexOf(first); InsnList earlier = new InsnList();
        for (int i = end - 4; i <= end; i++) earlier.add(real.get(i).clone(new java.util.HashMap<>()));
        method.instructions.insertBefore(method.instructions.getFirst(), earlier); method.maxStack = Math.max(method.maxStack, 3);
        HostCreativeTabCompatibility adjuster = new HostCreativeTabCompatibility(mappings, name -> bytes(extra));
        MethodNode handler = method(copy(nativeMixin), "argentum$drawTabBatch"); AdjustableAnnotationNode annotation = adjustable(injector(handler));
        adjuster.adjust(List.of(creative), MIXIN, handler, annotation);
        List<AbstractInsnNode> found = selected(extra, method, annotation); assertEquals(1, found.size()); assertEquals(4, textureCalls(method).indexOf(found.get(0))); assertEquals(4, adjuster.validatedOrdinal());
        new Analyzer<>(new BasicVerifier()).analyze(extra.name, method);
    }

    @Test void rejectsUnknownPrefixesControlFlowAndMetadataBeforeChangingTheAnnotation() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            ClassNode altered = copy(forge); MethodNode draw = draw(altered);
            LdcInsnNode prefix = code(draw).stream().filter(instruction -> instruction instanceof LdcInsnNode constant && PREFIX.equals(constant.cst)).map(LdcInsnNode.class::cast).findFirst().orElseThrow();
            if (mode == 0) prefix.cst = "unknown";
            else if (mode == 1) { draw.instructions.insertBefore(prefix, new LdcInsnNode(PREFIX)); draw.instructions.insertBefore(prefix, new InsnNode(Opcodes.POP)); }
            else {
                AbstractInsnNode exit = code(draw).stream().filter(instruction -> instruction.getOpcode() == Opcodes.RETURN).findFirst().orElseThrow();
                LabelNode end = new LabelNode(); draw.instructions.insertBefore(exit, end);
                if (mode == 2) { InsnList bypass = new InsnList(); bypass.add(new InsnNode(Opcodes.ICONST_0)); bypass.add(new JumpInsnNode(Opcodes.IFEQ, end)); draw.instructions.insertBefore(draw.instructions.getFirst(), bypass); }
                else { LabelNode begin = new LabelNode(); draw.instructions.insertBefore(draw.instructions.getFirst(), begin); draw.instructions.insertBefore(exit, new JumpInsnNode(Opcodes.GOTO, begin)); }
            }
            MethodNode handler = method(copy(nativeMixin), "argentum$drawTabBatch"); AdjustableAnnotationNode annotation = adjustable(injector(handler)); byte[] before = annotationBytes(annotation);
            HostCreativeTabCompatibility adjuster = new HostCreativeTabCompatibility(mappings, name -> bytes(altered));
            assertThrows(IllegalStateException.class, () -> adjuster.adjust(List.of(creative), MIXIN, handler, annotation)); assertArrayEquals(before, annotationBytes(annotation));
        }
        MethodNode handler = method(copy(nativeMixin), "argentum$drawTabBatch"); AdjustableAnnotationNode annotation = adjustable(injector(handler));
        put(at(annotation), "ordinal", 2); byte[] before = annotationBytes(annotation);
        assertThrows(IllegalStateException.class, () -> new HostCreativeTabCompatibility(mappings, name -> bytes(forge)).adjust(List.of(creative), MIXIN, handler, annotation)); assertArrayEquals(before, annotationBytes(annotation));
    }

    @Test void leavesOtherNativeHandlersMixinsAndTargetsUntouchedWithoutReadingMinecraft() throws Exception {
        HostCreativeTabCompatibility adjuster = new HostCreativeTabCompatibility(mappings, name -> { throw new AssertionError("Unrelated handler read the game"); });
        for (MethodNode handler : nativeMixin.methods) if (!handler.name.equals("argentum$drawTabBatch") && handler.visibleAnnotations != null) for (AnnotationNode raw : handler.visibleAnnotations) {
            AdjustableAnnotationNode annotation = adjustable(raw); byte[] before = annotationBytes(annotation); adjuster.adjust(List.of(creative), MIXIN, handler, annotation); assertArrayEquals(before, annotationBytes(annotation));
        }
        MethodNode handler = method(copy(nativeMixin), "argentum$drawTabBatch"); AdjustableAnnotationNode annotation = adjustable(injector(handler)); byte[] before = annotationBytes(annotation);
        adjuster.adjust(List.of("fixture/Other"), MIXIN, handler, annotation); adjuster.adjust(List.of(creative), "fixture.Other", handler, annotation); assertArrayEquals(before, annotationBytes(annotation));
    }

    @Test void reproducesPageZeroMissingFlushAndRunsTheUnchangedNativeBatchOnBothForgePages() throws Exception {
        try (Fixture broken = new Fixture(false)) { broken.render(0, "custom.png"); assertEquals(0, OwnDraw.flushes); assertFalse(OwnDraw.events.contains("immediate:selected")); }
        for (int page : List.of(0, 1)) try (Fixture fixed = new Fixture(true)) {
            fixed.render(page, "custom_" + page + ".png");
            assertEquals(1, OwnDraw.flushes); assertEquals(page == 0 ? 1 : 3, OwnDraw.flushedQuads); assertEquals(1, OwnDraw.createdBatches);
            assertTrue(OwnDraw.events.contains("immediate:selected")); assertFalse(fixed.type.getField("argentum$batchingTabs").getBoolean(fixed.screen));
            int flush = OwnDraw.events.indexOf("flush"), panel = OwnDraw.events.indexOf("panel:" + PREFIX + "custom_" + page + ".png");
            assertTrue(flush >= 0 && flush < panel); assertEquals("bind:" + PREFIX + "custom_" + page + ".png", OwnDraw.events.get(panel - 1));
            assertEquals(page == 0 ? 1 : 3, OwnDraw.events.subList(0, flush).stream().filter(value -> value.equals("queue")).count());
        }
    }

    public record OwnResource(String name) { }
    public static final class OwnDraw { public static final List<String> events = new ArrayList<>(); public static int flushes, flushedQuads, createdBatches; public static void reset() { events.clear(); flushes = flushedQuads = createdBatches = 0; } }
    public static final class OwnTextures { public OwnResource bound; public void bind(OwnResource texture) { bound = texture; OwnDraw.events.add("bind:" + texture.name); } }
    public static final class OwnClient { public static final OwnClient INSTANCE = new OwnClient(); public final OwnTextures manager = new OwnTextures(); public static OwnClient instance() { return INSTANCE; } public OwnTextures textures() { return manager; } }
    public static final class OwnGl { public static void state() { OwnDraw.events.add("state"); } public static void color(float r, float g, float b, float a) { if (r != 1 || g != 1 || b != 1 || a != 1) throw new AssertionError("Unexpected batch color"); } }
    public static final class OwnHudBatch { public static OwnTextured textured(int capacity) { assertEquals(4096, capacity); OwnDraw.createdBatches++; return new OwnTextured(); } }
    public static final class OwnTextured { public int queued; public void quad(float x, float y, float u, float v, int w, int h, int tw, int th, float sw, float sh, float z) { assertEquals(w, tw); assertEquals(h, th); assertEquals(256, sw); assertEquals(256, sh); queued++; OwnDraw.events.add("queue"); } public void draw() { assertEquals("tabs", OwnClient.INSTANCE.manager.bound.name); OwnDraw.flushes++; OwnDraw.flushedQuads += queued; queued = 0; OwnDraw.events.add("flush"); } }
    public static final class OwnTab { public final String name; public OwnTab(String name) { this.name = name; } public String background() { return name; } }
    public static class OwnScreen {
        public static final OwnResource TABS = new OwnResource("tabs"); public OwnTextured argentum$tabBatch; public boolean argentum$batchingTabs; public float z; public int page; public OwnTab selected = new OwnTab("custom.png");
        private boolean argentum$batchTabIcon(OwnScreen screen, int x, int y, int u, int v, int width, int height) { throw new AssertionError("Actual native batch handler missing"); }
        private void argentum$openTabBatch(float delta, int x, int y, CallbackInfo ci) { throw new AssertionError("Actual native HEAD handler missing"); }
        private void argentum$drawTabBatch(float delta, int x, int y, CallbackInfo ci) { throw new AssertionError("Actual native flush handler missing"); }
        private void tab(String name) { if (argentum$batchTabIcon(this, 1, 2, 3, 4, 28, 32)) OwnDraw.events.add("immediate:" + name); }
        public void render(float delta, int x, int y) {
            OwnTextures textures = OwnClient.instance().textures(); textures.bind(TABS); tab("unselected");
            if (page != 0) { textures.bind(TABS); tab("extra-search"); textures.bind(TABS); tab("extra-inventory"); }
            textures.bind(new OwnResource(new StringBuilder().append(PREFIX).append(selected.background()).toString()));
            OwnDraw.events.add("panel:" + textures.bound.name()); textures.bind(TABS); tab("selected");
        }
    }
    private static final class Fixture implements AutoCloseable {
        final Class<?> type; final Object screen;
        Fixture(boolean fixed) throws Exception {
            ClassNode node = owned(OwnScreen.class); ClassNode projected = projectHandlers();
            for (String name : List.of("argentum$batchTabIcon", "argentum$openTabBatch", "argentum$drawTabBatch")) node.methods.set(node.methods.indexOf(method(node, name)), method(projected, name));
            MethodNode body = method(node, "render"); MethodNode sourceHandler = method(copy(nativeMixin), "argentum$drawTabBatch"); AdjustableAnnotationNode annotation = adjustable(injector(sourceHandler));
            if (fixed) new HostCreativeTabCompatibility(mappings, name -> bytes(forge)).adjust(List.of(creative), MIXIN, sourceHandler, annotation);
            put(at(annotation), "target", "L" + Type.getInternalName(OwnTextures.class) + ";bind(L" + Type.getInternalName(OwnResource.class) + ";)V");
            List<AbstractInsnNode> points = selected(node, body, annotation); assertEquals(1, points.size());
            body.instructions.insertBefore(points.get(0), callback(node.name, "argentum$drawTabBatch")); body.instructions.insertBefore(body.instructions.getFirst(), callback(node.name, "argentum$openTabBatch"));
            ClassNode renamed = new ClassNode(); node.accept(new ClassRemapper(renamed, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return name.equals(node.name) ? GENERATED : name; } })); renamed.nestHostClass = null;
            byte[] data = bytes(renamed); ClassLoader loader = new ClassLoader(HostCreativeTabCompatibilityTest.class.getClassLoader()) { Class<?> define() { return defineClass(GENERATED.replace('/', '.'), data, 0, data.length); } };
            var define = loader.getClass().getDeclaredMethod("define"); define.setAccessible(true); type = (Class<?>) define.invoke(loader); screen = type.getConstructor().newInstance();
        }
        void render(int page, String background) throws Exception { OwnDraw.reset(); type.getField("page").setInt(screen, page); type.getField("selected").set(screen, new OwnTab(background)); try { type.getMethod("render", float.class, int.class, int.class).invoke(screen, 0.5f, 1, 2); } catch (InvocationTargetException failure) { throw new AssertionError("Owned native batching fixture failed", failure.getCause()); } }
        @Override public void close() { OwnDraw.reset(); }
    }
    private static ClassNode projectHandlers() {
        Map<String, String> classes = Map.ofEntries(Map.entry(nativeMixin.name, Type.getInternalName(OwnScreen.class)), Map.entry(creative, Type.getInternalName(OwnScreen.class)),
            Map.entry(resource, Type.getInternalName(OwnResource.class)), Map.entry(textures, Type.getInternalName(OwnTextures.class)), Map.entry("net/minecraft/client/Minecraft", Type.getInternalName(OwnClient.class)),
            Map.entry(mappings.classFromSrg("net/minecraft/client/renderer/GlStateManager"), Type.getInternalName(OwnGl.class)),
            Map.entry("dev/rdh/argentum/api/render/gui/hud/HudBatch", Type.getInternalName(OwnHudBatch.class)), Map.entry("dev/rdh/argentum/api/render/gui/hud/HudBatch$Textured", Type.getInternalName(OwnTextured.class)));
        String instance = mappings.methodFromSrg("net/minecraft/client/Minecraft", "func_71410_x", "()Lnet/minecraft/client/Minecraft;"), manager = mappings.methodFromSrg("net/minecraft/client/Minecraft", "func_110434_K", "()Lnet/minecraft/client/renderer/texture/TextureManager;");
        String state = mappings.methodFromSrg("net/minecraft/client/renderer/GlStateManager", "func_179098_w", "()V");
        ClassNode result = new ClassNode(); nativeMixin.accept(new ClassRemapper(result, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return classes.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) { if (owner.equals(nativeMixin.name)) { if (name.equals("f_20986697")) return "TABS"; if (name.equals("f_02657644")) return "z"; } return name; }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                if (owner.equals(textures) && name.equals(bind)) return "bind";
                if (owner.equals("net/minecraft/client/Minecraft")) { if (name.equals(instance)) return "instance"; if (name.equals(manager)) return "textures"; }
                if (owner.equals(mappings.classFromSrg("net/minecraft/client/renderer/GlStateManager"))) return descriptor.equals("()V") ? "state" : "color";
                return name;
            }
        })); return result;
    }
    private static InsnList callback(String owner, String name) { InsnList code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(new VarInsnNode(Opcodes.FLOAD, 1)); code.add(new VarInsnNode(Opcodes.ILOAD, 2)); code.add(new VarInsnNode(Opcodes.ILOAD, 3)); code.add(new InsnNode(Opcodes.ACONST_NULL)); code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, name, "(FIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", false)); return code; }
    private static List<AbstractInsnNode> selected(ClassNode owner, MethodNode method, AnnotationNode annotation) {
        IInjectionPointContext base = HostCompatibilityPatchesTest.context(owner.name);
        ClassLoader loader = HostCreativeTabCompatibilityTest.class.getClassLoader();
        var config = Proxy.newProxyInstance(loader, new Class<?>[] {org.spongepowered.asm.mixin.extensibility.IMixinConfig.class}, (proxy, called, args) -> {
            if (called.getName().equals("hasDecoration")) return false;
            throw new AssertionError("Unexpected slice config call: " + called.getName());
        });
        var info = Proxy.newProxyInstance(loader, new Class<?>[] {org.spongepowered.asm.mixin.extensibility.IMixinInfo.class}, (proxy, called, args) -> {
            if (called.getName().equals("getConfig")) return config;
            throw new AssertionError("Unexpected slice mixin-info call: " + called.getName());
        });
        var mixin = Proxy.newProxyInstance(loader, new Class<?>[] {org.spongepowered.asm.mixin.refmap.IMixinContext.class}, (proxy, called, args) -> called.getName().equals("getMixin") ? info : called.invoke(base.getMixin(), args));
        ISliceContext context = (ISliceContext) Proxy.newProxyInstance(HostCreativeTabCompatibilityTest.class.getClassLoader(), new Class<?>[] {ISliceContext.class}, (proxy, called, args) -> {
            if (called.getName().equals("getMethod")) return method; if (called.getName().equals("getAnnotationNode")) return annotation; if (called.getName().equals("getSlice")) return null;
            if (called.getName().equals("getAnnotation") || called.getName().equals("getSelectorAnnotation")) return org.spongepowered.asm.util.Annotations.handleOf(annotation);
            if (called.getName().equals("getMixin")) return mixin;
            return called.invoke(base, args);
        });
        AnnotationNode point = at(annotation); List<AbstractInsnNode> found = new ArrayList<>();
        var data = new InjectionPointData(context, "INVOKE", List.of(), (String) value(point, "target"), "", (Integer) value(point, "ordinal"), -1, "creative-batch", 0);
        if (value(annotation, "slice") instanceof List<?> slices) {
            MethodSlice slice = MethodSlice.parse(context, (AnnotationNode) slices.get(0));
            new BeforeInvoke(data).find(method.desc, slice.getSlice(Target.of(null, owner, method)), found);
        } else new BeforeInvoke(data).find(method.desc, method.instructions, found);
        return found;
    }
    private static AdjustableAnnotationNode adjustable(AnnotationNode annotation) { AdjustableAnnotationNode result = AdjustableAnnotationNode.fromNode(annotation); if (result instanceof RemappableAnnotationNode node) node.setRemapper(value -> value.applyRefmap(UnaryOperator.identity())); return result; }
    private static AnnotationNode injector(MethodNode handler) { return handler.visibleAnnotations.stream().filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow(); }
    private static AnnotationNode at(AnnotationNode annotation) { return (AnnotationNode) ((List<?>) value(annotation, "at")).get(0); }
    private static MethodNode draw(ClassNode node) { return node.methods.stream().filter(method -> method.name.equals(render) && method.desc.equals("(FII)V")).findFirst().orElseThrow(); }
    private static List<MethodInsnNode> textureCalls(MethodNode method) { return code(method).stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(textures) && call.name.equals(bind)).map(MethodInsnNode.class::cast).toList(); }
    private static List<AbstractInsnNode> code(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static ClassNode read(Path path, String name) throws Exception { try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static ClassNode owned(Class<?> type) throws Exception { try (var input = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static ClassNode copy(ClassNode node) { ClassNode result = new ClassNode(); new ClassReader(bytes(node)).accept(result, 0); return result; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] bodyBytes(MethodNode method) { MethodNode copy = new MethodNode(method.access, method.name, method.desc, method.signature, method.exceptions.toArray(String[]::new)); method.accept(copy); copy.visibleAnnotations = null; copy.invisibleAnnotations = null; return methodBytes(copy); }
    private static byte[] annotationBytes(AnnotationNode annotation) { MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "metadata", "()V", null, null); method.visibleAnnotations = List.of(annotation); method.visitInsn(Opcodes.RETURN); return methodBytes(method); }
    private static byte[] methodBytes(MethodNode method) { ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "fixture/CreativeMethod", null, "java/lang/Object", null); method.accept(writer); writer.visitEnd(); return writer.toByteArray(); }
    private static Object value(AnnotationNode node, String key) { if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) return node.values.get(i + 1); return null; }
    private static void put(AnnotationNode node, String key, Object value) { for (int i = 0; node.values != null && i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) { node.values.set(i + 1, value); return; } if (node.values == null) node.values = new ArrayList<>(); node.values.add(key); node.values.add(value); }
}
