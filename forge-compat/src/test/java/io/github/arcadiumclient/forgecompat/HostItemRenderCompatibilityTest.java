package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import static org.junit.jupiter.api.Assertions.*;

class HostItemRenderCompatibilityTest {
    private static final String ITEM = "net/minecraft/client/renderer/entity/RenderItem";
    private static final String WORLD_BUFFER = "net/minecraft/client/renderer/WorldRenderer";
    private static final String BAKED_QUAD = "net/minecraft/client/renderer/block/model/BakedQuad";
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";
    private static final String BAKED_MODEL = "net/minecraft/client/resources/model/IBakedModel";
    private static final String TRANSFORMS = "net/minecraft/client/renderer/block/model/ItemCameraTransforms";
    private static final String TRANSFORM_TYPE = TRANSFORMS + "$TransformType";
    private static final String HOOKS = "net/minecraftforge/client/ForgeHooksClient";
    private static final String LIGHT = "net/minecraftforge/client/model/pipeline/LightUtil";
    private static final String PERSPECTIVE = "net/minecraftforge/client/model/IPerspectiveAwareModel";
    private static final String OVERFLOW = "org/polyfrost/overflowanimations/mixins/legacy/firstperson/MixinItemRenderer_FirstPersonTransform";
    private static final String OVERFLOW_API = "org/polyfrost/overflowanimations/legacy/firstperson/LegacyFirstPerson";
    private static final String CERA = "dev/rdh/cera/mixin/ItemRendererMixin";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String OWNER = Type.getInternalName(HostItemRenderCompatibilityTest.class) + "$GeneratedRenderer";
    private static final String BUFFER = Type.getInternalName(Buffer.class), QUAD = Type.getInternalName(Quad.class);
    private static final String STACK = Type.getInternalName(Stack.class), MODEL = Type.getInternalName(Model.class);
    private static final String CAMERA = Type.getInternalName(Camera.class), TRANSFORM = Type.getInternalName(Transform.class);

    @Test void restoresActualRequiredTargetsAndPreservesAllOtherMethodsAndNativeHandlers() throws Exception {
        ForgeMappings mappings = actualMappings();
        ClassNode node = read(actualGame(), mappings.classFromSrg(ITEM));
        ClassNode cera = read(nativeJar("cera-1.0.1.jar"), CERA);
        ClassNode overflow = read(nativeJar("overflowanimations-5.0.6+1.8.9-fabric.jar"), OVERFLOW);
        MethodNode emissive = method(cera, "cera$emissiveQuad"), animation = method(overflow, "overflowanimations$legacyFirstPersonTransform");
        byte[] originalCera = methodBytes(cera.name, emissive), originalOverflow = methodBytes(overflow.name, animation);
        MethodNode list = method(node, runtimeMethod(mappings, "func_175032_a", "(L" + WORLD_BUFFER + ";Ljava/util/List;IL" + ITEM_STACK + ";)V"));
        MethodNode render = method(node, runtimeMethod(mappings, "func_175040_a", "(L" + ITEM_STACK + ";L" + BAKED_MODEL + ";L" + TRANSFORM_TYPE + ";)V"));
        MethodNode quad = method(node, runtimeMethod(mappings, "func_175033_a", "(L" + WORLD_BUFFER + ";L" + BAKED_QUAD + ";I)V"));
        String quadTarget = target(emissive), transformTarget = target(animation);
        assertEquals(0, matches(node, list, quadTarget));
        assertEquals(0, matches(node, render, transformTarget));
        assertEquals(4, render.maxLocals);
        Map<String, byte[]> untouched = new LinkedHashMap<>();
        for (MethodNode method : node.methods) if (method != list && method != render && method != quad) untouched.put(method.name + method.desc, methodBytes(node.name, method));
        HostItemRenderCompatibility.normalize(node, mappings);
        list = method(node, list.name); render = method(node, render.name); quad = method(node, quad.name);
        assertEquals(1, matches(node, list, quadTarget));
        assertEquals(1, matches(node, render, transformTarget));
        assertEquals(1, calls(quad, LIGHT, "renderQuadColor"));
        assertEquals(0, calls(list, LIGHT, "renderQuadColor"));
        assertEquals(1, calls(render, HOOKS, "handleCameraTransforms"));
        assertEquals(4, render.maxLocals);
        assertEquals(2, Arrays.stream(render.instructions.toArray()).filter(instruction -> instruction instanceof FrameNode frame
            && frame.local != null && frame.local.size() == 4).count());
        for (MethodNode method : node.methods) if (untouched.containsKey(method.name + method.desc)) assertArrayEquals(untouched.get(method.name + method.desc), methodBytes(node.name, method));
        assertArrayEquals(originalCera, methodBytes(cera.name, emissive));
        assertArrayEquals(originalOverflow, methodBytes(overflow.name, animation));
        assertEquals(List.of("m_73924644(Lnet/minecraft/unmapped/C_88708284;Lnet/minecraft/unmapped/C_41102159;Lnet/minecraft/unmapped/C_83604618$C_68196853;)V"), value(injection(animation), "method"));
        assertEquals(2, localParameters(animation));
        for (MethodNode method : node.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        byte[] normalized = bytes(node, ClassWriter.COMPUTE_MAXS);
        ClassNode decoded = new ClassNode(); new ClassReader(normalized).accept(decoded, 0);
        HostItemRenderCompatibility.normalize(decoded, mappings);
        assertArrayEquals(normalized, bytes(decoded, ClassWriter.COMPUTE_MAXS));
    }

    @Test void ordinaryBranchMatchesTheActualForgeFallbackAndKeepsItsReplacementBranch() throws Exception {
        ForgeMappings mappings = actualMappings();
        Path universal = root().resolve("build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        Assumptions.assumeTrue(Files.isRegularFile(universal));
        ClassNode original = read(universal, HOOKS), hooks = new ClassNode();
        original.accept(new ClassRemapper(hooks, mappings.officialToRuntime()));
        List<String> source = instructions(method(hooks, "handleCameraTransforms"));
        assertTrue(source.stream().anyMatch(instruction -> instruction.contains(PERSPECTIVE + ".handlePerspective")));
        assertTrue(source.stream().anyMatch(instruction -> instruction.contains(HOOKS + ".multiplyCurrentGlMatrix")));
        assertTrue(source.stream().anyMatch(instruction -> instruction.contains("Pair.getLeft")));
        ClassNode node = read(actualGame(), mappings.classFromSrg(ITEM));
        HostItemRenderCompatibility.normalize(node, mappings);
        MethodNode render = method(node, runtimeMethod(mappings, "func_175040_a", "(L" + ITEM_STACK + ";L" + BAKED_MODEL + ";L" + TRANSFORM_TYPE + ";)V"));
        List<String> normalized = instructions(render);
        int getter = -1;
        for (int i = 0; i < normalized.size(); i++) if (normalized.get(i).contains(mappings.classFromSrg(BAKED_MODEL) + "." + mappings.methodFromSrg(BAKED_MODEL, "func_177552_f", "()L" + TRANSFORMS + ";"))) getter = i;
        assertTrue(getter > 0);
        List<String> fallback = source.subList(source.size() - 6, source.size() - 1);
        List<String> restored = new ArrayList<>(normalized.subList(getter - 1, getter + 4));
        restored.replaceAll(instruction -> instruction.equals("25 2") ? "25 0" : instruction.equals("25 3") ? "25 1" : instruction);
        assertEquals(fallback, restored);
        assertEquals(source.get(source.size() - 1), "176");
    }

    @Test void jvmPreservesForgeQuadEmissionAndCeraRespriteAndSkipDecisions() throws Exception {
        ClassNode node = fixture();
        HostItemRenderCompatibility.normalize(node, fixtureMappings());
        MethodNode list = method(node, "renderQuads");
        MethodInsnNode call = Arrays.stream(list.instructions.toArray()).filter(i -> i instanceof MethodInsnNode m && m.owner.equals(OWNER) && m.name.equals("quad")).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        list.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(CeraOperation.class), "emit", "(Ljava/lang/Object;L" + BUFFER + ";L" + QUAD + ";I)V", false));
        Object renderer = define(node).getConstructor().newInstance();
        Method draw = renderer.getClass().getMethod("renderQuads", Buffer.class, List.class, int.class, Stack.class);
        Quad original = new Quad("ordinary"), emissive = new Quad("emissive");
        Buffer buffer = new Buffer();
        draw.invoke(renderer, buffer, List.of(original), 0x12345678, new Stack(false));
        assertEquals(List.of("forge:ordinary:305419896"), buffer.events);
        assertEquals(1, buffer.forgeCalls);
        buffer.emissivePass = true; buffer.replacement = emissive;
        draw.invoke(renderer, buffer, List.of(original), -1, new Stack(false));
        assertEquals("forge:emissive:-1", buffer.events.get(1));
        assertEquals(2, buffer.forgeCalls);
        buffer.replacement = null;
        draw.invoke(renderer, buffer, List.of(original), 7, new Stack(false));
        assertEquals(2, buffer.forgeCalls);
        assertEquals(0, buffer.vanillaCalls);
    }

    @Test void jvmRunsActualNativeOverflowGateWithoutDoubleTransformingOrdinaryItems() throws Exception {
        ClassNode node = fixture(); HostItemRenderCompatibility.normalize(node, fixtureMappings());
        wrapWithActualOverflowHandler(node);
        Class<?> renderer = define(node);
        for (Transform transform : Transform.values()) for (boolean enabled : List.of(false, true)) {
            Hooks.reset(); Object instance = renderer.getConstructor().newInstance();
            StandardModel model = new StandardModel(); Stack stack = new Stack(enabled);
            renderer.getMethod("renderModel", Stack.class, Model.class, Transform.class).invoke(instance, stack, model, transform);
            boolean handled = enabled && transform == Transform.FIRST_PERSON;
            assertEquals(handled ? 1 : 0, stack.legacyTransforms);
            assertEquals(transform == Transform.FIRST_PERSON ? 1 : 0, stack.featureCalls);
            assertEquals(handled ? 0 : 1, model.camera.transforms);
            assertEquals(0, Hooks.perspectiveCalls);
            assertEquals(0, Hooks.hookCalls);
            assertSame(model, renderer.getField("drawn").get(instance));
        }
    }

    @Test void jvmKeepsCustomPerspectiveMatrixAndReturnedModelExactlyOnce() throws Exception {
        ClassNode node = fixture(); HostItemRenderCompatibility.normalize(node, fixtureMappings());
        wrapWithActualOverflowHandler(node);
        Class<?> renderer = define(node);
        for (boolean enabled : List.of(false, true)) {
            Hooks.reset(); Object instance = renderer.getConstructor().newInstance();
            StandardModel replacement = new StandardModel(); CustomModel model = new CustomModel(replacement); Stack stack = new Stack(enabled);
            renderer.getMethod("renderModel", Stack.class, Model.class, Transform.class).invoke(instance, stack, model, Transform.FIRST_PERSON);
            assertEquals(1, Hooks.hookCalls);
            assertEquals(1, Hooks.perspectiveCalls);
            assertEquals(1, Hooks.matrixApplications);
            assertEquals(0, model.camera.transforms);
            assertEquals(0, replacement.camera.transforms);
            assertEquals(0, stack.featureCalls);
            assertEquals(0, stack.legacyTransforms);
            assertSame(replacement, renderer.getField("drawn").get(instance));
        }
    }

    @Test void unknownForgeShapesAreRejectedAtomicallyAndUnrelatedClassesStayUntouched() throws Exception {
        for (boolean operands : List.of(false, true)) {
            ClassNode node = fixture();
            if (operands) {
                MethodNode list = method(node, "renderQuads");
                VarInsnNode color = Arrays.stream(list.instructions.toArray()).filter(i -> i instanceof VarInsnNode v && v.getOpcode() == Opcodes.ILOAD).map(VarInsnNode.class::cast).findFirst().orElseThrow();
                list.instructions.insertBefore(color, new org.objectweb.asm.tree.InsnNode(Opcodes.NOP));
            } else method(node, "renderModel").maxLocals = 5;
            byte[] before = bytes(node, 0);
            assertThrows(IllegalStateException.class, () -> HostItemRenderCompatibility.normalize(node, fixtureMappings()));
            assertArrayEquals(before, bytes(node, 0));
        }
        ClassNode node = fixture(); node.name = "fixture/UnrelatedRenderer";
        byte[] before = bytes(node, 0);
        HostItemRenderCompatibility.normalize(node, fixtureMappings());
        assertArrayEquals(before, bytes(node, 0));
    }

    public static final class Buffer {
        public final List<String> events = new ArrayList<>();
        public int forgeCalls, vanillaCalls;
        public boolean emissivePass;
        public Quad replacement;
    }
    public record Quad(String sprite) { }
    public static final class Stack {
        public final boolean enabled;
        public int featureCalls, legacyTransforms;
        public Stack(boolean enabled) { this.enabled = enabled; }
    }
    public enum Transform { FIRST_PERSON, GUI }
    public static class Camera {
        public int transforms;
        public void apply(Transform type) { transforms++; }
    }
    public interface Model { Camera camera(); }
    public interface Perspective extends Model { Model perspective(Transform type); }
    public static class StandardModel implements Model {
        public final Camera camera = new Camera();
        @Override public Camera camera() { return camera; }
    }
    public static final class CustomModel extends StandardModel implements Perspective {
        private final Model replacement;
        public CustomModel(Model replacement) { this.replacement = replacement; }
        @Override public Model perspective(Transform type) { Hooks.perspectiveCalls++; return replacement; }
    }
    public static final class Hooks {
        public static int hookCalls, perspectiveCalls, matrixApplications;
        public static void setup() { }
        public static void reset() { hookCalls = perspectiveCalls = matrixApplications = 0; }
        public static Model handleCameraTransforms(Model model, Transform type) {
            hookCalls++;
            if (model instanceof Perspective perspective) { Model result = perspective.perspective(type); matrixApplications++; return result; }
            model.camera().apply(type); return model;
        }
    }
    public static final class Light {
        public static void renderQuadColor(Buffer buffer, Quad quad, int color) { buffer.forgeCalls++; buffer.events.add("forge:" + quad.sprite() + ":" + color); }
        public static void vanilla(Buffer buffer, Quad quad, int color) { buffer.vanillaCalls++; }
    }
    public static final class CeraOperation {
        public static void emit(Object renderer, Buffer buffer, Quad quad, int color) throws Exception {
            if (buffer.emissivePass) { if (buffer.replacement == null) return; quad = buffer.replacement; }
            Method original = renderer.getClass().getDeclaredMethod("quad", Buffer.class, Quad.class, int.class);
            original.setAccessible(true); original.invoke(renderer, buffer, quad, color);
        }
    }
    public interface Operation { Object call(Object... arguments); }
    public static final class CameraOperation implements Operation {
        public CameraOperation() { }
        @Override public Object call(Object... arguments) { ((Camera) arguments[0]).apply((Transform) arguments[1]); return null; }
    }
    public static final class LegacyFeature {
        public static boolean apply(Stack stack, Model model) { stack.featureCalls++; if (stack.enabled) stack.legacyTransforms++; return stack.enabled; }
    }

    private static void wrapWithActualOverflowHandler(ClassNode node) throws Exception {
        ForgeMappings mappings = actualMappings();
        ClassNode mixin = read(nativeJar("overflowanimations-5.0.6+1.8.9-fabric.jar"), OVERFLOW), translated = new ClassNode();
        Map<String, String> names = Map.of(OVERFLOW, OWNER, mappings.classFromSrg(TRANSFORMS), CAMERA, mappings.classFromSrg(TRANSFORM_TYPE), TRANSFORM,
            mappings.classFromSrg(ITEM_STACK), STACK, mappings.classFromSrg(BAKED_MODEL), MODEL, OVERFLOW_API, Type.getInternalName(LegacyFeature.class), OPERATION, Type.getInternalName(Operation.class));
        mixin.accept(new ClassRemapper(translated, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) { return owner.equals(mappings.classFromSrg(TRANSFORM_TYPE)) ? "FIRST_PERSON" : name; }
            @Override public String mapMethodName(String owner, String name, String descriptor) { return owner.equals(OVERFLOW_API) ? "apply" : name; }
        }));
        MethodNode handler = method(translated, "overflowanimations$legacyFirstPersonTransform");
        handler.signature = null; handler.visibleAnnotations = null; handler.invisibleAnnotations = null; handler.visibleParameterAnnotations = null; handler.invisibleParameterAnnotations = null;
        node.methods.add(handler);
        MethodNode render = method(node, "renderModel");
        MethodInsnNode call = Arrays.stream(render.instructions.toArray()).filter(i -> i instanceof MethodInsnNode m && m.owner.equals(CAMERA)).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        AbstractInsnNode receiver = call.getPrevious().getPrevious().getPrevious();
        assertTrue(receiver instanceof VarInsnNode variable && variable.var == 2);
        render.instructions.insertBefore(receiver, new VarInsnNode(Opcodes.ALOAD, 0));
        InsnList arguments = new InsnList();
        String operation = Type.getInternalName(CameraOperation.class);
        arguments.add(new TypeInsnNode(Opcodes.NEW, operation)); arguments.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP));
        arguments.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, operation, "<init>", "()V", false));
        arguments.add(new VarInsnNode(Opcodes.ALOAD, 1)); arguments.add(new VarInsnNode(Opcodes.ALOAD, 2));
        render.instructions.insertBefore(call, arguments);
        render.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESPECIAL, OWNER, handler.name, handler.desc, false));
    }

    private static ClassNode fixture() {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "drawn", "L" + MODEL + ";", null, null));
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitVarInsn(Opcodes.ALOAD, 0); init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); init.visitInsn(Opcodes.RETURN); init.maxStack = 1; init.maxLocals = 1; node.methods.add(init);
        MethodNode quad = new MethodNode(Opcodes.ACC_PRIVATE, "quad", "(L" + BUFFER + ";L" + QUAD + ";I)V", null, null);
        quad.visitVarInsn(Opcodes.ALOAD, 1); quad.visitVarInsn(Opcodes.ALOAD, 2); quad.visitVarInsn(Opcodes.ILOAD, 3);
        quad.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Light.class), "vanilla", quad.desc, false); quad.visitInsn(Opcodes.RETURN); quad.maxStack = 3; quad.maxLocals = 4; node.methods.add(quad);
        MethodNode list = new MethodNode(Opcodes.ACC_PUBLIC, "renderQuads", "(L" + BUFFER + ";Ljava/util/List;IL" + STACK + ";)V", null, null);
        list.visitVarInsn(Opcodes.ALOAD, 2); list.visitInsn(Opcodes.ICONST_0); list.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        list.visitTypeInsn(Opcodes.CHECKCAST, QUAD); list.visitVarInsn(Opcodes.ASTORE, 5);
        list.visitVarInsn(Opcodes.ALOAD, 1); list.visitVarInsn(Opcodes.ALOAD, 5); list.visitVarInsn(Opcodes.ILOAD, 3);
        list.visitMethodInsn(Opcodes.INVOKESTATIC, LIGHT, "renderQuadColor", quad.desc, false); list.visitInsn(Opcodes.RETURN); list.maxStack = 3; list.maxLocals = 6; node.methods.add(list);
        MethodNode render = new MethodNode(Opcodes.ACC_PUBLIC, "renderModel", "(L" + STACK + ";L" + MODEL + ";L" + TRANSFORM + ";)V", null, null);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Hooks.class), "setup", "()V", false);
        render.visitVarInsn(Opcodes.ALOAD, 2); render.visitVarInsn(Opcodes.ALOAD, 3);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "handleCameraTransforms", "(L" + MODEL + ";L" + TRANSFORM + ";)L" + MODEL + ";", false);
        render.visitVarInsn(Opcodes.ASTORE, 2); render.visitVarInsn(Opcodes.ALOAD, 0); render.visitVarInsn(Opcodes.ALOAD, 2);
        render.visitFieldInsn(Opcodes.PUTFIELD, OWNER, "drawn", "L" + MODEL + ";"); render.visitInsn(Opcodes.RETURN); render.maxStack = 2; render.maxLocals = 4; node.methods.add(render);
        return node;
    }

    private static ForgeMappings fixtureMappings() throws Exception {
        Map<String, String> types = Map.of(ITEM, OWNER, WORLD_BUFFER, BUFFER, BAKED_QUAD, QUAD, ITEM_STACK, STACK, BAKED_MODEL, MODEL, TRANSFORMS, CAMERA, TRANSFORM_TYPE, TRANSFORM);
        StringBuilder srg = new StringBuilder(); types.forEach((legacy, runtime) -> srg.append("CL: ").append(runtime).append(' ').append(legacy).append('\n'));
        addMapping(srg, types, ITEM, "quad", "func_175033_a", "(L" + WORLD_BUFFER + ";L" + BAKED_QUAD + ";I)V");
        addMapping(srg, types, ITEM, "renderQuads", "func_175032_a", "(L" + WORLD_BUFFER + ";Ljava/util/List;IL" + ITEM_STACK + ";)V");
        addMapping(srg, types, ITEM, "renderModel", "func_175040_a", "(L" + ITEM_STACK + ";L" + BAKED_MODEL + ";L" + TRANSFORM_TYPE + ";)V");
        addMapping(srg, types, BAKED_MODEL, "camera", "func_177552_f", "()L" + TRANSFORMS + ";");
        addMapping(srg, types, TRANSFORMS, "apply", "func_181689_a", "(L" + TRANSFORM_TYPE + ";)V");
        return new ForgeMappings(new MappingResolver() {
            @Override public List<String> getNamespaces() { return List.of("official", "intermediary"); }
            @Override public String getCurrentRuntimeNamespace() { return "intermediary"; }
            @Override public String mapClassName(String namespace, String name) { return name; }
            @Override public String unmapClassName(String namespace, String name) { return name; }
            @Override public String mapMethodName(String namespace, String owner, String name, String descriptor) { return name; }
            @Override public String mapFieldName(String namespace, String owner, String name, String descriptor) { return name; }
        }, new StringReader(srg.toString()));
    }
    private static void addMapping(StringBuilder srg, Map<String, String> types, String owner, String source, String name, String descriptor) {
        String mapped = new Remapper(Opcodes.ASM9) { @Override public String map(String type) { return types.getOrDefault(type, type); } }.mapMethodDesc(descriptor);
        srg.append("MD: ").append(types.get(owner)).append('/').append(source).append(' ').append(mapped).append(' ').append(owner).append('/').append(name).append(' ').append(descriptor).append('\n');
    }
    private static Class<?> define(ClassNode node) {
        ClassNode linked = new ClassNode(); Map<String, String> external = Map.of(HOOKS, Type.getInternalName(Hooks.class), LIGHT, Type.getInternalName(Light.class), PERSPECTIVE, Type.getInternalName(Perspective.class));
        node.accept(new ClassRemapper(linked, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return external.getOrDefault(name, name); } }));
        byte[] bytes = bytes(linked, ClassWriter.COMPUTE_MAXS);
        class FixtureLoader extends ClassLoader {
            FixtureLoader() { super(HostItemRenderCompatibilityTest.class.getClassLoader()); }
            Class<?> define() { return defineClass(OWNER.replace('/', '.'), bytes, 0, bytes.length); }
        }
        return new FixtureLoader().define();
    }
    private static Path root() { return Path.of("..").toAbsolutePath().normalize(); }
    private static Path actualGame() { Path jar = root().resolve("build/forge-compat/runtime-qa/20261015-142622-46d4047a83/jei/game/.fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar"); Assumptions.assumeTrue(Files.isRegularFile(jar)); return jar; }
    private static Path nativeJar(String filename) { Path jar = ForgeTestFixtures.nativeDirectory().resolve(filename); Assumptions.assumeTrue(Files.isRegularFile(jar)); return jar; }
    private static ForgeMappings actualMappings() throws Exception {
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Path srg = root().resolve("build/forge-compat/dependencies/forge-1.8.9.srg"); Assumptions.assumeTrue(Files.isRegularFile(tiny) && Files.isRegularFile(srg));
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(Files.newBufferedReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); MappingResolver resolver = (MappingResolver) constructor.newInstance(tree, "intermediary");
        try (var reader = Files.newBufferedReader(srg)) { return new ForgeMappings(resolver, reader); }
    }
    private static ClassNode read(Path jar, String name) throws Exception { try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static String runtimeMethod(ForgeMappings mappings, String name, String descriptor) { return mappings.methodFromSrg(ITEM, name, descriptor); }
    private static AnnotationNode injection(MethodNode method) { return method.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/WrapOperation;")).findFirst().orElseThrow(); }
    private static String target(MethodNode handler) { return (String) value((AnnotationNode) ((List<?>) value(injection(handler), "at")).get(0), "target"); }
    private static Object value(AnnotationNode annotation, String key) { for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1); return null; }
    private static long calls(MethodNode method, String owner, String name) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)).count(); }
    private static int localParameters(MethodNode method) { int count = 0; for (List<AnnotationNode>[] annotations : Arrays.asList(method.visibleParameterAnnotations, method.invisibleParameterAnnotations)) if (annotations != null) for (var parameter : annotations) if (parameter != null) count += (int) parameter.stream().filter(annotation -> annotation.desc.endsWith("/Local;")).count(); return count; }
    private static int matches(ClassNode node, MethodNode method, String target) { BeforeInvoke point = new BeforeInvoke(new InjectionPointData(HostCompatibilityPatchesTest.context(node.name), "INVOKE", List.of(), target, "", -1, -1, "item-render-compat", 0)); List<AbstractInsnNode> matches = new ArrayList<>(); point.find(method.desc, method.instructions, matches); return matches.size(); }
    private static List<String> instructions(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).map(instruction -> instruction.getOpcode() + (instruction instanceof MethodInsnNode call ? " " + call.owner + "." + call.name + call.desc : instruction instanceof VarInsnNode variable ? " " + variable.var : "")).toList(); }
    private static byte[] bytes(ClassNode node, int flags) { ClassWriter writer = new ClassWriter(flags); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node, 0); }
}
