package io.github.arcadiumclient.forgecompat.transform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeFieldAccess;

import static io.github.arcadiumclient.forgecompat.transform.TransformerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class GuestMixinPriorityTest {
    @TempDir Path directory;

    @Test void keepsNativeFieldCallbacksEligibleAfterTheGuestOverwriteWithoutChangingItsBody() throws Exception {
        ClassNode original = guest(true, null, GAME);
        byte[] source = bytes(original);
        Path input = jar(directory.resolve("guest.jar"), Map.of(MIXIN + ".class", source,
            "mixins.json", text(config(""))));
        Path converted = new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null);
        byte[] changed = entries(converted).get(MIXIN + ".class");
        ClassNode mapped = node(changed);
        assertEquals(999, priority(mapped));
        assertArrayEquals(source, entries(input).get(MIXIN + ".class"));
        Class<?> owner = new Definer().define(changed);
        assertEquals(7, owner.getMethod("work").invoke(owner.getConstructor().newInstance()));
        assertEquals(opcodes(original.methods.get(1)), opcodes(mapped.methods.get(1)));
        assertEquals(original.methods.get(1).visibleAnnotations.get(0).desc, mapped.methods.get(1).visibleAnnotations.get(0).desc);
        assertEquals(1, verify(converted));
        InjectionPoint fieldRule = new InjectionPoint() {
            @Override public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) { return false; }
        };
        assertEquals(InjectionPoint.class, BeforeFieldAccess.class.getMethod("checkPriority", int.class, int.class).getDeclaringClass());
        assertFalse(fieldRule.checkPriority(1000, 1000));
        assertTrue(fieldRule.checkPriority((int) priority(mapped), 1000));
    }

    @Test void honorsExplicitClassPrioritiesAndInheritedOrExplicitConfigurationPolicies() throws Exception {
        for (int priority : new int[]{999, 1000, 2000}) {
            assertEquals(priority, priority(convert(guest(true, priority, GAME), config(""))));
        }
        for (String options : List.of(",\"mixinPriority\":1000", ",\"mixinPriority\":2000", ",\"parent\":\"base.json\"")) {
            assertNull(priority(convert(guest(true, null, GAME), config(options))));
        }
        assertEquals(999, priority(convert(guest(true, null, GAME), config(",\"priority\":2000"))));
    }

    @Test void leavesNonOverwritesUnlistedMixinsAndNonMinecraftTargetsUntouched() throws Exception {
        assertNull(priority(convert(guest(false, null, GAME), config(""))));
        assertNull(priority(convert(guest(true, null, "example/Library"), config(""))));
        assertNull(priority(convert(guest(true, null, GAME), config("").replace("GameMixin", "OtherMixin"))));
    }

    @Test void preservesExplicitPolicyWhenOneMixinIsDeclaredInSeveralConfigurations() throws Exception {
        ClassNode guest = guest(true, null, GAME);
        Path input = jar(directory.resolve("several.jar"), Map.of(MIXIN + ".class", bytes(guest),
            "first.json", text(config("")), "second.json", text(config(",\"mixinPriority\":2000"))));
        Path result = new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null);
        assertNull(priority(node(entries(result).get(MIXIN + ".class"))));
    }

    private ClassNode convert(ClassNode guest, String configuration) throws Exception {
        Path input = jar(Files.createTempFile(directory, "guest-", ".jar"), Map.of(MIXIN + ".class", bytes(guest), "mixins.json", text(configuration)));
        Path result = new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null);
        return node(entries(result).get(MIXIN + ".class"));
    }

    private static ClassNode guest(boolean overwrite, Integer priority, String target) {
        ClassNode node = simple(MIXIN);
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        annotation.values = new ArrayList<>(List.of("value", List.of(Type.getObjectType(target))));
        if (priority != null) { annotation.values.add("priority"); annotation.values.add(priority); }
        node.invisibleAnnotations = new ArrayList<>(List.of(annotation));
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "work", "()I", null, null);
        method.visitIntInsn(Opcodes.BIPUSH, 7); method.visitInsn(Opcodes.IRETURN);
        if (overwrite) method.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Overwrite;")));
        node.methods.add(method);
        return node;
    }

    private static Object priority(ClassNode node) {
        for (AnnotationNode annotation : node.invisibleAnnotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || annotation.values == null) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals("priority")) return annotation.values.get(i + 1);
        }
        return null;
    }
    private static String config(String options) { return "{\"package\":\"example\",\"mixins\":[\"GameMixin\"]" + options + "}"; }
    private static List<Integer> opcodes(MethodNode method) { return java.util.Arrays.stream(method.instructions.toArray()).map(AbstractInsnNode::getOpcode).toList(); }
    private static class Definer extends ClassLoader {
        Definer() { super(ClassLoader.getPlatformClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
}
