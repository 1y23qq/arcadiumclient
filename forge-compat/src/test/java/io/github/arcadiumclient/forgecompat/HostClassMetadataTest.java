package io.github.arcadiumclient.forgecompat;

import java.util.UUID;
import java.util.HashMap;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.api.EnvType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.util.Locals;
import org.spongepowered.asm.util.throwables.LVTGeneratorError;

import static org.junit.jupiter.api.Assertions.*;

class HostClassMetadataTest {
    @BeforeAll static void initializeMixinProperties() throws Exception {
        if (FabricLauncherBase.getProperties() == null) {
            var properties = FabricLauncherBase.class.getDeclaredField("properties");
            properties.setAccessible(true); properties.set(null, new HashMap<String, Object>());
        }
        FabricLauncherBase.getProperties().putIfAbsent("mixin.initialised", "0.8.7");
        if (FabricLauncherBase.getLauncher() == null) {
            var launcher = FabricLauncherBase.class.getDeclaredField("launcher");
            launcher.setAccessible(true);
            launcher.set(null, java.lang.reflect.Proxy.newProxyInstance(FabricLauncher.class.getClassLoader(),
                new Class<?>[]{FabricLauncher.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getEnvironmentType" -> EnvType.CLIENT;
                    case "getTargetNamespace" -> "intermediary";
                    case "getTargetClassLoader" -> HostClassMetadataTest.class.getClassLoader();
                    case "isDevelopment" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                }));
        }
    }

    @Test void makesTheAddedLoginSignatureAvailableToTheActualMixinLocalAnalyzer() throws Exception {
        ClassNode node = fixture();
        ClassInfo info = register(node);
        MethodNode added = branch("compatibilityLogin");
        node.methods.add(added);
        assertNull(info.findMethod(added, ClassInfo.INCLUDE_ALL));
        assertThrows(LVTGeneratorError.class, () -> Locals.getLocalsAt(node, added, added.instructions.get(1), 0));
        HostClassMetadata.synchronize(node.name, bytes(node));
        assertSame(info, ClassInfo.fromCache(node.name));
        ClassInfo.Method method = info.findMethod(added, ClassInfo.INCLUDE_ALL);
        assertNotNull(method);
        assertFalse(method.isInjected());
        assertEquals(1, method.getFrames().size());
        var locals = Locals.getLocalsAt(node, added, added.instructions.get(1), 0);
        assertNotNull(locals[0]);
        assertEquals("I", locals[1].desc);
    }

    @Test void updatesExistingFrameMetadataWithoutReplacingCachedMemberIdentity() throws Exception {
        ClassNode node = fixture();
        MethodNode before = new MethodNode(Opcodes.ACC_PUBLIC, "render", "(I)I", null, null);
        before.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        before.instructions.add(new InsnNode(Opcodes.IRETURN));
        before.maxStack = 1; before.maxLocals = 2;
        node.methods.add(before);
        ClassInfo info = register(node);
        ClassInfo.Method cached = info.findMethod(before, ClassInfo.INCLUDE_ALL);
        assertEquals(0, cached.getFrames().size());
        node.methods.set(0, branch("render"));
        HostClassMetadata.synchronize(node.name, bytes(node));
        assertSame(cached, info.findMethod("render", "(I)I", ClassInfo.INCLUDE_ALL));
        assertEquals(1, cached.getFrames().size());
    }

    @Test void leavesClassesWithoutCachedMixinMetadataUnloaded() {
        ClassNode node = fixture();
        assertNull(ClassInfo.fromCache(node.name));
        HostClassMetadata.synchronize(node.name, bytes(node));
        assertNull(ClassInfo.fromCache(node.name));
    }

    @Test void keepsCachedMemberIdentityWhenForgeWidensPrivateAccessAndRemovesFinal() throws Exception {
        ClassNode node = fixture();
        MethodNode method = branch("renderStats");
        method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL;
        node.methods.add(method);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "state", "I", null, null));
        ClassInfo info = register(node);
        ClassInfo.Method cachedMethod = info.findMethod(method, ClassInfo.INCLUDE_ALL);
        ClassInfo.Field cachedField = info.findField("state", "I", ClassInfo.INCLUDE_ALL);
        assertTrue(cachedMethod.isPrivate()); assertTrue(cachedMethod.isFinal());
        method.access = Opcodes.ACC_PROTECTED;
        node.fields.get(0).access = Opcodes.ACC_PUBLIC;
        assertThrows(LVTGeneratorError.class, () -> Locals.getLocalsAt(node, method, method.instructions.get(1), 0));
        HostClassMetadata.synchronize(node.name, bytes(node));
        assertSame(cachedMethod, info.findMethod(method, method.access | ClassInfo.INCLUDE_INITIALISERS));
        assertSame(cachedField, info.findField("state", "I", Opcodes.ACC_PUBLIC));
        assertEquals(Opcodes.ACC_PROTECTED, cachedMethod.getAccess());
        assertFalse(cachedMethod.isPrivate()); assertFalse(cachedMethod.isFinal());
        assertEquals(Opcodes.ACC_PUBLIC, cachedField.getAccess());
        assertFalse(cachedField.isPrivate()); assertFalse(cachedField.isFinal());
        assertEquals("I", Locals.getLocalsAt(node, method, method.instructions.get(1), 0)[1].desc);
    }

    private static ClassNode fixture() {
        ClassNode node = new ClassNode(); node.version = Opcodes.V1_8; node.access = Opcodes.ACC_PUBLIC;
        node.name = "fixture/ArcadiumMetadata" + UUID.randomUUID().toString().replace("-", "");
        node.superName = "java/lang/Object"; return node;
    }

    private static ClassInfo register(ClassNode node) throws Exception {
        var factory = ClassInfo.class.getDeclaredMethod("fromClassNode", ClassNode.class);
        factory.setAccessible(true); return (ClassInfo) factory.invoke(null, node);
    }

    private static MethodNode branch(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "(I)I", null, null);
        LabelNode start = new LabelNode(), zero = new LabelNode(), end = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(zero);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(end);
        method.localVariables.add(new LocalVariableNode("id", "I", null, start, end, 1));
        method.maxStack = 1; method.maxLocals = 2; return method;
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter output = new ClassWriter(0); node.accept(output); return output.toByteArray();
    }
}
