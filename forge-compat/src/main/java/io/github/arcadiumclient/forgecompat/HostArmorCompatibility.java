package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostArmorCompatibility {
    private static final String LAYER = "net/minecraft/client/renderer/entity/layers/LayerArmorBase";
    private static final String ARMOR = "net/minecraft/item/ItemArmor";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String STACK = "net/minecraft/item/ItemStack";
    private static final String RESOURCE = "net/minecraft/util/ResourceLocation";
    private static final String HOOKS = "net/minecraftforge/client/ForgeHooksClient";
    private static final String FORMAT = "%s:textures/models/armor/%s_layer_%d%s.png";

    private HostArmorCompatibility() { }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(mappings.classFromSrg(LAYER))) return;
        String descriptor = mappings.descriptorFromSrg("(L" + ENTITY + ";L" + STACK + ";ILjava/lang/String;)L" + RESOURCE + ";");
        MethodNode source = method(node, "getArmorResource", descriptor);
        if (source == null) return;
        String legacyDescriptor = mappings.descriptorFromSrg("(L" + ARMOR + ";ZLjava/lang/String;)L" + RESOURCE + ";");
        String legacy = mappings.methodFromSrg(LAYER, "func_177178_a", "(L" + ARMOR + ";ZLjava/lang/String;)L" + RESOURCE + ";");
        String leggings = mappings.methodFromSrg(LAYER, "func_177180_b", "(I)Z");
        String resource = mappings.classFromSrg(RESOURCE);
        List<MethodInsnNode> existing = calls(source, node.name, legacy, legacyDescriptor);
        if (!existing.isEmpty()) {
            if (existing.size() == 1 && normalized(existing.get(0), node.name, leggings, resource)) return;
            throw unsupported("existing texture delegation");
        }
        String hookDescriptor = mappings.descriptorFromSrg("(L" + ENTITY + ";L" + STACK + ";Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;");
        List<MethodInsnNode> hooks = calls(source, HOOKS, "getArmorTexture", hookDescriptor);
        List<LdcInsnNode> formats = Arrays.stream(source.instructions.toArray()).filter(instruction -> instruction instanceof LdcInsnNode constant
            && FORMAT.equals(constant.cst)).map(LdcInsnNode.class::cast).toList();
        MethodNode old = method(node, legacy, legacyDescriptor), slot = method(node, leggings, "(I)Z");
        if (hooks.size() != 1 || formats.size() != 1 || old == null || slot == null || source.maxLocals != 11
                || !instance(source) || !instance(old) || !instance(slot) || !colonIndex(source)) {
            throw unsupported("texture layout");
        }
        MethodNode restored = expanded(node, source);
        LdcInsnNode format = Arrays.stream(restored.instructions.toArray()).filter(instruction -> instruction instanceof LdcInsnNode constant
            && FORMAT.equals(constant.cst)).map(LdcInsnNode.class::cast).findFirst().orElseThrow();
        AbstractInsnNode stored = format;
        while (stored != null && !variable(stored, Opcodes.ASTORE, 9)) stored = stored.getNext();
        if (stored == null || !(previous(stored) instanceof MethodInsnNode formatter) || formatter.getOpcode() != Opcodes.INVOKESTATIC
                || !formatter.owner.equals("java/lang/String") || !formatter.name.equals("format")
                || !formatter.desc.equals("(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")) {
            throw unsupported("default texture formatter");
        }
        AbstractInsnNode argument = next(stored);
        for (int index : new int[]{1, 2, 9, 3, 4}) {
            if (!variable(argument, index == 3 ? Opcodes.ILOAD : Opcodes.ALOAD, index)) throw unsupported("armor hook arguments");
            argument = next(argument);
        }
        if (!(argument instanceof MethodInsnNode hook) || hook.getOpcode() != Opcodes.INVOKESTATIC || hook.itf
                || !hook.owner.equals(HOOKS) || !hook.name.equals("getArmorTexture") || !hook.desc.equals(hookDescriptor)) {
            throw unsupported("armor hook");
        }
        LabelNode ordinary = new LabelNode(), custom = new LabelNode(), ready = new LabelNode();
        Object[] locals = {node.name, mappings.classFromSrg(ENTITY), mappings.classFromSrg(STACK), Opcodes.INTEGER,
            "java/lang/String", mappings.classFromSrg(ARMOR), "java/lang/String", "java/lang/String", Opcodes.INTEGER};
        InsnList branch = new InsnList();
        branch.add(new VarInsnNode(Opcodes.ILOAD, 8)); branch.add(new InsnNode(Opcodes.ICONST_M1));
        branch.add(new JumpInsnNode(Opcodes.IF_ICMPNE, custom));
        branch.add(new VarInsnNode(Opcodes.ALOAD, 4)); branch.add(new JumpInsnNode(Opcodes.IFNULL, ordinary));
        branch.add(new LdcInsnNode("overlay")); branch.add(new VarInsnNode(Opcodes.ALOAD, 4));
        branch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false));
        branch.add(new JumpInsnNode(Opcodes.IFEQ, custom));
        branch.add(ordinary); branch.add(new FrameNode(Opcodes.F_NEW, locals.length, locals, 0, null));
        branch.add(new VarInsnNode(Opcodes.ALOAD, 0)); branch.add(new VarInsnNode(Opcodes.ALOAD, 5));
        branch.add(new VarInsnNode(Opcodes.ALOAD, 0)); branch.add(new VarInsnNode(Opcodes.ILOAD, 3));
        branch.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, leggings, "(I)Z", false));
        branch.add(new VarInsnNode(Opcodes.ALOAD, 4));
        branch.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, legacy, legacyDescriptor, false));
        branch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, resource, "toString", "()Ljava/lang/String;", false));
        branch.add(new VarInsnNode(Opcodes.ASTORE, 9)); branch.add(new JumpInsnNode(Opcodes.GOTO, ready));
        branch.add(custom); branch.add(new FrameNode(Opcodes.F_NEW, locals.length, locals, 0, null));
        restored.instructions.insertBefore(format, branch);
        Object[] textureLocals = Arrays.copyOf(locals, 10); textureLocals[9] = "java/lang/String";
        InsnList merge = new InsnList(); merge.add(ready); merge.add(new FrameNode(Opcodes.F_NEW, textureLocals.length, textureLocals, 0, null));
        restored.instructions.insert(stored, merge);
        restored.maxStack = Math.max(restored.maxStack, 5);
        node.methods.set(node.methods.indexOf(source), restored);
    }

    private static boolean colonIndex(MethodNode method) {
        List<MethodInsnNode> indexes = calls(method, "java/lang/String", "indexOf", "(I)I");
        if (indexes.size() != 1) return false;
        MethodInsnNode index = indexes.get(0);
        return previous(index) instanceof IntInsnNode separator && separator.getOpcode() == Opcodes.BIPUSH && separator.operand == ':'
            && variable(previous(separator), Opcodes.ALOAD, 6) && variable(next(index), Opcodes.ISTORE, 8);
    }
    private static boolean normalized(MethodInsnNode call, String owner, String leggings, String resource) {
        AbstractInsnNode suffix = previous(call), slot = previous(suffix), slotArgument = previous(slot), slotReceiver = previous(slotArgument);
        AbstractInsnNode armor = previous(slotReceiver), receiver = previous(armor), string = next(call);
        return call.getOpcode() == Opcodes.INVOKESPECIAL && variable(suffix, Opcodes.ALOAD, 4)
            && slot instanceof MethodInsnNode test && test.getOpcode() == Opcodes.INVOKESPECIAL && test.owner.equals(owner)
            && test.name.equals(leggings) && test.desc.equals("(I)Z") && variable(slotArgument, Opcodes.ILOAD, 3)
            && variable(slotReceiver, Opcodes.ALOAD, 0) && variable(armor, Opcodes.ALOAD, 5) && variable(receiver, Opcodes.ALOAD, 0)
            && string instanceof MethodInsnNode text && text.owner.equals(resource) && text.name.equals("toString") && text.desc.equals("()Ljava/lang/String;")
            && variable(next(string), Opcodes.ASTORE, 9);
    }
    private static MethodNode expanded(ClassNode owner, MethodNode method) {
        ClassWriter writer = new ClassWriter(0); writer.visit(owner.version, owner.access, owner.name, owner.signature, owner.superName, owner.interfaces.toArray(String[]::new));
        method.accept(writer); writer.visitEnd();
        ClassNode node = new ClassNode(); new ClassReader(writer.toByteArray()).accept(node, ClassReader.EXPAND_FRAMES); return node.methods.get(0);
    }
    private static boolean instance(MethodNode method) { return (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0; }
    private static boolean variable(AbstractInsnNode instruction, int opcode, int slot) { return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot; }
    private static MethodNode method(ClassNode node, String name, String descriptor) { return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null); }
    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, String descriptor) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)).map(MethodInsnNode.class::cast).toList(); }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) { if (instruction == null) return null; do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0); return instruction; }
    private static AbstractInsnNode next(AbstractInsnNode instruction) { if (instruction == null) return null; do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0); return instruction; }
    private static IllegalStateException unsupported(String shape) { return new IllegalStateException("Unsupported Forge armor " + shape); }
}
