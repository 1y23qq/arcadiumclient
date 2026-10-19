package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import static org.junit.jupiter.api.Assertions.*;

class GuestMixinVisibilityTest {
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String TARGET = "net/minecraft/unmapped/VisibilityTarget";
    @TempDir Path directory;

    @BeforeAll static void mixinEnvironment() throws Exception { HostClassMetadataTest.initializeMixinProperties(); }

    @Test void matchesActualPreMixinBytesAndPreservesBodyDescriptorAnnotationsAndOtherFlags() {
        for (int visibility : new int[]{0, Opcodes.ACC_PROTECTED}) {
            Probe launcher = new Probe(Map.of(TARGET, target(TARGET, Opcodes.ACC_PUBLIC)));
            int flags = visibility | Opcodes.ACC_FINAL | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_SYNTHETIC;
            MethodNode source = method(flags), other = method(Opcodes.ACC_PROTECTED);
            AdjustableAnnotationNode annotation = overwrite(); source.visibleAnnotations = new ArrayList<>(List.of(annotation));
            List<AbstractInsnNode> body = Arrays.asList(source.instructions.toArray()); List<Object> options = List.copyOf(annotation.values);
            assertSame(annotation, adjuster(launcher).adjust(List.of(TARGET.replace('/', '.')), "fixture.Mixin", source, annotation));
            assertEquals((flags & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC, source.access);
            assertEquals("()I", source.desc); assertEquals("work", source.name);
            assertEquals(body, Arrays.asList(source.instructions.toArray())); assertEquals(options, annotation.values);
            assertSame(annotation, source.visibleAnnotations.get(0)); assertEquals(Opcodes.ACC_PROTECTED, other.access);
            assertEquals(List.of(TARGET.replace('/', '.')), launcher.reads);
        }
    }

    @Test void keepsTheWidestRealTargetVisibilityAcrossMultipleTargetsAndDefersUnmatchedSelectors() {
        String second = TARGET + "Second";
        Probe launcher = new Probe(Map.of(TARGET, target(TARGET, Opcodes.ACC_PROTECTED), second, target(second, Opcodes.ACC_PUBLIC)));
        MethodNode source = method(Opcodes.ACC_PROTECTED);
        adjuster(launcher).adjust(List.of(TARGET, second), "fixture.Mixin", source, overwrite());
        assertEquals(Opcodes.ACC_PUBLIC, source.access); assertEquals(2, launcher.reads.size());
        Probe missing = new Probe(Map.of(TARGET, target(TARGET, Opcodes.ACC_PUBLIC)));
        source = method(Opcodes.ACC_PROTECTED); source.desc = "(I)I";
        adjuster(missing).adjust(List.of(TARGET, TARGET + "Missing"), "fixture.Mixin", source, overwrite());
        assertEquals(Opcodes.ACC_PROTECTED, source.access);
        source = method(Opcodes.ACC_PROTECTED);
        adjuster(launcher).adjust(List.of(TARGET), "fixture.Mixin", source, overwrite());
        assertEquals(Opcodes.ACC_PROTECTED, source.access);
    }

    @Test void skipsPrivatePublicOtherAnnotationsAndUnrelatedTargetsWithoutReadingClasses() {
        Probe launcher = new Probe(Map.of(TARGET, target(TARGET, Opcodes.ACC_PUBLIC)));
        var adjuster = adjuster(launcher);
        for (int access : new int[]{Opcodes.ACC_PRIVATE, Opcodes.ACC_PUBLIC}) {
            MethodNode source = method(access); adjuster.adjust(List.of(TARGET), "fixture.Mixin", source, overwrite()); assertEquals(access, source.access);
        }
        for (String kind : List.of("Shadow", "injection/Inject")) {
            MethodNode source = method(Opcodes.ACC_PROTECTED);
            AdjustableAnnotationNode annotation = AdjustableAnnotationNode.fromNode(new AnnotationNode("Lorg/spongepowered/asm/mixin/" + kind + ";"));
            assertSame(annotation, adjuster.adjust(List.of(TARGET), "fixture.Mixin", source, annotation)); assertEquals(Opcodes.ACC_PROTECTED, source.access);
        }
        MethodNode source = method(Opcodes.ACC_PROTECTED);
        adjuster.adjust(List.of("example/Unrelated"), "fixture.Mixin", source, overwrite());
        adjuster.adjust(List.of(TARGET), "org.polyfrost.native.NativeMixin", source, overwrite());
        assertEquals(Opcodes.ACC_PROTECTED, source.access); assertTrue(launcher.reads.isEmpty());
    }

    @Test void usesCompletedBytesInsteadOfStaleClassInfoAccessMetadata() throws Exception {
        String name = TARGET + UUID.randomUUID().toString().replace("-", "");
        ClassNode stale = targetNode(name, Opcodes.ACC_PROTECTED); ClassInfo cached = register(stale);
        assertEquals(Opcodes.ACC_PROTECTED, cached.findMethod("work", "()I", ClassInfo.INCLUDE_ALL).getAccess());
        Probe launcher = new Probe(Map.of(name, target(name, Opcodes.ACC_PUBLIC))); MethodNode source = method(Opcodes.ACC_PROTECTED);
        adjuster(launcher).adjust(List.of(name), "fixture.Mixin", source, overwrite());
        assertEquals(Opcodes.ACC_PUBLIC, source.access); assertSame(cached, ClassInfo.fromCache(name));
        assertEquals(Opcodes.ACC_PROTECTED, cached.findMethod("work", "()I", ClassInfo.INCLUDE_ALL).getAccess());
    }

    @Test void reportsUnreadableOrMismatchedTargetBytesWithoutChangingTheOverwrite() {
        for (Object input : List.of(new IOException("fixture failure"), new byte[]{1, 2}, target(TARGET + "Wrong", Opcodes.ACC_PUBLIC))) {
            Probe launcher = new Probe(Map.of(TARGET, input)); MethodNode source = method(Opcodes.ACC_PROTECTED);
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> adjuster(launcher).adjust(List.of(TARGET), "fixture.Mixin", source, overwrite()));
            assertTrue(error.getMessage().contains(TARGET)); assertNotNull(error.getCause()); assertEquals(Opcodes.ACC_PROTECTED, source.access);
        }
    }

    @Test void privateOverwritesStillUsePinnedMixinsVisibilityAndInvocationUpgradePath() throws Exception {
        Class<?> infoType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");
        Object info = allocate(infoType); set(info, "name", "PrivateVisibilityMixin");
        Class<?> configType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig");
        Class<?> gson = Class.forName("org.spongepowered.include.com.google.gson.Gson");
        Object config = gson.getMethod("fromJson", Reader.class, Class.class).invoke(gson.getConstructor().newInstance(), new StringReader("{\"overwrites\":{\"conformVisibility\":true}}"), configType); set(info, "parent", config);
        Class<?> methodType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$MixinMethodNode");
        var constructor = methodType.getDeclaredConstructor(infoType, int.class, String.class, String.class, String.class, String[].class); constructor.setAccessible(true);
        MethodNode source = (MethodNode) constructor.newInstance(info, Opcodes.ACC_PRIVATE, "work", "()I", null, null);
        source.visitInsn(Opcodes.ICONST_5); source.visitInsn(Opcodes.IRETURN); source.maxStack = 1; source.maxLocals = 1;
        Probe launcher = new Probe(Map.of(TARGET, target(TARGET, Opcodes.ACC_PUBLIC)));
        adjuster(launcher).adjust(List.of(TARGET), "fixture.Mixin", source, overwrite());
        assertEquals(Opcodes.ACC_PRIVATE, source.access); assertTrue(launcher.reads.isEmpty());
        String owner = "fixture/PrivateVisibility" + UUID.randomUUID().toString().replace("-", "");
        ClassNode metadata = targetNode(owner, Opcodes.ACC_PRIVATE); ClassInfo classInfo = register(metadata); set(info, "info", classInfo);
        Class<?> nodeType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$MixinClassNode");
        var nodeConstructor = nodeType.getDeclaredConstructor(infoType, int.class); nodeConstructor.setAccessible(true);
        ClassNode node = (ClassNode) nodeConstructor.newInstance(info, Opcodes.ASM9); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(source);
        MethodNode caller = new MethodNode(Opcodes.ACC_PUBLIC, "caller", "()I", null, null);
        caller.visitVarInsn(Opcodes.ALOAD, 0); caller.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "work", "()I", false); caller.visitInsn(Opcodes.IRETURN); caller.maxStack = 1; caller.maxLocals = 1; node.methods.add(caller);
        Class<?> contextType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTargetContext");
        Object context = allocate(contextType); set(context, "mixin", info); set(context, "classNode", node); set(context, "upgradedMethods", new HashSet<>());
        Object processor = allocate(Class.forName("org.spongepowered.asm.mixin.transformer.MixinPreProcessorStandard")); set(processor, "mixin", info);
        Class<?> specialType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinPreProcessorStandard$SpecialMethod");
        Field kind = specialType.getDeclaredField("OVERWRITE"); kind.setAccessible(true);
        Method conform = processor.getClass().getDeclaredMethod("conformVisibility", contextType, methodType, specialType, MethodNode.class); conform.setAccessible(true);
        conform.invoke(processor, context, source, kind.get(null), method(Opcodes.ACC_PUBLIC));
        assertEquals(Opcodes.ACC_PUBLIC, source.access);
        Method upgrade = contextType.getSuperclass().getDeclaredMethod("upgradeMethods"); upgrade.setAccessible(true); upgrade.invoke(context);
        MethodInsnNode call = Arrays.stream(caller.instructions.toArray()).filter(i -> i instanceof MethodInsnNode).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
    }

    @Test void allowlistContainsOnlyAnnotatedClassesFromConvertedGuestArchives() throws Exception {
        ClassNode mixin = targetNode("fixture/ConvertedMixin", Opcodes.ACC_PROTECTED);
        mixin.invisibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;")));
        Path jar = directory.resolve("converted.jar");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            for (ClassNode node : List.of(mixin, targetNode("fixture/OrdinaryGuestClass", Opcodes.ACC_PUBLIC))) {
                zip.putNextEntry(new java.util.zip.ZipEntry(node.name + ".class")); ClassWriter writer = new ClassWriter(0); node.accept(writer); zip.write(writer.toByteArray()); zip.closeEntry();
            }
        }
        assertEquals(Set.of("fixture.ConvertedMixin"), GuestMixinVisibility.guestMixins(List.of(jar)));
        Probe launcher = new Probe(Map.of(TARGET, target(TARGET, Opcodes.ACC_PUBLIC)));
        var adjuster = new GuestMixinVisibility(launcher.value, GuestMixinVisibility.guestMixins(List.of(jar)));
        MethodNode guest = method(Opcodes.ACC_PROTECTED), nativeMixin = method(Opcodes.ACC_PROTECTED);
        adjuster.adjust(List.of(TARGET), "fixture.ConvertedMixin", guest, overwrite());
        adjuster.adjust(List.of(TARGET), "org.polyfrost.native.NativeMixin", nativeMixin, overwrite());
        assertEquals(Opcodes.ACC_PUBLIC, guest.access); assertEquals(Opcodes.ACC_PROTECTED, nativeMixin.access); assertEquals(1, launcher.reads.size());
    }

    private static final class Probe {
        final List<String> reads = new ArrayList<>();
        final FabricLauncher value;
        Probe(Map<String, ?> sources) {
            value = (FabricLauncher) Proxy.newProxyInstance(FabricLauncher.class.getClassLoader(), new Class<?>[]{FabricLauncher.class}, (proxy, method, args) -> {
                if (!method.getName().equals("getClassByteArray")) throw new AssertionError(method.getName());
                assertEquals(true, args[1]); String binary = (String) args[0]; reads.add(binary); Object bytes = sources.get(binary.replace('.', '/'));
                if (bytes instanceof IOException failure) throw failure; return bytes;
            });
        }
    }
    private static AdjustableAnnotationNode overwrite() { AnnotationNode annotation = new AnnotationNode(OVERWRITE); annotation.values = new ArrayList<>(List.of("constraints", "TOKEN(1)")); return AdjustableAnnotationNode.fromNode(annotation); }
    private static GuestMixinVisibility adjuster(Probe launcher) { return new GuestMixinVisibility(launcher.value, Set.of("fixture.Mixin")); }
    private static MethodNode method(int access) { MethodNode method = new MethodNode(access, "work", "()I", null, null); method.visitInsn(Opcodes.ICONST_5); method.visitInsn(Opcodes.IRETURN); method.maxStack = 1; method.maxLocals = 1; return method; }
    private static ClassNode targetNode(String name, int access) { ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null); node.methods.add(method(access)); return node; }
    private static byte[] target(String name, int access) { ClassWriter writer = new ClassWriter(0); targetNode(name, access).accept(writer); return writer.toByteArray(); }
    private static ClassInfo register(ClassNode node) throws Exception { Method factory = ClassInfo.class.getDeclaredMethod("fromClassNode", ClassNode.class); factory.setAccessible(true); return (ClassInfo) factory.invoke(null, node); }
    private static Object allocate(Class<?> type) throws Exception { Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true); return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type); }
    private static void set(Object owner, String name, Object value) throws Exception { Class<?> type = owner.getClass(); while (type != null) { try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value); return; } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); } } throw new NoSuchFieldException(name); }
}
