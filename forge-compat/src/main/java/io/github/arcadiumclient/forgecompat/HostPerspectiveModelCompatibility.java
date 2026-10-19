package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class HostPerspectiveModelCompatibility {
    static final String TARGET = "net/minecraftforge/client/model/ISmartBlockModel$PerspectiveWrapper";
    static final String BASE = "net/minecraftforge/client/model/ISmartBlockModel$Wrapper";
    static final String FLEXIBLE = "net/minecraftforge/client/model/IFlexibleBakedModel";
    static final String CACHED = "arcadium$perspectiveModels";
    static final String LOOKUP = "arcadium$perspectiveModel";
    private static final String ANONYMOUS = TARGET + "$1";
    private static final Type MAP = Type.getType(java.util.IdentityHashMap.class);
    private static final Type OBJECT = Type.getType(Object.class);
    private static final String DESCRIPTOR = "(L" + FLEXIBLE + ";)L" + BASE + ";";

    private HostPerspectiveModelCompatibility() { }

    static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(TARGET)) return;
        String descriptor = mappings.descriptorFromSrg("(Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)Lorg/apache/commons/lang3/tuple/Pair;");
        MethodNode perspective = node.methods.stream().filter(method -> method.name.equals("handlePerspective") && method.desc.equals(descriptor))
            .findFirst().orElseThrow(() -> unsupported("perspective method"));
        List<MethodNode> existing = node.methods.stream().filter(method -> method.name.equals(LOOKUP)).toList();
        if (!existing.isEmpty()) {
            if (existing.size() == 1 && existing.get(0).desc.equals(DESCRIPTOR)
                    && node.fields.stream().filter(field -> field.name.equals(CACHED) && field.desc.equals(MAP.getDescriptor())).count() == 1
                    && calls(perspective, node.name, LOOKUP).size() == 1) return;
            throw unsupported("existing model cache");
        }
        if (node.fields.stream().anyMatch(field -> field.name.equals(CACHED))) throw unsupported("cache field collision");
        var constructors = calls(perspective, ANONYMOUS, "<init>");
        if (constructors.size() != 1) throw unsupported("delegate constructor");
        MethodInsnNode constructor = constructors.get(0);
        AbstractInsnNode cast = previous(constructor), left = previous(cast), result = previous(left);
        AbstractInsnNode self = previous(result), duplicate = previous(self), allocation = previous(duplicate);
        if (constructor.getOpcode() != Opcodes.INVOKESPECIAL || constructor.itf
                || !constructor.desc.equals("(L" + TARGET + ";L" + FLEXIBLE + ";)V")
                || !(allocation instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW || !type.desc.equals(ANONYMOUS)
                || duplicate.getOpcode() != Opcodes.DUP || !load(self, 0) || !load(result, 2)
                || !(left instanceof MethodInsnNode getter) || getter.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !getter.owner.equals("org/apache/commons/lang3/tuple/Pair") || !getter.name.equals("getLeft")
                || !getter.desc.equals("()Ljava/lang/Object;") || !(cast instanceof TypeInsnNode flexible)
                || flexible.getOpcode() != Opcodes.CHECKCAST || !flexible.desc.equals(FLEXIBLE)
                || perspective.maxLocals != 3) throw unsupported("delegate operands");
        MethodNode lookup = lookup(node.version);
        perspective.instructions.remove(allocation);
        perspective.instructions.remove(duplicate);
        perspective.instructions.set(constructor, new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, LOOKUP, DESCRIPTOR, false));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, CACHED, MAP.getDescriptor(), null, null));
        node.methods.add(lookup);
    }

    private static MethodNode lookup(int version) {
        Type owner = Type.getObjectType(TARGET), flexible = Type.getObjectType(FLEXIBLE), wrapper = Type.getObjectType(BASE);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(version, Opcodes.ACC_PUBLIC, TARGET, null, BASE, null);
        GeneratorAdapter method = new GeneratorAdapter(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
            new Method(LOOKUP, DESCRIPTOR), null, null, writer);
        method.visitCode();
        Label ready = method.newLabel(), found = method.newLabel();
        method.loadThis(); method.getField(owner, CACHED, MAP); method.ifNonNull(ready);
        method.loadThis(); method.newInstance(MAP); method.dup(); method.invokeConstructor(MAP, new Method("<init>", "()V"));
        method.putField(owner, CACHED, MAP);
        method.mark(ready);
        int result = method.newLocal(wrapper);
        method.loadThis(); method.getField(owner, CACHED, MAP); method.loadArg(0);
        method.invokeVirtual(MAP, new Method("get", OBJECT, new Type[] {OBJECT})); method.checkCast(wrapper); method.storeLocal(result);
        method.loadLocal(result); method.ifNonNull(found);
        Type anonymous = Type.getObjectType(ANONYMOUS);
        method.newInstance(anonymous); method.dup(); method.loadThis(); method.loadArg(0);
        method.invokeConstructor(anonymous, new Method("<init>", Type.VOID_TYPE, new Type[] {owner, flexible}));
        method.checkCast(wrapper);
        method.storeLocal(result);
        method.loadThis(); method.getField(owner, CACHED, MAP); method.loadArg(0); method.loadLocal(result);
        method.invokeVirtual(MAP, new Method("put", OBJECT, new Type[] {OBJECT, OBJECT})); method.pop();
        method.mark(found); method.loadLocal(result); method.returnValue(); method.endMethod(); writer.visitEnd();
        ClassNode generated = new ClassNode(); new ClassReader(writer.toByteArray()).accept(generated, 0);
        return generated.methods.get(0);
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.owner.equals(owner) && call.name.equals(name)).map(MethodInsnNode.class::cast).toList();
    }
    private static boolean load(AbstractInsnNode instruction, int slot) { return instruction instanceof VarInsnNode value && value.getOpcode() == Opcodes.ALOAD && value.var == slot; }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static IllegalStateException unsupported(String shape) { return new IllegalStateException("Unsupported Forge perspective wrapper " + shape); }
}
