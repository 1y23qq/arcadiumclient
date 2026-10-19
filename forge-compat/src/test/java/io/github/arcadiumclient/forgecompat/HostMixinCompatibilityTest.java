package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.adjuster.tools.type.RemappableAnnotationNode;
import com.bawnorton.mixinsquared.reflection.StateExtension;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class HostMixinCompatibilityTest {
    private static Path game;
    private static ForgeMappings mappings;
    private static HostMixinCompatibility adjuster;
    private static ClassNode fontMixin, lightmapMixin, fontTarget;

    @BeforeAll static void setup() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        game = root.resolve("build/forge-compat/runtime-qa/20261015-141452-d8ba1222de/jei/game");
        Path runtime = game.resolve(".fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Assumptions.assumeTrue(Files.isRegularFile(runtime) && Files.isRegularFile(tiny));
        try (var reader = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader);
        }
        Path patched;
        try (var files = Files.list(game.resolve(".arcadium/forge-compat/game"))) { patched = files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow(); }
        new ForgeJarTransformer().index(patched, mappings, true);
        fontMixin = read(module("argentum-"), "dev/rdh/argentum/mixin/features/font/TextRendererMixin");
        lightmapMixin = read(module("sarcio-"), "dev/rdh/sarcio/mixin/bugfix/render/GameRendererMixin");
        fontTarget = read(runtime, mappings.classFromSrg("net/minecraft/client/gui/FontRenderer"));
        adjuster = new HostMixinCompatibility(mappings);
    }

    @Test void retargetsAllSixActualFontHandlersWithoutChangingTheirBodiesOrInjectionRequirements() {
        int changed = 0;
        for (MethodNode source : fontMixin.methods) {
            if (!Set.of("argentum$captureColor", "argentum$decorationBuffer", "argentum$beginDecorations", "argentum$colorDecoration", "argentum$deferDecorations", "argentum$deferTextureState").contains(source.name)) continue;
            MethodNode handler = copy(source);
            AnnotationNode original = injector(handler);
            var before = spec(original);
            List<AbstractInsnNode> body = List.of(handler.instructions.toArray());
            AdjustableAnnotationNode annotation = adjustable(original);
            assertSame(annotation, adjuster.adjust(List.of(fontTarget.name), fontMixin.name.replace('/', '.'), handler, annotation));
            assertEquals(List.of(source.name.equals("argentum$captureColor") ? "setColor(FFFF)V" : "doDraw(F)V"), value(annotation, "method"));
            var after = spec(annotation); before.remove("method"); after.remove("method"); assertEquals(before, after);
            assertEquals(body, List.of(handler.instructions.toArray()));
            changed++;
        }
        assertEquals(6, changed);
    }

    @Test void findsEveryActualRelocatedFontCallAndPreservesForgeVirtualMethods() {
        Map<String,Integer> expected = Map.of("argentum$captureColor", 1, "argentum$decorationBuffer", 2,
            "argentum$beginDecorations", 2, "argentum$colorDecoration", 8, "argentum$deferDecorations", 2, "argentum$deferTextureState", 4);
        for (MethodNode source : fontMixin.methods) {
            if (!expected.containsKey(source.name)) continue;
            MethodNode handler = copy(source); AdjustableAnnotationNode annotation = adjustable(injector(handler));
            adjuster.adjust(List.of(fontTarget.name), fontMixin.name.replace('/', '.'), handler, annotation);
            String selected = ((List<?>) value(annotation, "method")).get(0).toString();
            MethodNode target = fontTarget.methods.stream().filter(method -> (method.name + method.desc).equals(selected)).findFirst().orElseThrow();
            assertEquals(0, target.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL));
            int count = 0;
            for (Object entry : (List<?>) value(annotation, "at")) {
                var at = (AnnotationNode) entry;
                var point = new BeforeInvoke(new InjectionPointData(HostCompatibilityPatchesTest.context(fontTarget.name), "INVOKE", List.of(),
                    (String) value(at, "target"), "", -1, -1, "font-compat", 0));
                List<AbstractInsnNode> matches = new ArrayList<>(); point.find(target.desc, target.instructions, matches); count += matches.size();
            }
            assertEquals(expected.get(source.name), count, source.name);
            assertTrue(count >= ((Number) Optional.ofNullable(value(annotation, "require")).orElse(1)).intValue());
        }
    }

    @Test void correctsOnlyTheActualThreeLightmapFloatOrdinals() {
        int changed = 0;
        for (MethodNode source : lightmapMixin.methods) {
            if (!source.name.matches("sarcio\\$clamp(Red|Green|Blue)BeforeNightVision")) continue;
            MethodNode handler = copy(source); AnnotationNode original = injector(handler); var before = spec(original);
            AdjustableAnnotationNode annotation = adjustable(original);
            int oldOrdinal = ((Number) value(annotation, "ordinal")).intValue();
            adjuster.adjust(List.of(mappings.classFromSrg("net/minecraft/client/renderer/EntityRenderer")), lightmapMixin.name.replace('/', '.'), handler, annotation);
            assertEquals(oldOrdinal - 2, value(annotation, "ordinal"));
            var after = spec(annotation); before.remove("ordinal"); after.remove("ordinal"); assertEquals(before, after); changed++;
        }
        assertEquals(3, changed);
    }

    @Test void isIdempotentAndLeavesOtherMixinsHandlersAndTargetsUnchanged() {
        MethodNode handler = copy(fontMixin.methods.stream().filter(method -> method.name.equals("argentum$captureColor")).findFirst().orElseThrow());
        AdjustableAnnotationNode annotation = adjustable(injector(handler)); var original = spec(annotation);
        adjuster.adjust(List.of("fixture.Other"), fontMixin.name.replace('/', '.'), handler, annotation); assertEquals(original, spec(annotation));
        adjuster.adjust(List.of(fontTarget.name), "fixture.OtherMixin", handler, annotation); assertEquals(original, spec(annotation));
        adjuster.adjust(List.of(fontTarget.name), fontMixin.name.replace('/', '.'), handler, annotation); var once = spec(annotation);
        adjuster.adjust(List.of(fontTarget.name), fontMixin.name.replace('/', '.'), handler, annotation); assertEquals(once, spec(annotation));
        handler.name = "fixture$unrelated";
        adjuster.adjust(List.of(fontTarget.name), fontMixin.name.replace('/', '.'), handler, annotation); assertEquals(once, spec(annotation));
    }

    @Test void rejectsUnexpectedNativeMetadataInsteadOfSuppressingTheInjection() {
        MethodNode handler = copy(fontMixin.methods.stream().filter(method -> method.name.equals("argentum$captureColor")).findFirst().orElseThrow());
        AdjustableAnnotationNode annotation = adjustable(injector(handler));
        annotation.set("method", new ArrayList<>(List.of("unexpectedMethod()V")));
        var failure = assertThrows(IllegalStateException.class, () -> adjuster.adjust(List.of(fontTarget.name), fontMixin.name.replace('/', '.'), handler, annotation));
        assertTrue(failure.getMessage().contains("method selectors changed"));
        assertNotNull(annotation); assertEquals(List.of("unexpectedMethod()V"), value(annotation, "method"));
    }

    @Test void reachesTheActualPinnedMixinStateAndItsSubsequentClassNodeClone() throws Exception {
        ClassNode template = new ClassNode(); template.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/NativeAdjusterMixin", null, "java/lang/Object", null);
        MethodNode handler = copy(fontMixin.methods.stream().filter(method -> method.name.equals("argentum$captureColor")).findFirst().orElseThrow());
        template.methods.add(handler);
        Class<?> infoType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");
        Class<?> stateType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$State");
        Class<?> classInfoType = Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo");
        var objectInfo = classInfoType.getDeclaredField("OBJECT"); objectInfo.setAccessible(true);
        Object classInfo = objectInfo.get(null);
        var constructor = stateType.getDeclaredConstructor(infoType, ClassNode.class, classInfoType); constructor.setAccessible(true);
        Object state = constructor.newInstance(null, template, classInfo);
        var create = stateType.getDeclaredMethod("createClassNode", int.class); create.setAccessible(true);
        ClassNode clone = (ClassNode) create.invoke(state, 0);
        MethodNode copied = clone.methods.get(0); AdjustableAnnotationNode annotation = adjustable(injector(copied));
        adjuster.adjust(List.of(fontTarget.name), fontMixin.name.replace('/', '.'), copied, annotation);
        copied.visibleAnnotations.set(copied.visibleAnnotations.indexOf(injector(copied)), annotation);
        StateExtension.tryAs(state, extension -> extension.setClassNode(clone));
        ClassNode applied = (ClassNode) create.invoke(state, 0);
        assertEquals(List.of("setColor(FFFF)V"), value(injector(applied.methods.get(0)), "method"));
        assertNotEquals(value(injector(applied.methods.get(0)), "method"), value(injector(template.methods.get(0)), "method"));
        assertNotSame(clone, applied); assertNotSame(copied, applied.methods.get(0));
    }

    @Test void verifiesThePinnedExtensionRunsBeforeMixinContextsAreCreated() throws Exception {
        ClassNode target = library("org/spongepowered/asm/mixin/transformer/TargetClassContext");
        List<String> calls = calls(target.methods.stream().filter(method -> method.name.equals("applyMixins")).findFirst().orElseThrow());
        assertTrue(calls.indexOf("preApply") < calls.indexOf("apply"));
        assertTrue(calls(target.methods.stream().filter(method -> method.name.equals("preApply")).findFirst().orElseThrow()).contains("preApply"));
        ClassNode applicator = library("org/spongepowered/asm/mixin/transformer/MixinApplicatorStandard");
        assertTrue(calls(applicator.methods.stream().filter(method -> method.name.equals("apply")).findFirst().orElseThrow()).contains("createContextFor"));
    }

    @Test void relocatesActualChunkWeatherAndCarvingCallbacksWithBodiesAndShareAnnotationsIntact() throws Exception {
        Map<String,String> classes = Map.of(
            "dev/rdh/argentum/mixin/core/world/ChunkCacheMixin", "argentum-",
            "org/polyfrost/polyweather/mixin/client/Mixin_FixWorldColors", "polyweather-",
            "dev/rdh/sarcio/mixin/mem/leak/world/AnvilChunkStorageMixin", "sarcio-",
            "dev/rdh/sarcio/mixin/opt/worldgen/ServerChunkCacheMixin", "sarcio-",
            "dev/rdh/sarcio/mixin/opt/worldgen/WorldCarverMixin", "sarcio-");
        Set<String> handlers = Set.of("markLoaded", "sendLoadEventIfLoaded", "modifyPrecipitationStrength", "modifyThunderStrength",
            "closeChunkStream", "sarcio$takePrefetched", "sarcio$prefetchedBiome");
        Map<String,List<String>> pairedShare = new HashMap<>(); int changed = 0;
        for (var entry : classes.entrySet()) {
            ClassNode mixin = read(module(entry.getValue()), entry.getKey()); List<String> targets = targets(mixin);
            for (MethodNode source : mixin.methods) {
                if (!handlers.contains(source.name)) continue;
                MethodNode handler = copy(source); AdjustableAnnotationNode annotation = adjustable(injector(handler));
                var before = spec(annotation); List<?> original = (List<?>) value(annotation, "method");
                List<AbstractInsnNode> body = List.of(handler.instructions.toArray());
                String[] parameters = parameters(handler);
                adjuster.adjust(targets, mixin.name.replace('/', '.'), handler, annotation);
                List<String> selected = ((List<?>) value(annotation, "method")).stream().map(Object::toString).toList();
                assertNotEquals(original, selected, source.name);
                var after = spec(annotation); before.remove("method"); after.remove("method"); assertEquals(before, after, source.name);
                assertEquals(body, List.of(handler.instructions.toArray())); assertArrayEquals(parameters, parameters(handler));
                if (source.name.equals("markLoaded") || source.name.equals("sendLoadEventIfLoaded")) pairedShare.put(source.name, selected);
                int matches = targetCount(targets, selected, annotation);
                assertTrue(matches >= 1, source.name + " must retain a real required target");
                changed++;
            }
        }
        assertEquals(7, changed);
        assertEquals(pairedShare.get("markLoaded"), pairedShare.get("sendLoadEventIfLoaded"));
    }

    @Test void addsTheThreeActualGlintCameraAliasesWithoutChangingTheirOrdinalOrCallbackAbi() throws Exception {
        int changed = 0;
        for (String name : List.of("ItemEntityRendererMixin_HandleCameraTransforms", "ItemRendererMixin_HandleCameraTransforms")) {
            ClassNode mixin = read(module("glintcolorizer-"), "org/polyfrost/glintcolorizer/mixin/" + name);
            List<String> targets = targets(mixin);
            for (MethodNode source : mixin.methods) {
                if (!source.name.startsWith("glintcolorizer$setCameraTransform")) continue;
                MethodNode handler = copy(source); AdjustableAnnotationNode annotation = adjustable(injector(handler));
                AnnotationNode old = (AnnotationNode) ((List<?>) value(annotation, "at")).get(0);
                var originalAt = spec(old); var before = spec(annotation); List<AbstractInsnNode> body = List.of(handler.instructions.toArray());
                adjuster.adjust(targets, mixin.name.replace('/', '.'), handler, annotation);
                List<?> points = (List<?>) value(annotation, "at"); assertEquals(2, points.size());
                assertEquals(originalAt, spec((AnnotationNode) points.get(0)));
                var alternate = spec((AnnotationNode) points.get(1)); alternate.remove("target"); originalAt.remove("target"); assertEquals(originalAt, alternate);
                assertTrue(value((AnnotationNode) points.get(1), "target").toString().startsWith("Lnet/minecraftforge/client/ForgeHooksClient;handleCameraTransforms("));
                var after = spec(annotation); before.remove("at"); after.remove("at"); assertEquals(before, after);
                assertEquals(body, List.of(handler.instructions.toArray()));
                assertTrue(targetCount(targets, ((List<?>) value(annotation, "method")).stream().map(Object::toString).toList(), annotation) >= 1);
                var once = spec(annotation); adjuster.adjust(targets, mixin.name.replace('/', '.'), handler, annotation); assertEquals(once, spec(annotation)); changed++;
            }
        }
        assertEquals(3, changed);
    }

    @Test void retainsTheActualHorizonProviderCallAndVerifiesTheGuardedNativeHandlerOnTheJvm() throws Exception {
        ClassNode mixin = read(module("sarcio-"), "dev/rdh/sarcio/mixin/bugfix/render/WorldHorizonMixin");
        MethodNode source = mixin.methods.stream().filter(method -> method.name.equals("sarcio$horizonAtVoid")).findFirst().orElseThrow();
        MethodNode handler = copy(source); AdjustableAnnotationNode annotation = adjustable(injector(handler));
        String descriptor = handler.desc; int locals = handler.maxLocals, stack = handler.maxStack;
        long frames = Arrays.stream(handler.instructions.toArray()).filter(instruction -> instruction instanceof FrameNode).count();
        List<String> targets = targets(mixin);
        adjuster.adjust(targets, mixin.name.replace('/', '.'), handler, annotation);
        assertEquals(descriptor, handler.desc); assertEquals(locals, handler.maxLocals); assertEquals(stack, handler.maxStack);
        assertEquals(frames, Arrays.stream(handler.instructions.toArray()).filter(instruction -> instruction instanceof FrameNode).count());
        assertEquals(1, targetCount(targets, ((List<?>) value(annotation, "method")).stream().map(Object::toString).toList(), annotation));
        AnnotationNode at = (AnnotationNode) ((List<?>) value(annotation, "at")).get(0);
        assertEquals("INVOKE", value(at, "value")); assertNull(value(at, "args"));
        ClassNode fixture = new ClassNode(); fixture.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/NativeHorizonGuard", null, "java/lang/Object", null);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); fixture.methods.add(constructor); fixture.methods.add(handler);
        ClassWriter writer = new ClassWriter(0); fixture.accept(writer);
        class Loader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass("fixture.NativeHorizonGuard", bytes, 0, bytes.length); } }
        Class<?> loaded = new Loader().define(writer.toByteArray()); Object instance = loaded.getConstructor().newInstance();
        var method = loaded.getDeclaredMethod(source.name, double.class); method.setAccessible(true);
        for (double input : new double[]{63, 0, -0.0D, 48, 80, -64, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.longBitsToDouble(0x7ff8000000000011L)}) {
            double output = (Double) method.invoke(instance, input);
            assertEquals(Double.doubleToRawLongBits(input == 63 ? 0 : input), Double.doubleToRawLongBits(output));
        }
        var once = spec(annotation); List<AbstractInsnNode> body = List.of(handler.instructions.toArray());
        adjuster.adjust(targets, mixin.name.replace('/', '.'), handler, annotation);
        assertEquals(once, spec(annotation)); assertEquals(body, List.of(handler.instructions.toArray()));
    }

    @Test void restoresBothActualArmorCacheTargetsWithoutChangingItsOperandAbiOrRequireTwo() throws Exception {
        ClassNode mixin = read(module("sarcio-"), "dev/rdh/sarcio/mixin/mem/alloc/render/AbstractArmorLayerMixin");
        MethodNode source = mixin.methods.stream().filter(method -> method.name.equals("sarcio$cacheArmorTexture")).findFirst().orElseThrow();
        MethodNode handler = copy(source); AdjustableAnnotationNode annotation = adjustable(injector(handler));
        var before = spec(annotation); List<AbstractInsnNode> body = List.of(handler.instructions.toArray());
        adjuster.adjust(targets(mixin), mixin.name.replace('/', '.'), handler, annotation);
        assertEquals(source.desc, handler.desc); assertEquals(body, List.of(handler.instructions.toArray()));
        assertEquals(2, value(annotation, "require"));
        List<String> selected = ((List<?>) value(annotation, "method")).stream().map(Object::toString).toList();
        assertTrue(selected.get(0).startsWith("getArmorResource("));
        assertEquals(((List<?>) before.get("method")).get(1), selected.get(1));
        var after = spec(annotation); before.remove("method"); after.remove("method"); assertEquals(before, after);
        assertEquals(2, targetCount(targets(mixin), selected, annotation));
    }

    private static int targetCount(List<String> targets, List<String> selected, AnnotationNode annotation) throws Exception {
        int total = 0;
        Path runtime = game.resolve(".fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar");
        for (String targetName : targets) {
            ClassNode target = read(runtime, targetName);
            if (target.name.equals(mappings.classFromSrg("net/minecraft/client/renderer/entity/layers/LayerArmorBase"))) {
                HostArmorCompatibility.normalize(target, mappings);
            }
            for (String request : selected) {
                MemberInfo member = MemberInfo.parse(request, null);
                for (MethodNode method : target.methods) {
                    if (!method.name.equals(member.getName()) || (member.getDesc() != null && !method.desc.equals(member.getDesc()))) continue;
                    for (Object item : (List<?>) value(annotation, "at")) {
                        AnnotationNode at = (AnnotationNode) item;
                        if (value(at, "value").equals("RETURN")) total += (int) Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= Opcodes.IRETURN && instruction.getOpcode() <= Opcodes.RETURN).count();
                        else {
                            var point = new BeforeInvoke(new InjectionPointData(HostCompatibilityPatchesTest.context(target.name), "INVOKE", List.of(),
                                (String) value(at, "target"), "", ((Number) Optional.ofNullable(value(at, "ordinal")).orElse(-1)).intValue(), -1, "native-relocation", 0));
                            List<AbstractInsnNode> matches = new ArrayList<>(); point.find(method.desc, method.instructions, matches); total += matches.size();
                        }
                    }
                }
            }
        }
        return total;
    }
    private static List<String> targets(ClassNode mixin) { return ((List<?>) value(mixin.invisibleAnnotations.stream().filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow(), "value")).stream().map(value -> ((org.objectweb.asm.Type) value).getInternalName()).toList(); }
    private static String[] parameters(MethodNode method) { List<String> values = new ArrayList<>(); if(method.invisibleParameterAnnotations!=null)for(var annotations:method.invisibleParameterAnnotations)if(annotations!=null)for(var annotation:annotations)values.add(spec(annotation).toString()); return values.toArray(String[]::new); }

    private static Path module(String prefix) throws Exception {
        for (String folder : List.of("mods", ".fabric/processedMods")) {
            try (var paths = Files.list(game.resolve(folder))) {
                var found = paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(prefix)).findFirst();
                if (found.isPresent()) return found.get();
            }
        }
        throw new IllegalStateException("The native fixture is missing " + prefix);
    }
    private static ClassNode read(Path jar, String name) throws Exception { ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static ClassNode library(String name) throws Exception { ClassNode node = new ClassNode(); try (var input = HostMixinCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) { assertNotNull(input); new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static MappingResolver resolver(String tiny) throws Exception { MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree); var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary"); }
    private static MethodNode copy(MethodNode original) { MethodNode node = new MethodNode(original.access, original.name, original.desc, original.signature, original.exceptions.toArray(String[]::new)); original.accept(node); return node; }
    private static AnnotationNode injector(MethodNode method) { return method.visibleAnnotations.stream().filter(annotation -> value(annotation, "method") != null).findFirst().orElseThrow(); }
    private static AdjustableAnnotationNode adjustable(AnnotationNode annotation) { AdjustableAnnotationNode node = AdjustableAnnotationNode.fromNode(annotation); if (node instanceof RemappableAnnotationNode remappable) remappable.setRemapper(value -> value.applyRefmap(UnaryOperator.identity())); return node; }
    private static Object value(AnnotationNode annotation, String key) { for (int i=0; annotation.values!=null && i<annotation.values.size(); i+=2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i+1); return null; }
    private static Map<String,Object> spec(AnnotationNode annotation) { Map<String,Object> result=new LinkedHashMap<>(); result.put("descriptor",annotation.desc);for(int i=0;annotation.values!=null&&i<annotation.values.size();i+=2)result.put(annotation.values.get(i).toString(),specValue(annotation.values.get(i+1)));return result; }
    private static Object specValue(Object value) { if(value instanceof AnnotationNode annotation)return spec(annotation);if(value instanceof List<?> list)return list.stream().map(HostMixinCompatibilityTest::specValue).toList();if(value instanceof String[] array)return List.of(array);return value; }
    private static List<String> calls(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode).map(instruction -> ((MethodInsnNode)instruction).name).toList(); }
}
