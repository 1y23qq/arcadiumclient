package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostBiomeCompatibility {
    private static final String BIOME = "net/minecraft/world/biome/BiomeGenBase";

    private HostBiomeCompatibility() { }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(mappings.classFromSrg(BIOME))) return;
        MethodNode single = constructor(node, "(I)V");
        if (single == null || objectConstructorCalls(single) == 1) return;
        MethodNode register = constructor(node, "(IZ)V");
        List<AbstractInsnNode> delegation = instructions(single);
        if (!node.superName.equals("java/lang/Object") || register == null || delegation.size() != 5
            || !(delegation.get(0) instanceof VarInsnNode receiver) || receiver.getOpcode() != Opcodes.ALOAD || receiver.var != 0
            || !(delegation.get(1) instanceof VarInsnNode id) || id.getOpcode() != Opcodes.ILOAD || id.var != 1
            || delegation.get(2).getOpcode() != Opcodes.ICONST_1
            || !(delegation.get(3) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
            || !call.owner.equals(node.name) || !call.name.equals("<init>") || !call.desc.equals("(IZ)V") || call.itf
            || delegation.get(4).getOpcode() != Opcodes.RETURN || objectConstructorCalls(register) != 1) {
            throw unsupported(node);
        }
        String idField = mappings.fieldFromSrg(BIOME, "field_76756_M", "I");
        String arrayDescriptor = "[L" + node.name + ";";
        String arrayField = mappings.fieldFromSrg(BIOME, "field_76773_a", "[L" + BIOME + ";");
        int flagLoads = 0, idWrites = 0, arrays = 0, stores = 0;
        for (AbstractInsnNode instruction : register.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var >= 2) {
                if (variable.var != 2 || variable.getOpcode() != Opcodes.ILOAD) throw unsupported(node);
                flagLoads++;
            }
            if (instruction instanceof FieldInsnNode field && field.owner.equals(node.name)) {
                if (field.getOpcode() == Opcodes.PUTFIELD && field.name.equals(idField) && field.desc.equals("I")) idWrites++;
                if (field.getOpcode() == Opcodes.GETSTATIC && field.name.equals(arrayField) && field.desc.equals(arrayDescriptor)) arrays++;
            }
            if (instruction.getOpcode() == Opcodes.AASTORE) stores++;
        }
        if (flagLoads != 1 || idWrites != 1 || arrays != 1 || stores != 1) throw unsupported(node);

        ClassWriter serialized = new ClassWriter(0);
        node.accept(serialized);
        ClassNode expanded = new ClassNode();
        new ClassReader(serialized.toByteArray()).accept(expanded, ClassReader.EXPAND_FRAMES);
        MethodNode body = constructor(expanded, "(IZ)V");
        InsnList flag = new InsnList();
        flag.add(new InsnNode(Opcodes.ICONST_1));
        flag.add(new VarInsnNode(Opcodes.ISTORE, 2));
        body.instructions.insert(flag);
        // Slot 2 remains initialized for the copied, expanded constructor frames.
        single.instructions = body.instructions;
        single.tryCatchBlocks = body.tryCatchBlocks;
        single.localVariables = body.localVariables;
        single.visibleLocalVariableAnnotations = body.visibleLocalVariableAnnotations;
        single.invisibleLocalVariableAnnotations = body.invisibleLocalVariableAnnotations;
        single.maxStack = Math.max(1, body.maxStack);
        single.maxLocals = Math.max(3, body.maxLocals);
    }

    private static MethodNode constructor(ClassNode node, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals("<init>") && method.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static List<AbstractInsnNode> instructions(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
    }

    private static long objectConstructorCalls(MethodNode method) {
        return instructions(method).stream().filter(instruction -> instruction instanceof MethodInsnNode call
            && call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals("java/lang/Object")
            && call.name.equals("<init>") && call.desc.equals("()V")).count();
    }

    private static IllegalStateException unsupported(ClassNode node) {
        return new IllegalStateException("Unsupported Forge biome registration constructor in " + node.name);
    }
}
