// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.*;

public final class LayerOperationFusion implements Opcodes {
    private static final String OP = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String MIXIN = "dev.rdh.argentum.mixin.features.model.instancing.LivingEntityRendererMixin";
    private static final String CONTROL = Type.getInternalName(RenderHookAllocationControl.class);
    private static final String TYPED = "arcadium$typedLayerCapture";
    private static final String WRAPPER_HASH = "0159f7fc65efef03db3422cc36dbe5a4c04d365730b84f4d8eab82cfc7bbb3df";
    private static final String BRIDGE_HASH = "37d2c88ad912dd6921a116738c3eedfcdeb63a84204de79e8acfe6d7b3caeb1b";
    public record Site(String method, String descriptor) { }
    private record Context(String owner, Type[] arguments, MethodNode wrapper, String typedDescriptor) { }
    private record Pack(AbstractInsnNode first, AbstractInsnNode last) { }
    private record Frame(Object[] locals, Object[] stack) { }
    private record Patch(MethodNode method, InvokeDynamicInsnNode first, MethodInsnNode last, MethodInsnNode render, Frame frame) { }

    private LayerOperationFusion() { }

    public static List<Site> apply(ClassNode target) {
        if (target.methods.stream().anyMatch(method -> method.name.equals(TYPED))) return List.of();
        ClassNode work = expanded(target); Context context = context(work);
        if (context == null || !fingerprint(context.wrapper, context).equals(WRAPPER_HASH)) return List.of();
        List<Patch> patches = new ArrayList<>();
        for (MethodNode method : work.methods) {
            List<AbstractInsnNode> code = code(method);
            for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof InvokeDynamicInsnNode dynamic && dynamic.desc.equals("()L" + OP + ";")) {
                Patch patch = site(work, context, method, code, i);
                if (patch != null) patches.add(patch);
            }
        }
        if (patches.size() != 1) return List.of();
        Patch patch = patches.getFirst(); MethodNode typed = typed(context, patch.render);
        if (typed == null) return List.of();
        LabelNode slow = new LabelNode(), done = new LabelNode(); InsnList fast = new InsnList();
        fast.add(new FieldInsnNode(GETSTATIC, CONTROL, "enabled", "Z")); fast.add(new JumpInsnNode(IFEQ, slow));
        fast.add(new MethodInsnNode(INVOKESPECIAL, work.name, TYPED, context.typedDescriptor, false)); fast.add(new JumpInsnNode(GOTO, done));
        fast.add(slow); fast.add(frame(patch.frame.locals, patch.frame.stack)); patch.method.instructions.insertBefore(patch.first, fast);
        InsnList finish = new InsnList(); finish.add(done); finish.add(frame(patch.frame.locals, new Object[0]));
        patch.method.instructions.insert(patch.last, finish); patch.method.maxStack = Math.max(11, patch.method.maxStack);
        work.methods.add(typed); target.methods = work.methods;
        return List.of(new Site(patch.method.name, patch.method.desc));
    }

    public static Map<String, String> audit(ClassNode target) {
        ClassNode work = expanded(target); Context context = context(work); if (context == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>(); result.put("wrapper", fingerprint(context.wrapper, context));
        for (MethodNode method : work.methods) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof InvokeDynamicInsnNode dynamic) {
            MethodNode bridge = implementation(work, dynamic);
            if (bridge != null) result.put("bridge:" + bridge.name, fingerprint(bridge, context));
        }
        return result;
    }

    private static Context context(ClassNode owner) {
        List<MethodNode> wrappers = owner.methods.stream().filter(method -> method.name.endsWith("$celeritas$captureLayer") && merged(method)).toList();
        if (wrappers.size() != 1) return null; MethodNode wrapper = wrappers.getFirst(); Type[] args = Type.getArgumentTypes(wrapper.desc);
        if (wrapper.access != ACC_PRIVATE || Type.getReturnType(wrapper.desc) != Type.VOID_TYPE || args.length != 10 || !args[9].equals(Type.getObjectType(OP))) return null;
        for (int i = 0; i < 9; i++) if (i < 2 ? args[i].getSort() != Type.OBJECT : args[i] != Type.FLOAT_TYPE) return null;
        Context context = new Context(owner.name, Arrays.copyOf(args, 9), wrapper, Type.getMethodDescriptor(Type.VOID_TYPE, Arrays.copyOf(args, 9)));
        return pack(wrapper) == null ? null : context;
    }

    private static Patch site(ClassNode owner, Context context, MethodNode method, List<AbstractInsnNode> code, int at) {
        InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode) code.get(at); MethodNode bridge = implementation(owner, dynamic);
        if (bridge == null || !fingerprint(bridge, context).equals(BRIDGE_HASH) || at < 10 || at + 1 >= code.size() || !variable(code.get(at - 10), ALOAD, 0)) return null;
        for (int i = 0; i < 9; i++) if (!(code.get(at - 9 + i) instanceof VarInsnNode load) || load.getOpcode() != context.arguments[i].getOpcode(ILOAD)) return null;
        if (!(code.get(at + 1) instanceof MethodInsnNode call) || call.getOpcode() != INVOKEVIRTUAL || !call.owner.equals(owner.name)
                || !call.name.equals(context.wrapper.name) || !call.desc.equals(context.wrapper.desc) || !unbranched(method, code.get(at - 10), call)) return null;
        List<MethodInsnNode> renders = code(bridge).stream().filter(node -> node instanceof MethodInsnNode invocation && invocation.getOpcode() == INVOKEINTERFACE
            && invocation.owner.equals(context.arguments[0].getInternalName())).map(node -> (MethodInsnNode) node).toList();
        if (renders.size() != 1) return null; MethodInsnNode render = renders.getFirst();
        Frame frame = frameBefore(owner.name, method, dynamic);
        Object[] expected = new Object[]{owner.name, context.arguments[0].getInternalName(), context.arguments[1].getInternalName(), FLOAT, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT};
        return frame != null && Arrays.equals(frame.stack, expected) ? new Patch(method, dynamic, call, render, frame) : null;
    }

    private static MethodNode typed(Context context, MethodInsnNode render) {
        MethodNode result = new MethodNode(ASM9, ACC_PRIVATE | ACC_SYNTHETIC, TYPED, context.typedDescriptor, null, null);
        context.wrapper.accept(result); result.name = TYPED; result.desc = context.typedDescriptor; result.signature = null; result.access = ACC_PRIVATE | ACC_SYNTHETIC;
        result.visibleAnnotations = result.invisibleAnnotations = null; result.visibleParameterAnnotations = result.invisibleParameterAnnotations = null;
        result.visibleAnnotableParameterCount = result.invisibleAnnotableParameterCount = 0;
        if (result.parameters != null) result.parameters = new ArrayList<>(result.parameters.subList(0, 9));
        if (result.localVariables != null) result.localVariables.removeIf(variable -> variable.index == 10);
        Pack pack = pack(result); if (pack == null) return null; InsnList direct = new InsnList();
        for (int i = 0; i < 9; i++) direct.add(new VarInsnNode(context.arguments[i].getOpcode(ILOAD), i + 1));
        direct.add(new MethodInsnNode(INVOKEINTERFACE, render.owner, render.name, render.desc, render.itf));
        result.instructions.insertBefore(pack.first, direct); AbstractInsnNode after = pack.last.getNext();
        for (AbstractInsnNode node = pack.first; node != after;) { AbstractInsnNode following = node.getNext(); result.instructions.remove(node); node = following; }
        for (AbstractInsnNode node : result.instructions) {
            if (node instanceof FrameNode frame && frame.local != null && frame.local.size() > 10) frame.local.set(10, TOP);
            if (node instanceof VarInsnNode variable && variable.var == 10 || node instanceof IincInsnNode increment && increment.var == 10) return null;
            if (node instanceof MethodInsnNode call && (call.owner.equals(OP) || call.owner.equals("java/lang/Float") && call.name.equals("valueOf"))) return null;
        }
        result.maxStack = Math.max(10, result.maxStack); return result;
    }

    private static Pack pack(MethodNode method) {
        List<AbstractInsnNode> code = code(method); Pack result = null;
        for (int at = 0; at < code.size(); at++) if (variable(code.get(at), ALOAD, 10)) {
            if (result != null) return null; int i = at + 1;
            if (i + 2 >= code.size() || !integer(code.get(i++), 9) || !(code.get(i++) instanceof TypeInsnNode array) || array.getOpcode() != ANEWARRAY || !array.desc.equals("java/lang/Object")) return null;
            for (int arg = 0; arg < 9; arg++) {
                if (i + 4 >= code.size() || code.get(i++).getOpcode() != DUP || !integer(code.get(i++), arg) || !variable(code.get(i++), arg < 2 ? ALOAD : FLOAD, arg + 1)) return null;
                if (arg >= 2 && !method(code.get(i++), INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;")) return null;
                if (code.get(i++).getOpcode() != AASTORE) return null;
            }
            if (i + 1 >= code.size() || !method(code.get(i++), INVOKEINTERFACE, OP, "call", "([Ljava/lang/Object;)Ljava/lang/Object;") || code.get(i).getOpcode() != POP || !unbranched(method, code.get(at), code.get(i))) return null;
            result = new Pack(code.get(at), code.get(i)); at = i;
        }
        return result;
    }

    private static MethodNode implementation(ClassNode owner, InvokeDynamicInsnNode call) {
        if (!call.name.equals("call") || !call.desc.equals("()L" + OP + ";") || !call.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") || !call.bsm.getName().equals("metafactory")
                || call.bsmArgs.length != 3 || !(call.bsmArgs[0] instanceof Type generic) || !generic.getDescriptor().equals("([Ljava/lang/Object;)Ljava/lang/Object;")
                || !(call.bsmArgs[1] instanceof Handle handle) || handle.getTag() != H_INVOKESTATIC || !handle.getOwner().equals(owner.name)
                || !(call.bsmArgs[2] instanceof Type erased) || !erased.getDescriptor().equals("([Ljava/lang/Object;)Ljava/lang/Void;") || !handle.getName().startsWith("mixinextras$bridge$")) return null;
        MethodNode method = owner.methods.stream().filter(value -> value.name.equals(handle.getName()) && value.desc.equals(handle.getDesc())).findFirst().orElse(null);
        return method != null && method.access == (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC) && method.tryCatchBlocks.isEmpty() ? method : null;
    }

    private static boolean unbranched(MethodNode method, AbstractInsnNode first, AbstractInsnNode last) {
        int from = method.instructions.indexOf(first), to = method.instructions.indexOf(last);
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof JumpInsnNode jump && inside(method, jump.label, from, to)) return false;
            if (node instanceof TableSwitchInsnNode branch) { if (inside(method, branch.dflt, from, to)) return false; for (LabelNode label : branch.labels) if (inside(method, label, from, to)) return false; }
            if (node instanceof LookupSwitchInsnNode branch) { if (inside(method, branch.dflt, from, to)) return false; for (LabelNode label : branch.labels) if (inside(method, label, from, to)) return false; }
        }
        for (AbstractInsnNode node = first; node != last; node = node.getNext()) if (node instanceof LabelNode) return false;
        return true;
    }

    private static Frame frameBefore(String owner, MethodNode method, InvokeDynamicInsnNode wanted) {
        AnalyzerAdapter analyzer = new AnalyzerAdapter(owner, method.access, method.name, method.desc, null); Frame[] result = {null}; int[] ordinal = {0};
        int expected = 0; for (AbstractInsnNode node : method.instructions) { if (node == wanted) break; if (node instanceof InvokeDynamicInsnNode) expected++; } final int index = expected;
        method.accept(new MethodVisitor(ASM9, analyzer) {
            @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                if (ordinal[0]++ == index && analyzer.locals != null && analyzer.stack != null) result[0] = new Frame(values(analyzer.locals), values(analyzer.stack));
                super.visitInvokeDynamicInsn(name, descriptor, bootstrap, arguments);
            }
        });
        return result[0];
    }
    private static Object[] values(List<Object> values) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) { Object value = values.get(i); if (!(value instanceof String) && !(value instanceof Integer)) throw new IllegalArgumentException("Uninitialized layer operation frame"); result.add(value); if ((value.equals(LONG) || value.equals(DOUBLE)) && i + 1 < values.size() && values.get(i + 1).equals(TOP)) i++; }
        while (!result.isEmpty() && result.getLast().equals(TOP)) result.removeLast(); return result.toArray();
    }
    private static FrameNode frame(Object[] locals, Object[] stack) { return new FrameNode(F_FULL, locals.length, locals, stack.length, stack); }
    private static ClassNode expanded(ClassNode source) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); source.accept(writer); ClassNode result = new ClassNode(ASM9); new ClassReader(writer.toByteArray()).accept(result, ClassReader.EXPAND_FRAMES); return result; }
    private static boolean merged(MethodNode method) {
        if (method.visibleAnnotations == null) return false;
        for (AnnotationNode annotation : method.visibleAnnotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null) {
            Object name = null, priority = null; for (int i = 0; i + 1 < annotation.values.size(); i += 2) { if (annotation.values.get(i).equals("mixin")) name = annotation.values.get(i + 1); if (annotation.values.get(i).equals("priority")) priority = annotation.values.get(i + 1); }
            if (MIXIN.equals(name) && Integer.valueOf(1000).equals(priority)) return true;
        }
        return false;
    }
    private static String fingerprint(MethodNode method, Context context) {
        List<AbstractInsnNode> instructions = code(method); StringBuilder text = new StringBuilder(method.access + "|" + normalize(method.desc, context));
        for (AbstractInsnNode node : instructions) {
            text.append('|').append(node.getOpcode());
            if (node instanceof VarInsnNode variable) text.append(':').append(variable.var);
            else if (node instanceof IntInsnNode integer) text.append(':').append(integer.operand);
            else if (node instanceof TypeInsnNode type) text.append(':').append(normalize(type.desc, context));
            else if (node instanceof MethodInsnNode call) { String name = call.name; if (call.owner.equals(context.arguments[0].getInternalName()) && (name.equals("render") || name.equals("m_90564419"))) name = "@render"; text.append(':').append(normalize(call.owner, context)).append('.').append(name).append(normalize(call.desc, context)).append(':').append(call.itf); }
            else if (node instanceof FieldInsnNode field) text.append(':').append(normalize(field.owner, context)).append('.').append(field.name).append(normalize(field.desc, context));
            else if (node instanceof JumpInsnNode jump) text.append(':').append(position(instructions, jump.label));
            else if (node instanceof LdcInsnNode constant) text.append(':').append(normalize(String.valueOf(constant.cst), context));
            else if (!(node instanceof InsnNode)) throw new IllegalArgumentException("Unsupported layer operation instruction");
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) text.append("|catch:").append(position(instructions, block.start)).append(':').append(position(instructions, block.end)).append(':').append(position(instructions, block.handler)).append(':').append(block.type);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static int position(List<AbstractInsnNode> code, AbstractInsnNode node) { while (node != null && node.getOpcode() < 0) node = node.getNext(); return node == null ? code.size() : code.indexOf(node); }
    private static String normalize(String value, Context context) { value = value.replace(context.owner, "@owner"); for (int i = 0; i < 2; i++) { String type = context.arguments[i].getInternalName(); value = value.replace(type, "@arg" + i).replace(type.replace('/', '.'), "@arg" + i); } return value; }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) result.add(node); return result; }
    private static boolean inside(MethodNode method, LabelNode label, int from, int to) { int position = method.instructions.indexOf(label); return position > from && position <= to; }
    private static boolean variable(AbstractInsnNode node, int opcode, int slot) { return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot; }
    private static boolean integer(AbstractInsnNode node, int value) { return node.getOpcode() == ICONST_0 + value && value >= -1 && value <= 5 || node instanceof IntInsnNode integer && (integer.getOpcode() == BIPUSH || integer.getOpcode() == SIPUSH) && integer.operand == value; }
    private static boolean method(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) { return node instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor); }
}
