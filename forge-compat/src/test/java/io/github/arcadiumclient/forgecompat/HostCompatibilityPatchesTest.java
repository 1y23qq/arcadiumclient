package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.points.BeforeNew;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.util.asm.IAnnotationHandle;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class HostCompatibilityPatchesTest {
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String VANILLA = "net/minecraft/unmapped/C_97866173";
    private static final String FORGE = "net/minecraftforge/client/GuiIngameForge";
    @TempDir Path temporary;

    @BeforeAll static void configure() throws Exception {
        MappingResolver resolver = (MappingResolver) java.lang.reflect.Proxy.newProxyInstance(
            MappingResolver.class.getClassLoader(), new Class<?>[]{MappingResolver.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getNamespaces" -> List.of("official", "intermediary");
                case "getCurrentRuntimeNamespace" -> "intermediary";
                case "mapClassName" -> args[1].equals("ave") ? MINECRAFT.replace('/', '.')
                    : args[1].equals("avo") ? VANILLA.replace('/', '.') : args[1];
                case "unmapClassName" -> args[1].equals(MINECRAFT.replace('/', '.')) ? "ave"
                    : args[1].equals(VANILLA.replace('/', '.')) ? "avo" : args[1];
                case "mapFieldName", "mapMethodName" -> args[2];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        HostCompatibilityPatches.install(new ForgeMappings(resolver, new StringReader("""
            CL: ave net/minecraft/client/Minecraft
            CL: avo net/minecraft/client/gui/GuiIngame
            CL: b net/minecraft/crash/CrashReport
            MD: ave/c (Lb;)V net/minecraft/client/Minecraft/func_71377_b (Lnet/minecraft/crash/CrashReport;)V
            """)));
    }

    @AfterAll static void restoreDefaultSelector() {
        InjectionPoint.register(BeforeNew.class);
        InjectionPoint.register(BeforeInvoke.class);
    }

    private InjectionPointData data(String targetClass, String allocation, int ordinal) {
        return new InjectionPointData(context(targetClass), "NEW", List.of(), allocation, "resource-listeners", ordinal,
            Opcodes.NEW, "osl-resource-init", 0);
    }

    static IInjectionPointContext context(String target) {
        IMixinContext mixin = new IMixinContext() {
            @Override public IMixinInfo getMixin() { return null; }
            @Override public Extensions getExtensions() { return null; }
            @Override public String getClassName() { return "fixture.ResourceMixin"; }
            @Override public String getClassRef() { return "fixture/ResourceMixin"; }
            @Override public String getTargetClassName() { return target.replace('/', '.'); }
            @Override public String getTargetClassRef() { return target; }
            @Override public IReferenceMapper getReferenceMapper() { return ReferenceMapper.DEFAULT_MAPPER; }
            @Override public boolean getOption(MixinEnvironment.Option option) { return false; }
            @Override public int getPriority() { return 1000; }
        };
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, "initResourceManager", "()V", null, null);
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        return new IInjectionPointContext() {
            @Override public MethodNode getMethod() { return method; }
            @Override public AnnotationNode getAnnotationNode() { return annotation; }
            @Override public ISelectorContext getParent() { return null; }
            @Override public IMixinContext getMixin() { return mixin; }
            @Override public IAnnotationHandle getAnnotation() { return null; }
            @Override public IAnnotationHandle getSelectorAnnotation() { return null; }
            @Override public String getSelectorCoordinate(boolean leaf) { return "fixture resource initializer"; }
            @Override public String remap(String value) { return value; }
            @Override public String getElementDescription() { return "fixture resource initializer"; }
            @Override public void addMessage(String message, Object... args) {}
        };
    }

    private static TypeInsnNode allocation(InsnList code, String owner, String descriptor) {
        TypeInsnNode instruction = new TypeInsnNode(Opcodes.NEW, owner);
        code.add(instruction);
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "<init>", descriptor, false));
        code.add(new InsnNode(Opcodes.POP));
        return instruction;
    }

    private static List<AbstractInsnNode> matches(BeforeNew point, InsnList code) {
        List<AbstractInsnNode> nodes = new ArrayList<>();
        point.find("()V", code, nodes);
        return nodes;
    }

    @Test void preservesTheNormalBeforeNewMatchAndItsOriginalNode() {
        InsnList code = new InsnList();
        var original = allocation(code, VANILLA, "()V");
        allocation(code, FORGE, "()V");
        var input = data(MINECRAFT, "L" + VANILLA + ";", -1);
        assertEquals(matches(new BeforeNew(input), code), matches(new HostCompatibilityPatches(input), code));
        assertEquals(List.of(original), matches(new HostCompatibilityPatches(input), code));
    }

    @Test void matchesTheRealForgeReplacementOnlyWhenTheVanillaAllocationIsAbsent() {
        InsnList code = new InsnList();
        var replacement = allocation(code, FORGE, "(L" + MINECRAFT + ";)V");
        var input = data(MINECRAFT, "L" + VANILLA + ";", -1);
        assertTrue(matches(new BeforeNew(input), code).isEmpty());
        assertEquals(List.of(replacement), matches(new HostCompatibilityPatches(input), code));
    }

    @Test void delegatesConstructorDescriptorValidationToTheRealBeforeNewImplementation() {
        InsnList code = new InsnList();
        var replacement = allocation(code, FORGE, "(L" + MINECRAFT + ";)V");
        assertEquals(List.of(replacement), matches(new HostCompatibilityPatches(
            data(MINECRAFT, "(L" + MINECRAFT + ";)L" + VANILLA + ";", -1)), code));
        assertTrue(matches(new HostCompatibilityPatches(data(MINECRAFT, "(I)L" + VANILLA + ";", -1)), code).isEmpty());
    }

    @Test void preservesOrdinalsAndDoesNotBroadenAnOutOfRangeRequest() {
        InsnList code = new InsnList();
        allocation(code, FORGE, "()V");
        var second = allocation(code, FORGE, "()V");
        assertEquals(List.of(second), matches(new HostCompatibilityPatches(data(MINECRAFT, "L" + VANILLA + ";", 1)), code));
        assertTrue(matches(new HostCompatibilityPatches(data(MINECRAFT, "L" + VANILLA + ";", 2)), code).isEmpty());
    }

    @Test void neverReinterpretsAConstructorMismatchWhenVanillaIsStillPresent() {
        InsnList code = new InsnList();
        allocation(code, VANILLA, "()V");
        allocation(code, FORGE, "(I)V");
        assertTrue(matches(new HostCompatibilityPatches(data(MINECRAFT, "(I)L" + VANILLA + ";", -1)), code).isEmpty());
    }

    @Test void leavesOtherTargetsAndOtherNewSelectorsUnchanged() {
        InsnList code = new InsnList();
        allocation(code, FORGE, "()V");
        assertTrue(matches(new HostCompatibilityPatches(data("fixture/OtherTarget", "L" + VANILLA + ";", -1)), code).isEmpty());
        assertTrue(matches(new HostCompatibilityPatches(data(MINECRAFT, "Ljava/lang/String;", -1)), code).isEmpty());
        var unconstrained = data(MINECRAFT, "", -1);
        var originalFailure = assertThrows(RuntimeException.class, () -> new BeforeNew(unconstrained));
        var compatibleFailure = assertThrows(RuntimeException.class, () -> new HostCompatibilityPatches(unconstrained));
        assertEquals(originalFailure.getClass(), compatibleFailure.getClass());
    }

    @Test void keepsTheOriginalOuterPointMetadataAndNeverMutatesTargetInstructions() {
        InsnList code = new InsnList();
        allocation(code, FORGE, "()V");
        List<AbstractInsnNode> before = List.of(code.toArray());
        var point = new HostCompatibilityPatches(data(MINECRAFT, "L" + VANILLA + ";", -1));
        assertEquals("resource-listeners", point.getSlice());
        assertEquals("osl-resource-init", point.getId());
        assertEquals(1, matches(point, code).size());
        assertEquals(before, List.of(code.toArray()));
    }

    @Test void keepsTheActualOslResourceCallbacksAndFindsTheirRealForgeAllocation() throws Exception {
        Path dependency = Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/net.ornithemc.osl-gen2/resource-loader/0.8.1+mc1.8.2-pre5-mc1.12.2/6e31b121ce9f87e60b1b63670ea34f5ef595a3fe/resource-loader-0.8.1+mc1.8.2-pre5-mc1.12.2.jar");
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Assumptions.assumeTrue(Files.isRegularFile(dependency) && Files.isRegularFile(client));
        ClassNode mixin = new ClassNode();
        try (ZipFile zip = new ZipFile(dependency.toFile());
             var input = zip.getInputStream(zip.getEntry("net/ornithemc/osl/resource/loader/impl/mixin/client/MinecraftMixin.class"))) {
            new ClassReader(input.readAllBytes()).accept(mixin, 0);
        }
        MethodNode callback = mixin.methods.stream().filter(method -> method.name.equals("osl$resource_loader$initResourceManager"))
            .findFirst().orElseThrow();
        assertEquals(List.of("init", "partialReload"), java.util.Arrays.stream(callback.instructions.toArray())
            .filter(instruction -> instruction instanceof MethodInsnNode).map(instruction -> ((MethodInsnNode) instruction).name).toList());
        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) {
            assertNotNull(input); Files.copy(input, patches);
        }
        Path patched = new ForgeGamePatcher().patch(client, patches, temporary.resolve("patched"));
        ClassNode minecraft = new ClassNode();
        try (ZipFile zip = new ZipFile(patched.toFile()); var input = zip.getInputStream(zip.getEntry("ave.class"))) {
            new ClassReader(input.readAllBytes()).accept(minecraft, 0);
        }
        MethodNode init = minecraft.methods.stream().filter(method -> method.name.equals("am") && method.desc.equals("()V"))
            .findFirst().orElseThrow();
        var original = data(MINECRAFT, "L" + VANILLA + ";", -1);
        assertTrue(matches(new BeforeNew(original), init.instructions).isEmpty());
        var nodes = matches(new HostCompatibilityPatches(original), init.instructions);
        assertEquals(1, nodes.size());
        assertEquals(FORGE, ((TypeInsnNode) nodes.get(0)).desc);
        ClassWriter output = new ClassWriter(0);
        mixin.accept(output);
        assertTrue(callback.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.endsWith("/Inject;")));
    }
}
