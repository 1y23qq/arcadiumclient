// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class GlCallbackFusion implements Opcodes {
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CAPTURE = "dev/rdh/argentum/impl/render/entity/instancing/EntityCapture";
    private static final String CONTROL = Type.getInternalName(RenderHookAllocationControl.class);
    private static final String ENTITY_MIXIN = "dev.rdh.argentum.mixin.features.model.instancing.GlStateManagerMixin";
    private static final Map<String,String> DELEGATES = Map.of("pushEntityMatrix", "pushMatrix", "popEntityMatrix", "popMatrix",
            "translateEntity", "translate", "scaleEntity", "scale", "rotateEntity", "rotate");
    public record Site(String method, String descriptor, String handler, String predicate) { }
    private record Gate(MethodInsnNode current, MethodInsnNode delegate, List<AbstractInsnNode> arguments) { }
    private record Patch(MethodNode method, AbstractInsnNode start, LabelNode continuation, int callbackSlot, MethodNode predicate, String handler) { }

    private GlCallbackFusion() { }

    public static List<Site> apply(ClassNode target) {
        List<Patch> patches = new ArrayList<>();
        for (MethodNode entry : List.copyOf(target.methods)) {
            if ((entry.access & ACC_STATIC) == 0 || Type.getReturnType(entry.desc) != Type.VOID_TYPE || !entry.tryCatchBlocks.isEmpty()) continue;
            List<AbstractInsnNode> code = code(entry);
            for (int i = 0; i < code.size(); i++) {
                if (!(code.get(i) instanceof MethodInsnNode call) || call.getOpcode() != INVOKESTATIC || !call.owner.equals(target.name)) continue;
                MethodNode handler = target.methods.stream().filter(method -> method.name.equals(call.name) && method.desc.equals(call.desc)).findFirst().orElse(null);
                Gate gate = handler == null ? null : gate(handler, entry.desc);
                if (gate == null) continue;
                Patch patch = patch(target, entry, handler, gate, code, i, patches.size());
                if (patch != null) { patches.add(patch); break; }
            }
        }
        List<Site> sites = new ArrayList<>();
        for (Patch patch : patches) {
            InsnList fast = new InsnList();
            LabelNode slow = new LabelNode(), resume = new LabelNode();
            fast.add(new FieldInsnNode(GETSTATIC, CONTROL, "enabled", "Z"));
            fast.add(new JumpInsnNode(IFEQ, slow));
            arguments(fast, Type.getArgumentTypes(patch.method.desc));
            fast.add(new MethodInsnNode(INVOKESTATIC, target.name, patch.predicate.name, patch.predicate.desc, false));
            fast.add(new JumpInsnNode(IFEQ, resume));
            fast.add(new InsnNode(RETURN));
            fast.add(resume);
            fast.add(frame(patch.method.desc));
            fast.add(new InsnNode(ACONST_NULL));
            fast.add(new VarInsnNode(ASTORE, patch.callbackSlot));
            fast.add(new JumpInsnNode(GOTO, patch.continuation));
            fast.add(slow);
            fast.add(frame(patch.method.desc));
            patch.method.instructions.insertBefore(patch.start, fast);
            patch.method.maxStack = Math.max(patch.method.maxStack, slots(Type.getArgumentTypes(patch.method.desc)) + 2);
            target.methods.add(patch.predicate);
            sites.add(new Site(patch.method.name, patch.method.desc, patch.handler, patch.predicate.name));
        }
        return List.copyOf(sites);
    }

    private static Patch patch(ClassNode owner, MethodNode entry, MethodNode handler, Gate gate, List<AbstractInsnNode> code, int at, int index) {
        Type[] args = Type.getArgumentTypes(entry.desc);
        int slot = slots(args), start = at - args.length - 7;
        if (start < 0 || at + 4 >= code.size()) return null;
        if (!type(code.get(start), NEW, CALLBACK) || code.get(start + 1).getOpcode() != DUP || !(code.get(start + 2) instanceof LdcInsnNode id)
                || !(id.cst instanceof String) || code.get(start + 3).getOpcode() != ICONST_1
                || !method(code.get(start + 4), INVOKESPECIAL, CALLBACK, "<init>", "(Ljava/lang/String;Z)V")
                || !variable(code.get(start + 5), ASTORE, slot)) return null;
        int load = start + 6, local = 0;
        for (Type arg : args) {
            if (!variable(code.get(load++), arg.getOpcode(ILOAD), local)) return null;
            local += arg.getSize();
        }
        if (!variable(code.get(load), ALOAD, slot) || load + 1 != at || !variable(code.get(at + 1), ALOAD, slot)
                || !method(code.get(at + 2), INVOKEVIRTUAL, CALLBACK, "isCancelled", "()Z")
                || !(code.get(at + 3) instanceof JumpInsnNode jump) || jump.getOpcode() != IFEQ || code.get(at + 4).getOpcode() != RETURN) return null;
        if (nextReal(code.get(at + 4)) != nextReal(jump.label)) return null;
        if (!emptyPrefix(code, start)) return null;
        for (int i = 0; i < start; i++) {
            AbstractInsnNode instruction = code.get(i);
            if (instruction instanceof JumpInsnNode || instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode
                    || instruction instanceof IincInsnNode || instruction.getOpcode() == ATHROW
                    || instruction instanceof VarInsnNode variable && variable.getOpcode() >= ISTORE && variable.getOpcode() <= ASTORE) return null;
        }
        for (AbstractInsnNode instruction : code) {
            if (instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode) return null;
            if (instruction instanceof VarInsnNode variable && variable.var == slot
                    && instruction != code.get(start + 5) && instruction != code.get(load) && instruction != code.get(at + 1)) return null;
            if (instruction instanceof JumpInsnNode branch && branch != jump && in(entry, branch.label, code.get(start), code.get(at + 4))) return null;
        }
        String name = "arcadium$typedRenderGate$" + index;
        if (owner.methods.stream().anyMatch(method -> method.name.equals(name))) return null;
        MethodNode predicate = new MethodNode(ASM9, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, name, Type.getMethodDescriptor(Type.BOOLEAN_TYPE, args), null, null);
        predicate.instructions.add(copy(gate.current));
        predicate.instructions.add(new InsnNode(DUP));
        LabelNode present = new LabelNode();
        predicate.instructions.add(new JumpInsnNode(IFNONNULL, present));
        predicate.instructions.add(new InsnNode(POP));
        predicate.instructions.add(new InsnNode(ICONST_0));
        predicate.instructions.add(new InsnNode(IRETURN));
        predicate.instructions.add(present);
        predicate.instructions.add(new FrameNode(F_SAME1, 0, null, 1, new Object[]{CAPTURE}));
        for (AbstractInsnNode argument : gate.arguments) predicate.instructions.add(argument.clone(null));
        predicate.instructions.add(copy(gate.delegate));
        predicate.instructions.add(new InsnNode(IRETURN));
        predicate.maxStack = slot + 2;
        predicate.maxLocals = slot;
        return new Patch(entry, code.get(start), jump.label, slot, predicate, handler.name);
    }

    private static Gate gate(MethodNode handler, String entryDescriptor) {
        if ((handler.access & ACC_STATIC) == 0 || !handler.tryCatchBlocks.isEmpty() || !merged(handler)) return null;
        String delegateName = DELEGATES.entrySet().stream().filter(entry -> handler.name.endsWith("celeritas$" + entry.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
        if (delegateName == null) return null;
        Type[] args = Type.getArgumentTypes(entryDescriptor), actual = Type.getArgumentTypes(handler.desc);
        if (actual.length != args.length + 1 || !Arrays.equals(Arrays.copyOf(actual, args.length), args)
                || !actual[args.length].equals(Type.getObjectType(CALLBACK)) || Type.getReturnType(handler.desc) != Type.VOID_TYPE) return null;
        for (Type arg : args) if (arg != Type.FLOAT_TYPE && arg != Type.DOUBLE_TYPE) return null;
        List<AbstractInsnNode> code = code(handler);
        if (code.size() < 10 || !method(code.get(0), INVOKESTATIC, CAPTURE, "current", "()L" + CAPTURE + ";")
                || !(code.get(1) instanceof VarInsnNode stored) || stored.getOpcode() != ASTORE
                || !variable(code.get(2), ALOAD, stored.var) || !(code.get(3) instanceof JumpInsnNode absent) || absent.getOpcode() != IFNULL
                || !variable(code.get(4), ALOAD, stored.var)) return null;
        int at = 5, local = 0;
        List<AbstractInsnNode> converted = new ArrayList<>();
        for (Type arg : args) {
            if (at >= code.size() || !variable(code.get(at), arg.getOpcode(ILOAD), local)) return null;
            converted.add(code.get(at++)); local += arg.getSize();
            if (arg == Type.DOUBLE_TYPE) {
                if (at >= code.size() || code.get(at).getOpcode() != D2F) return null;
                converted.add(code.get(at++));
            }
        }
        Type[] floats = new Type[args.length]; Arrays.fill(floats, Type.FLOAT_TYPE);
        if (at + 5 != code.size() || !method(code.get(at), INVOKEVIRTUAL, CAPTURE, delegateName, Type.getMethodDescriptor(Type.BOOLEAN_TYPE, floats))
                || !(code.get(at + 1) instanceof JumpInsnNode declined) || declined.getOpcode() != IFEQ
                || !variable(code.get(at + 2), ALOAD, slots(args)) || !method(code.get(at + 3), INVOKEVIRTUAL, CALLBACK, "cancel", "()V")
                || code.get(at + 4).getOpcode() != RETURN || nextReal(absent.label) != code.get(at + 4) || nextReal(declined.label) != code.get(at + 4)) return null;
        if (stored.var <= slots(args)) return null;
        return new Gate((MethodInsnNode) code.get(0), (MethodInsnNode) code.get(at), converted);
    }

    private static boolean merged(MethodNode method) {
        for (List<AnnotationNode> annotations : List.of(method.visibleAnnotations == null ? List.<AnnotationNode>of() : method.visibleAnnotations,
                method.invisibleAnnotations == null ? List.<AnnotationNode>of() : method.invisibleAnnotations)) {
            for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
                for (int i = 0; i + 1 < annotation.values.size(); i += 2) if (annotation.values.get(i).equals("mixin") && ENTITY_MIXIN.equals(annotation.values.get(i + 1))) return true;
        }
        return false;
    }

    private static FrameNode frame(String descriptor) {
        Type[] args = Type.getArgumentTypes(descriptor);
        Object[] locals = new Object[args.length];
        for (int i = 0; i < args.length; i++) locals[i] = args[i] == Type.DOUBLE_TYPE ? DOUBLE : FLOAT;
        return new FrameNode(F_FULL, locals.length, locals, 0, new Object[0]);
    }
    private static boolean emptyPrefix(List<AbstractInsnNode> code, int end) {
        int stack = 0;
        for (int i = 0; i < end; i++) {
            AbstractInsnNode instruction = code.get(i);
            int opcode = instruction.getOpcode();
            if (instruction instanceof VarInsnNode variable && opcode >= ILOAD && opcode <= ALOAD) stack += opcode == DLOAD || opcode == LLOAD ? 2 : 1;
            else if (instruction instanceof LdcInsnNode constant) stack += constant.cst instanceof Double || constant.cst instanceof Long ? 2 : 1;
            else if (opcode == ACONST_NULL || opcode >= ICONST_M1 && opcode <= ICONST_5 || opcode >= FCONST_0 && opcode <= FCONST_2) stack++;
            else if (opcode == DCONST_0 || opcode == DCONST_1 || opcode == LCONST_0 || opcode == LCONST_1) stack += 2;
            else if (instruction instanceof MethodInsnNode call && opcode == INVOKESTATIC && Type.getReturnType(call.desc) == Type.VOID_TYPE) {
                if (stack != slots(Type.getArgumentTypes(call.desc))) return false;
                stack = 0;
            } else if (opcode != NOP) return false;
        }
        return stack == 0;
    }
    private static void arguments(InsnList output, Type[] args) { int slot = 0; for (Type arg : args) { output.add(new VarInsnNode(arg.getOpcode(ILOAD), slot)); slot += arg.getSize(); } }
    private static int slots(Type[] args) { int total = 0; for (Type arg : args) total += arg.getSize(); return total; }
    private static MethodInsnNode copy(MethodInsnNode call) { return new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf); }
    private static boolean variable(AbstractInsnNode node, int opcode, int slot) { return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot; }
    private static boolean type(AbstractInsnNode node, int opcode, String type) { return node instanceof TypeInsnNode value && value.getOpcode() == opcode && value.desc.equals(type); }
    private static boolean method(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) { return node instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor); }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> code = new ArrayList<>(); for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) code.add(instruction); return code; }
    private static AbstractInsnNode nextReal(AbstractInsnNode node) { do { node = node.getNext(); } while (node != null && node.getOpcode() < 0); return node; }
    private static boolean in(MethodNode method, LabelNode label, AbstractInsnNode first, AbstractInsnNode last) { int at = method.instructions.indexOf(label); return at > method.instructions.indexOf(first) && at <= method.instructions.indexOf(last); }
}
