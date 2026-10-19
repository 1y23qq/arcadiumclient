package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostHudCompatibilityTest {
    private static final String HUD = "net/minecraftforge/client/GuiIngameForge";

    @Test void actualForgeOverlayKeepsItsDrawingAndCancellationAndCallsNativeHudOnce() throws Exception {
        for (boolean previews : List.of(false, true)) {
          for (boolean vanilla : List.of(false, true)) {
            ClassNode node = actual();
            MethodNode method = render(node);
            List<AbstractInsnNode> original = Arrays.asList(method.instructions.toArray());
            var others = node.methods.stream().filter(value -> value != method).map(HostHudCompatibilityTest::bytes).toList();
            int locals = method.maxLocals;
            HostHudCompatibility.normalize(node, mappings(), previews, vanilla);
            int cursor = -1;
            List<AbstractInsnNode> changed = Arrays.asList(method.instructions.toArray());
            for (var instruction : original) {
                int next = changed.indexOf(instruction);
                assertTrue(next > cursor); cursor = next;
            }
            assertEquals(locals, method.maxLocals);
            assertEquals(original.size() + 1 + (previews ? 3 : 0) + (vanilla ? 2 : 0), changed.size());
            assertEquals(1, calls(method, HostHudCompatibility.CORE));
            assertEquals(previews ? 1 : 0, calls(method, HostHudCompatibility.CONTEXT));
            assertEquals(previews ? 1 : 0, calls(method, HostHudCompatibility.PREVIEW));
            assertEquals(vanilla ? 1 : 0, calls(method, HostHudCompatibility.VANILLA));
            if (vanilla) {
                var code = changed.stream().filter(value -> value.getOpcode() >= 0).toList();
                assertTrue(code.get(0) instanceof org.objectweb.asm.tree.FieldInsnNode context
                    && context.getOpcode() == Opcodes.GETSTATIC && context.owner.equals(HostHudCompatibility.VANILLA_CONTEXT)
                    && context.name.equals("INSTANCE") && context.desc.equals("L" + HostHudCompatibility.VANILLA_CONTEXT + ";"));
                assertTrue(code.get(1) instanceof MethodInsnNode frame && frame.getOpcode() == Opcodes.INVOKESTATIC
                    && frame.owner.equals(HostHudCompatibility.VANILLA) && frame.name.equals("beginFrame")
                    && frame.desc.equals("(L" + HostHudCompatibility.VANILLA_CONTEXT + ";)V"));
            }
            var returns = changed.stream().filter(value -> value.getOpcode() == Opcodes.RETURN).toList();
            assertEquals(2, returns.size());
            assertFalse(previous(returns.get(0)) instanceof MethodInsnNode call && call.owner.equals(HostHudCompatibility.CORE));
            assertTrue(previous(returns.get(1)) instanceof MethodInsnNode call && call.owner.equals(HostHudCompatibility.CORE));
            var untouched = node.methods.stream().filter(value -> value != method).map(HostHudCompatibilityTest::bytes).toList();
            for (int i = 0; i < others.size(); i++) assertArrayEquals(others.get(i), untouched.get(i));
            new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
            byte[] once = bytes(node);
            HostHudCompatibility.normalize(node, mappings(), previews, vanilla);
            assertArrayEquals(once, bytes(node));
          }
        }
    }

    @Test void rejectsUnknownOverlayBeforeMutationAndLeavesOtherClassesAlone() throws Exception {
        ClassNode node = actual(); node.name = "unrelated/Hud";
        byte[] unrelated = bytes(node);
        HostHudCompatibility.normalize(node, mappings(), true, true);
        assertArrayEquals(unrelated, bytes(node));
        node = actual();
        for (var instruction : render(node).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("post")) call.name = "differentPost";
        }
        byte[] unknown = bytes(node); ClassNode changed = node;
        assertThrows(IllegalStateException.class, () -> HostHudCompatibility.normalize(changed, mappings(), true, true));
        assertArrayEquals(unknown, bytes(node));
    }

    private static long calls(MethodNode method, String owner) {
        return Arrays.stream(method.instructions.toArray()).filter(value -> value instanceof MethodInsnNode call && call.owner.equals(owner)).count();
    }
    private static AbstractInsnNode previous(AbstractInsnNode value) {
        do { value = value.getPrevious(); } while (value != null && value.getOpcode() < 0);
        return value;
    }
    private static MethodNode render(ClassNode node) {
        return node.methods.stream().filter(method -> method.name.equals("a") && method.desc.equals("(F)V")).findFirst().orElseThrow();
    }
    private static ClassNode actual() throws Exception {
        try (var zip = new ZipFile(Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar").toFile());
             var input = zip.getInputStream(zip.getEntry(HUD + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node;
        }
    }
    private static ForgeMappings mappings() throws Exception {
        var resolver = (MappingResolver) Proxy.newProxyInstance(MappingResolver.class.getClassLoader(), new Class<?>[] {MappingResolver.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getNamespaces" -> List.of("official", "intermediary");
                case "getCurrentRuntimeNamespace" -> "intermediary";
                case "mapClassName", "unmapClassName" -> arguments[1];
                case "mapMethodName", "mapFieldName" -> arguments[2];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new ForgeMappings(resolver, new StringReader("CL: avo net/minecraft/client/gui/GuiIngame\n"
            + "MD: avo/a (F)V net/minecraft/client/gui/GuiIngame/func_175180_a (F)V\n"));
    }
    private static byte[] bytes(MethodNode method) {
        ClassNode node = new ClassNode(); node.version = Opcodes.V17; node.name = "fixture/Method"; node.superName = "java/lang/Object";
        node.methods.add(method); return bytes(node);
    }
    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }
}
