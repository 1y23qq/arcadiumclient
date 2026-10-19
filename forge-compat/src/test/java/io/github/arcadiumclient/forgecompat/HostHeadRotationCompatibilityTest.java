package io.github.arcadiumclient.forgecompat;

import com.google.gson.JsonParser;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.util.Locals;

import static org.junit.jupiter.api.Assertions.*;

class HostHeadRotationCompatibilityTest {
    private static final String RENDERER = "net/minecraft/client/renderer/entity/RendererLivingEntity";
    private static final String DESCRIPTOR = "(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V";
    private static Path runtime, game;
    private static ForgeMappings mappings;
    private static ClassNode original, mixin;
    private static String name, descriptor;
    private static URLClassLoader resources;

    @BeforeAll static void setup() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        game = root.resolve("build/forge-compat/runtime-qa/20261015-162217-a5436c6db1/jei/game");
        runtime = game.resolve(".fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Assumptions.assumeTrue(Files.isRegularFile(runtime) && Files.isRegularFile(tiny));
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(Files.readString(tiny)), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var input = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), input); }
        Path patched;
        try (var paths = Files.list(game.resolve(".arcadium/forge-compat/game"))) { patched = paths.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow(); }
        new ForgeJarTransformer().index(patched, mappings, true);
        original = read(runtime, mappings.classFromSrg(RENDERER));
        Path sarcio;
        try (var paths = Files.list(game.resolve("mods"))) { sarcio = paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("sarcio-")).findFirst().orElseThrow(); }
        mixin = read(sarcio, "dev/rdh/sarcio/mixin/bugfix/render/entity/LivingEntityRendererMixin");
        name = mappings.methodFromSrg(RENDERER, "func_76986_a", DESCRIPTOR); descriptor = mappings.descriptorFromSrg(DESCRIPTOR);
        resources = new URLClassLoader(new URL[]{runtime.toUri().toURL()}, HostHeadRotationCompatibilityTest.class.getClassLoader());
    }

    @AfterAll static void close() throws Exception { if (resources != null) resources.close(); }

    @Test void restoresTheActualPinnedMixinLocalsWithoutChangingSarciosCorrectFloatOrdinals() throws Exception {
        Field properties = FabricLauncherBase.class.getDeclaredField("properties"), launcher = FabricLauncherBase.class.getDeclaredField("launcher"); properties.setAccessible(true); launcher.setAccessible(true);
        Object previousProperties = properties.get(null), previousLauncher = launcher.get(null);
        try {
            properties.set(null, new HashMap<String,Object>()); FabricLauncherBase.getProperties().put("mixin.initialised", "0.8.7");
            launcher.set(null, java.lang.reflect.Proxy.newProxyInstance(FabricLauncher.class.getClassLoader(), new Class<?>[]{FabricLauncher.class}, (proxy, method, args) -> switch(method.getName()) {
                case "getEnvironmentType" -> EnvType.CLIENT; case "getTargetNamespace" -> "intermediary"; case "getTargetClassLoader" -> resources;
                case "isDevelopment" -> false; case "getClassPath" -> List.of(runtime);
                case "getResourceAsStream" -> resources.getResourceAsStream((String) args[0]);
                case "getClassByteArray" -> { try (var input = resources.getResourceAsStream(((String) args[0]).replace('.', '/') + ".class")) { yield input == null ? null : input.readAllBytes(); } }
                default -> throw new UnsupportedOperationException(method.getName());
            }));
            ClassNode node = copy(original); MethodNode render = render(node);
            var factory = ClassInfo.class.getDeclaredMethod("fromClassNode", ClassNode.class); factory.setAccessible(true); factory.invoke(null, node);
            HostClassMetadata.synchronize(node.name, bytes(node));
            AbstractInsnNode anchor = anchor(render);
            var actual = new Analyzer<>(new BasicInterpreter()).analyze(node.name, render)[render.instructions.indexOf(anchor)];
            for (int slot : new int[]{8,9,11,12,13}) assertEquals(Type.FLOAT_TYPE, actual.getLocal(slot).getType());
            var broken = Locals.getLocalsAt(node, render, anchor, 0);
            assertFalse(Arrays.stream(broken).filter(Objects::nonNull).anyMatch(local -> local.desc.equals("F")), "The raw Forge CHOP frame must reproduce the missing native float captures");
            HostHeadRotationCompatibility.normalize(node, mappings); HostClassMetadata.synchronize(node.name, bytes(node));
            var fixed = Locals.getLocalsAt(node, render, anchor, 0);
            List<Integer> floats = new ArrayList<>(); for (int index = 0; index < fixed.length; index++) if (fixed[index] != null && fixed[index].desc.equals("F")) floats.add(index);
            assertEquals(List.of(8,9,11,12,13), floats);
            assertEquals(11, floats.get(2)); assertEquals(12, floats.get(3)); assertEquals(13, floats.get(4));
            MethodNode handler = mixin.methods.stream().filter(method -> method.name.equals("sarcio$fixHeadRotation")).findFirst().orElseThrow();
            assertEquals(2, value(handler.invisibleParameterAnnotations[2].get(0), "ordinal"));
            assertEquals(3, value(handler.invisibleParameterAnnotations[3].get(0), "ordinal"));
            assertEquals(4, value(handler.visibleAnnotations.get(0), "ordinal"));
            assertEquals("f", fixed[11].name); assertEquals("f1", fixed[12].name); assertEquals("f2", fixed[13].name);
        } finally { properties.set(null, previousProperties); launcher.set(null, previousLauncher); }
    }

    @Test void preservesEveryInstructionAndForgeRenderingHookWhileReplacingOnlyEquivalentFrames() throws Exception {
        ClassNode node = copy(original); MethodNode render = render(node);
        List<AbstractInsnNode> instructions = instructions(render); List<TryCatchBlockNode> catches = List.copyOf(render.tryCatchBlocks);
        List<LocalVariableNode> locals = List.copyOf(render.localVariables); int maximumLocals = render.maxLocals, maximumStack = render.maxStack;
        int frames = (int) Arrays.stream(render.instructions.toArray()).filter(FrameNode.class::isInstance).count();
        assertTrue(Arrays.stream(render.instructions.toArray()).anyMatch(instruction -> instruction instanceof FrameNode frame && frame.type == Opcodes.F_CHOP));
        HostHeadRotationCompatibility.normalize(node, mappings);
        assertEquals(instructions, instructions(render)); assertEquals(catches, render.tryCatchBlocks); assertEquals(locals, render.localVariables);
        assertEquals(maximumLocals, render.maxLocals); assertEquals(maximumStack, render.maxStack);
        assertEquals(frames, Arrays.stream(render.instructions.toArray()).filter(instruction -> instruction instanceof FrameNode frame && frame.type == Opcodes.F_FULL).count());
        List<String> hooks = Arrays.stream(render.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
            .filter(call -> call.owner.startsWith("net/minecraftforge/")).map(call -> call.owner + "." + call.name + call.desc).toList();
        assertTrue(hooks.stream().anyMatch(call -> call.contains("RenderLivingEvent$Pre.<init>")));
        assertTrue(hooks.stream().anyMatch(call -> call.contains("RenderLivingEvent$Post.<init>")));
        assertEquals(2, hooks.stream().filter(call -> call.contains("EventBus.post")).count());
        new Analyzer<>(new BasicVerifier()).analyze(node.name, render);
        ClassWriter srg = new ClassWriter(0); node.accept(new ClassRemapper(srg, mappings.runtimeToSrg()));
        ClassWriter back = new ClassWriter(0); new ClassReader(srg.toByteArray()).accept(new ClassRemapper(back, mappings.srgToRuntime()), 0);
        ClassNode roundTrip = new ClassNode(); new ClassReader(back.toByteArray()).accept(roundTrip, 0);
        assertEquals(frames, Arrays.stream(render(roundTrip).instructions.toArray()).filter(instruction -> instruction instanceof FrameNode frame && frame.type == Opcodes.F_FULL).count());
    }

    @Test void isIdempotentAndLeavesOtherClassesAndTheErasedEntityOverloadUnchanged() {
        ClassNode node = copy(original);
        String erasedDescriptor = mappings.descriptorFromSrg("(Lnet/minecraft/entity/Entity;DDDFF)V");
        MethodNode erased = node.methods.stream().filter(method -> method.desc.equals(erasedDescriptor)).findFirst().orElseThrow();
        List<AbstractInsnNode> erasedBody = List.of(erased.instructions.toArray());
        HostHeadRotationCompatibility.normalize(node, mappings); byte[] once = bytes(node);
        HostHeadRotationCompatibility.normalize(node, mappings); assertArrayEquals(once, bytes(node));
        assertEquals(erasedBody, List.of(erased.instructions.toArray()));
        ClassNode other = copy(original); other.name = "fixture/UnrelatedRenderer"; byte[] before = bytes(other);
        HostHeadRotationCompatibility.normalize(other, mappings); assertArrayEquals(before, bytes(other));
    }

    @Test void verifiesTheNormalizedActualForgeRendererOnTheJava25VmWithoutInitializingTheGame() throws Exception {
        ClassNode node = copy(original); HostHeadRotationCompatibility.normalize(node, mappings); byte[] normalized = bytes(node);
        LinkedHashSet<Path> paths = new LinkedHashSet<>(); paths.add(runtime);
        try (var files = Files.list(game.resolve(".arcadium/forge-compat/api"))) { paths.add(files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow()); }
        Path metadata = Path.of("../build/forge-compat/fml-runtime-review/trusted-classpath.json");
        Assumptions.assumeTrue(Files.isRegularFile(metadata));
        for (var item : new JsonParser().parse(Files.readString(metadata)).getAsJsonArray()) {
            Path path = Path.of(item.getAsString()); if (!Files.isRegularFile(path)) continue;
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.getEntry("ave.class") == null && zip.getEntry("net/minecraft/client/main/Main.class") == null && zip.getEntry("fabric.mod.json") == null && zip.getEntry("mcmod.info") == null) paths.add(path);
            }
        }
        URL[] urls = paths.stream().map(path -> { try { return path.toUri().toURL(); } catch (Exception failure) { throw new IllegalArgumentException(failure); } }).toArray(URL[]::new);
        try (URLClassLoader verifier = new URLClassLoader(urls, getClass().getClassLoader()) {
            @Override protected Class<?> findClass(String className) throws ClassNotFoundException {
                if (className.equals(node.name.replace('/', '.'))) return defineClass(className, normalized, 0, normalized.length);
                return super.findClass(className);
            }
        }) {
            Class<?> verified = Class.forName(node.name.replace('/', '.'), false, verifier);
            Class<?> entity = Class.forName(mappings.classFromSrg("net/minecraft/entity/EntityLivingBase").replace('/', '.'), false, verifier);
            assertEquals(void.class, verified.getDeclaredMethod(name, entity, double.class, double.class, double.class, float.class, float.class).getReturnType());
            assertTrue(verified.getDeclaredMethods().length > 10);
        }
    }

    public static class OwnEntity { public OwnEntity mount; public boolean riding; public boolean isRiding() { return riding; } }
    public static class OwnLiving extends OwnEntity { }

    @Test void preservesTheActualNativeHeadRotationHandlerOnTheJvmForLivingAndOtherMounts() throws Exception {
        String living = mappings.classFromSrg("net/minecraft/entity/EntityLivingBase"), entity = mappings.classFromSrg("net/minecraft/entity/Entity");
        String riding = mappings.methodFromSrg("net/minecraft/entity/Entity", "func_70115_ae", "()Z");
        String mount = mappings.fieldFromSrg("net/minecraft/entity/Entity", "field_70154_o", "Lnet/minecraft/entity/Entity;");
        Remapper remapper = new Remapper() {
            @Override public String map(String internal) { if (internal.equals(living)) return Type.getInternalName(OwnLiving.class); if (internal.equals(entity)) return Type.getInternalName(OwnEntity.class); return internal; }
            @Override public String mapMethodName(String owner, String method, String desc) { return (owner.equals(living) || owner.equals(entity)) && method.equals(riding) ? "isRiding" : method; }
            @Override public String mapFieldName(String owner, String field, String desc) { return (owner.equals(living) || owner.equals(entity)) && field.equals(mount) ? "mount" : field; }
        };
        ClassNode fixture = new ClassNode(); fixture.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/NativeHeadRotation", null, "java/lang/Object", null);
        MethodNode handler = mixin.methods.stream().filter(method -> method.name.equals("sarcio$fixHeadRotation")).findFirst().orElseThrow();
        MethodNode copy = new MethodNode(handler.access, handler.name, remapper.mapMethodDesc(handler.desc), null, null);
        handler.accept(new org.objectweb.asm.commons.MethodRemapper(copy, remapper)); fixture.methods.add(copy);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); fixture.methods.add(constructor);
        class Loader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass("fixture.NativeHeadRotation", bytes, 0, bytes.length); } }
        Class<?> type = new Loader().define(bytes(fixture)); Object target = type.getConstructor().newInstance();
        var apply = type.getDeclaredMethod(handler.name, float.class, OwnLiving.class, float.class, float.class); apply.setAccessible(true);
        for (boolean ridingState : List.of(false,true)) for (OwnEntity vehicle : new OwnEntity[]{null,new OwnEntity(),new OwnLiving()}) {
            OwnLiving value = new OwnLiving(); value.riding = ridingState; value.mount = vehicle;
            for (float[] values : new float[][]{{17,13,84},{-0.0F,0.0F,-0.0F},{90,Float.NaN,5},{Float.NaN,12,33},{5,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}}) {
                float expected = ridingState && vehicle instanceof OwnLiving ? values[2] - values[1] : values[0];
                float actual = (Float) apply.invoke(target, values[0], value, values[1], values[2]);
                assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual));
            }
        }
    }

    private static AbstractInsnNode anchor(MethodNode method) { String pitch = mappings.fieldFromSrg("net/minecraft/entity/Entity", "field_70127_C", "F"); return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD && field.name.equals(pitch)).findFirst().orElseThrow(); }
    private static MethodNode render(ClassNode node) { return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> instructions(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> !(instruction instanceof FrameNode)).toList(); }
    private static ClassNode read(Path jar, String name) throws Exception { ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static ClassNode copy(ClassNode node) { ClassNode result = new ClassNode(); node.accept(result); return result; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static Object value(AnnotationNode annotation, String key) { for (int index=0;annotation.values!=null&&index<annotation.values.size();index+=2) if (annotation.values.get(index).equals(key)) return annotation.values.get(index+1); return null; }
}
