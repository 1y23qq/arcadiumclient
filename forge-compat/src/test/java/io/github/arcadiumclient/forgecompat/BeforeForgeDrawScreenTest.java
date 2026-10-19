package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import static org.junit.jupiter.api.Assertions.*;

class BeforeForgeDrawScreenTest {
    private static final String SCREEN = "net/minecraft/client/gui/GuiScreen";
    private static final String RENDERER = "net/minecraft/client/renderer/EntityRenderer";
    private static final String HOOK = "net/minecraftforge/client/ForgeHooksClient";
    @TempDir Path temporary;

    @BeforeAll static void configureMixin() throws Exception { HostCompatibilityPatchesTest.configure(); }
    @AfterAll static void restore() { InjectionPoint.register(BeforeInvoke.class); }

    private ForgeMappings mappings() throws Exception {
        MappingResolver resolver = (MappingResolver) java.lang.reflect.Proxy.newProxyInstance(
            MappingResolver.class.getClassLoader(), new Class<?>[]{MappingResolver.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getNamespaces" -> List.of("official", "intermediary");
                case "getCurrentRuntimeNamespace" -> "intermediary";
                case "mapClassName", "unmapClassName" -> args[1];
                case "mapFieldName", "mapMethodName" -> args[2];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var input = getClass().getResourceAsStream("/META-INF/arcadium/forge.srg.gz");
        assertNotNull(input);
        try (var reader = new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8)) {
            var mappings = new ForgeMappings(resolver, reader);
            BeforeForgeInvoke.install(mappings);
            return mappings;
        }
    }

    private InjectionPointData data(ForgeMappings mappings, String enclosingClass, int ordinal) {
        String target = "L" + mappings.classFromSrg(SCREEN) + ";"
            + mappings.methodFromSrg(SCREEN, "func_73863_a", "(IIF)V") + "(IIF)V";
        return new InjectionPointData(HostCompatibilityPatchesTest.context(enclosingClass), "INVOKE", List.of(), target,
            "screen-render", ordinal, Opcodes.INVOKEVIRTUAL, "projection", 0);
    }

    private List<AbstractInsnNode> matches(BeforeInvoke point, InsnList instructions) {
        var result = new ArrayList<AbstractInsnNode>();
        point.find("(FJ)V", instructions, result);
        return result;
    }

    @Test void matchesTheRealForgeHelperWithTheSameWrapOperationOperands() throws Exception {
        var mappings = mappings();
        Path original = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Assumptions.assumeTrue(Files.isRegularFile(original));
        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) {
            assertNotNull(input); Files.copy(input, patches);
        }
        Path patched = new ForgeGamePatcher().patch(original, patches, temporary.resolve("patched"));
        String renderer = mappings.classFromSrg(RENDERER);
        ClassNode forge = read(patched, renderer);
        MethodNode render = forge.methods.stream().filter(method -> Arrays.stream(method.instructions.toArray())
            .anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(HOOK)
                && call.name.equals("drawScreen"))).findFirst().orElseThrow();
        byte[] before = bytes(forge);
        var input = data(mappings, renderer, -1);
        assertTrue(matches(new BeforeInvoke(input), render.instructions).isEmpty());
        var found = matches(new BeforeForgeInvoke(input), render.instructions);
        assertEquals(1, found.size());
        var call = (MethodInsnNode) found.get(0);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(List.of(Type.getObjectType(mappings.classFromSrg(SCREEN)), Type.INT_TYPE, Type.INT_TYPE, Type.FLOAT_TYPE),
            List.of(Type.getArgumentTypes(call.desc)));
        assertEquals(Type.VOID_TYPE, Type.getReturnType(call.desc));
        assertArrayEquals(before, bytes(forge));
        MethodNode vanilla = read(original, renderer).methods.stream().filter(method -> method.name.equals(render.name)
            && method.desc.equals(render.desc)).findFirst().orElseThrow();
        assertEquals(matches(new BeforeInvoke(input), vanilla.instructions), matches(new BeforeForgeInvoke(input), vanilla.instructions));
        assertEquals(1, matches(new BeforeForgeInvoke(input), vanilla.instructions).size());
    }

    @Test void preservesOrdinalsOriginalPriorityAndOtherClassBoundaries() throws Exception {
        var mappings = mappings();
        String renderer = mappings.classFromSrg(RENDERER);
        String screen = mappings.classFromSrg(SCREEN);
        var code = new InsnList();
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "drawScreen", "(L" + screen + ";IIF)V", false));
        assertEquals(1, matches(new BeforeForgeInvoke(data(mappings, renderer, 0)), code).size());
        assertTrue(matches(new BeforeForgeInvoke(data(mappings, renderer, 1)), code).isEmpty());
        assertTrue(matches(new BeforeForgeInvoke(data(mappings, "fixture/Other", -1)), code).isEmpty());
        var original = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, screen,
            mappings.methodFromSrg(SCREEN, "func_73863_a", "(IIF)V"), "(IIF)V", false);
        code.insert(original);
        assertEquals(List.of(original), matches(new BeforeForgeInvoke(data(mappings, renderer, -1)), code));
        assertTrue(matches(new BeforeForgeInvoke(data(mappings, renderer, 1)), code).isEmpty());
    }

    private static ClassNode read(Path jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node;
        }
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter output = new ClassWriter(0); node.accept(output); return output.toByteArray();
    }
}
