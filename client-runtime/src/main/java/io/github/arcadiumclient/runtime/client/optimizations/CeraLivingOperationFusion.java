// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.*;

public final class CeraLivingOperationFusion implements Opcodes {
    private static final String OP = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String CERA = "dev.rdh.cera.mixin.LivingEntityRendererMixin";
    private static final String TINT = "org.polyfrost.overflowanimations.mixins.legacy.tint.MixinLivingEntityRenderer_DamageTint";
    private static final String REF = "com/llamalad7/mixinextras/sugar/ref/LocalFloatRef";
    private static final String REF_IMPL = "com/llamalad7/mixinextras/sugar/impl/ref/generated/LocalFloatRefImpl";
    private static final String CONTROL = Type.getInternalName(RenderHookAllocationControl.class);
    private static final String TYPED_CERA = "arcadium$typedCeraEmissivePass";
    private static final String TYPED_TINT = "arcadium$typedCeraFlatTint";
    private static final String TYPED_SUGAR = "arcadium$typedCeraTintSugar";
    private static final String CERA_HASH = "a84fc44aee2a78ed8209c052d505ee4d9413bcb0b5f8a5d2cedfdd256c2cb065";
    private static final String TINT_HASH = "a6d7a3eee6dd60361a7d1186c7b51d4d28fd2f3c0f88ef8b05c7b9b535441cc6";
    private static final String SUGAR_HASH = "8409f2ebdda7d8de4160c24a2a392252083aa4e6cece47bbd9d75eae3f4de4b8";
    private static final String BRIDGE_HASH = "01dd298c7912831ec3c513a8cd8764fb18ec3e7c95362063ab86600be1f8a08d";
    private static final String BASE_HASH = "d82e5f1d22f3aa242ab65ce29535279161bf13da29f9c0110ba59e8373d9b8c3";
    public record Site(String method, String descriptor, boolean captured) { }
    private record Context(String owner, Type[] arguments, MethodNode cera, MethodNode tint, MethodNode sugar, String typedDescriptor) { }
    private record Pack(AbstractInsnNode first, AbstractInsnNode last) { }
    private record Frame(Object[] locals, Object[] stack) { }
    private record Patch(MethodNode caller, InvokeDynamicInsnNode first, MethodInsnNode last, List<AbstractInsnNode> setup, Frame before, Frame after, boolean captured) { }

    private CeraLivingOperationFusion() { }

    public static List<Site> apply(ClassNode target) {
        try { return applyChecked(target); }
        catch (IllegalArgumentException | IndexOutOfBoundsException unsupported) { return List.of(); }
    }

    private static List<Site> applyChecked(ClassNode target) {
        if (target.methods.stream().anyMatch(method -> method.name.startsWith("arcadium$typedCera"))) return List.of();
        ClassNode work = expanded(target); Context context = context(work);
        if (context == null || !fingerprint(context.cera, context).equals(CERA_HASH)
                || !fingerprint(context.tint, context).equals(TINT_HASH) || !fingerprint(context.sugar, context).equals(SUGAR_HASH)) return List.of();
        List<Patch> patches = new ArrayList<>(); MethodInsnNode render = null;
        for (MethodNode method : work.methods) {
            List<AbstractInsnNode> code = code(method);
            for (int at = 0; at < code.size(); at++) if (code.get(at) instanceof InvokeDynamicInsnNode dynamic && dynamic.desc.equals("()L" + OP + ";")) {
                MethodNode base = implementation(work, dynamic);
                if (base == null || base.access != (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC) || !fingerprint(base, context).equals(BASE_HASH)) continue;
                Patch patch = site(work, context, method, code, at);
                if (patch == null) continue;
                List<MethodInsnNode> calls = code(base).stream().filter(node -> node instanceof MethodInsnNode call && call.getOpcode() == INVOKEVIRTUAL && call.owner.equals(context.owner)).map(node -> (MethodInsnNode) node).toList();
                if (calls.size() != 1) return List.of(); MethodInsnNode call = calls.getFirst();
                if (!call.desc.equals(Type.getMethodDescriptor(Type.VOID_TYPE, Arrays.copyOfRange(context.arguments, 1, 8)))) return List.of();
                if (render != null && (!render.name.equals(call.name) || !render.desc.equals(call.desc) || render.itf != call.itf)) return List.of();
                render = call; patches.add(patch);
            }
        }
        if (patches.size() != 2 || patches.stream().filter(Patch::captured).count() != 1 || render == null) return List.of();
        MethodNode typedCera = typedCera(context, render), typedTint = typedTint(context), typedSugar = typedSugar(context);
        if (typedCera == null || typedTint == null || typedSugar == null) return List.of();
        for (Patch patch : patches) {
            LabelNode slow = new LabelNode(), done = new LabelNode(); InsnList fast = new InsnList();
            fast.add(new FieldInsnNode(GETSTATIC, CONTROL, "enabled", "Z")); fast.add(new JumpInsnNode(IFEQ, slow));
            if (patch.captured) {
                fast.add(new InsnNode(POP)); fast.add(new InsnNode(POP));
                for (AbstractInsnNode instruction : patch.setup) fast.add(instruction.clone(new HashMap<>()));
                fast.add(new MethodInsnNode(INVOKESPECIAL, work.name, TYPED_SUGAR, withoutOperation(context.sugar.desc), false));
            } else fast.add(new MethodInsnNode(INVOKESPECIAL, work.name, TYPED_CERA, context.typedDescriptor, false));
            fast.add(new JumpInsnNode(GOTO, done)); fast.add(slow); fast.add(frame(patch.before));
            patch.caller.instructions.insertBefore(patch.first, fast);
            InsnList finish = new InsnList(); finish.add(done); finish.add(frame(patch.after));
            patch.caller.instructions.insert(patch.last, finish); patch.caller.maxStack = Math.max(16, patch.caller.maxStack);
        }
        work.methods.add(typedCera); work.methods.add(typedTint); work.methods.add(typedSugar); target.methods = work.methods;
        return patches.stream().map(patch -> new Site(patch.caller.name, patch.caller.desc, patch.captured)).toList();
    }

    public static Map<String, String> audit(ClassNode target) {
        ClassNode work = expanded(target); Context context = context(work); if (context == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("cera", fingerprint(context.cera, context)); result.put("tint", fingerprint(context.tint, context)); result.put("sugar", fingerprint(context.sugar, context));
        for (MethodNode method : work.methods) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof InvokeDynamicInsnNode dynamic) {
            MethodNode bridge = implementation(work, dynamic);
            if (bridge != null && (bridge.desc.equals("([Ljava/lang/Object;)Ljava/lang/Void;") || bridge.desc.equals("(L" + OP + ";[Ljava/lang/Object;)Ljava/lang/Void;"))) result.put("bridge:" + bridge.name, fingerprint(bridge, context));
        }
        return result;
    }

    private static Context context(ClassNode owner) {
        if (!owner.name.equals("net/minecraft/unmapped/C_71252617") && !owner.name.equals("net/minecraft/client/render/entity/LivingEntityRenderer")) return null;
        List<MethodNode> cera = owner.methods.stream().filter(method -> method.name.endsWith("$cera$emissivePass") && merged(method, CERA)).toList();
        List<MethodNode> tint = owner.methods.stream().filter(method -> method.name.endsWith("$overflowanimations$renderFlatTint") && merged(method, TINT)).toList();
        if (cera.size() != 1 || tint.size() != 1) return null;
        MethodNode wrapper = cera.getFirst(), flat = tint.getFirst(); Type[] args = Type.getArgumentTypes(wrapper.desc);
        if (wrapper.access != ACC_PRIVATE || flat.access != ACC_PRIVATE || args.length != 9 || Type.getReturnType(wrapper.desc) != Type.VOID_TYPE || !args[8].equals(Type.getObjectType(OP))) return null;
        for (int i = 0; i < 8; i++) if (i < 2 ? args[i].getSort() != Type.OBJECT : args[i] != Type.FLOAT_TYPE) return null;
        if (!args[0].getInternalName().equals(owner.name) || !flat.desc.equals(wrapper.desc.substring(0, wrapper.desc.length() - 2) + "F)V") || packs(wrapper).size() != 3 || packs(flat).size() != 1) return null;
        List<MethodNode> bridges = owner.methods.stream().filter(method -> method.name.startsWith(flat.name + "$mixinextras$bridge$") && merged(method, TINT)).toList();
        if (bridges.size() != 1) return null; MethodNode sugar = bridges.getFirst();
        String expected = wrapper.desc.substring(0, wrapper.desc.length() - 2) + "L" + REF + ";)V";
        if (sugar.access != ACC_PRIVATE || !sugar.desc.equals(expected)) return null;
        return new Context(owner.name, Arrays.copyOf(args, 8), wrapper, flat, sugar, Type.getMethodDescriptor(Type.VOID_TYPE, Arrays.copyOf(args, 8)));
    }

    private static Patch site(ClassNode owner, Context context, MethodNode caller, List<AbstractInsnNode> code, int at) {
        if (at < 9 || at + 1 >= code.size() || !variable(code.get(at - 9), ALOAD, 0)) return null;
        int[] slots = new int[8];
        for (int arg = 0; arg < 8; arg++) {
            if (!(code.get(at - 8 + arg) instanceof VarInsnNode load) || load.getOpcode() != context.arguments[arg].getOpcode(ILOAD)) return null;
            slots[arg] = load.var;
        }
        InvokeDynamicInsnNode base = (InvokeDynamicInsnNode) code.get(at); int i = at + 1;
        if (method(code.get(i), INVOKEVIRTUAL, owner.name, context.cera.name, context.cera.desc)) {
            MethodInsnNode last = (MethodInsnNode) code.get(i);
            if (!unbranched(caller, code.get(at - 9), last)) return null;
            Frame before = frameBefore(owner.name, caller, base), after = frameBefore(owner.name, caller, last.getNext());
            return frames(context, before, after, false) ? new Patch(caller, base, last, List.of(), before, after, false) : null;
        }
        if (i + 31 >= code.size() || !(code.get(i++) instanceof VarInsnNode operation) || operation.getOpcode() != ASTORE) return null;
        for (int arg = 7; arg >= 0; arg--) if (!variable(code.get(i++), context.arguments[arg].getOpcode(ISTORE), slots[arg])) return null;
        if (code.get(i++).getOpcode() != POP || !variable(code.get(i++), ALOAD, 0)) return null;
        for (int arg = 0; arg < 8; arg++) if (!variable(code.get(i++), context.arguments[arg].getOpcode(ILOAD), slots[arg])) return null;
        if (!variable(code.get(i++), ALOAD, 0)) return null; AbstractInsnNode operationLoad = code.get(i++);
        if (!variable(operationLoad, ALOAD, operation.var) || !(code.get(i++) instanceof InvokeDynamicInsnNode captured) || !captured.desc.equals("(L" + owner.name + ";L" + OP + ";)L" + OP + ";")) return null;
        MethodNode bridge = implementation(owner, captured);
        if (bridge == null || bridge.access != (ACC_PRIVATE | ACC_SYNTHETIC) || !fingerprint(bridge, context).equals(BRIDGE_HASH)) return null;
        int setupStart = i;
        if (!(code.get(i++) instanceof TypeInsnNode create) || create.getOpcode() != NEW || !create.desc.equals(REF_IMPL) || code.get(i++).getOpcode() != DUP
                || !method(code.get(i++), INVOKESPECIAL, REF_IMPL, "<init>", "()V") || !(code.get(i++) instanceof VarInsnNode ref) || ref.getOpcode() != ASTORE
                || !variable(code.get(i++), ALOAD, ref.var) || !variable(code.get(i++), ALOAD, ref.var)
                || !(code.get(i++) instanceof VarInsnNode tick) || tick.getOpcode() != FLOAD || !method(code.get(i++), INVOKEVIRTUAL, REF_IMPL, "init", "(F)V")
                || !method(code.get(i), INVOKEVIRTUAL, owner.name, context.sugar.name, context.sugar.desc)) return null;
        MethodInsnNode last = (MethodInsnNode) code.get(i);
        if (!unbranched(caller, code.get(at - 9), last)) return null;
        for (AbstractInsnNode instruction : caller.instructions) {
            if (instruction instanceof VarInsnNode var && var.var == operation.var && instruction != operation && instruction != operationLoad) return null;
            if (instruction instanceof IincInsnNode increment && increment.var == operation.var) return null;
        }
        Frame before = frameBefore(owner.name, caller, captured), after = frameBefore(owner.name, caller, last.getNext());
        return frames(context, before, after, true) ? new Patch(caller, captured, last, List.copyOf(code.subList(setupStart, i)), before, after, true) : null;
    }

    private static boolean frames(Context context, Frame before, Frame after, boolean captured) {
        List<Object> expected = new ArrayList<>(); expected.add(context.owner); expected.add(context.owner); expected.add(context.arguments[1].getInternalName());
        for (int i = 0; i < 6; i++) expected.add(FLOAT);
        if (captured) { expected.add(context.owner); expected.add(OP); }
        return before != null && after != null && Arrays.equals(before.stack, expected.toArray()) && after.stack.length == 0;
    }

    private static MethodNode typedCera(Context context, MethodInsnNode render) {
        MethodNode clone = clone(context.cera, TYPED_CERA); List<Pack> calls = packs(clone); if (calls.size() != 3) return null;
        for (Pack pack : calls) {
            InsnList direct = loads(context, false);
            direct.add(new MethodInsnNode(INVOKEVIRTUAL, render.owner, render.name, render.desc, render.itf)); replace(clone, pack, direct);
        }
        removeOperation(clone, null); return clean(clone) ? clone : null;
    }

    private static MethodNode typedTint(Context context) {
        MethodNode clone = clone(context.tint, TYPED_TINT); List<Pack> calls = packs(clone); if (calls.size() != 1) return null;
        InsnList direct = loads(context, true); direct.add(new MethodInsnNode(INVOKESPECIAL, context.owner, TYPED_CERA, context.typedDescriptor, false)); replace(clone, calls.getFirst(), direct);
        removeOperation(clone, Type.FLOAT_TYPE); return clean(clone) ? clone : null;
    }

    private static MethodNode typedSugar(Context context) {
        MethodNode clone = clone(context.sugar, TYPED_SUGAR); int calls = 0, loads = 0;
        for (AbstractInsnNode instruction : clone.instructions.toArray()) {
            if (variable(instruction, ALOAD, 9)) { clone.instructions.remove(instruction); loads++; }
            else if (instruction instanceof MethodInsnNode call && call.owner.equals(context.owner) && call.name.equals(context.tint.name) && call.desc.equals(context.tint.desc) && call.getOpcode() == INVOKESPECIAL) {
                call.name = TYPED_TINT; call.desc = withoutOperation(call.desc); calls++;
            }
        }
        if (calls != 1 || loads != 1) return null;
        removeOperation(clone, Type.getObjectType(REF)); return clean(clone) ? clone : null;
    }

    private static MethodNode clone(MethodNode source, String name) {
        MethodNode clone = new MethodNode(ASM9, source.access, name, source.desc, null, source.exceptions.toArray(String[]::new)); source.accept(clone);
        clone.name = name; clone.access = ACC_PRIVATE | ACC_SYNTHETIC; clone.signature = null;
        clone.visibleAnnotations = clone.invisibleAnnotations = null; clone.visibleParameterAnnotations = clone.invisibleParameterAnnotations = null;
        clone.visibleAnnotableParameterCount = clone.invisibleAnnotableParameterCount = 0; return clone;
    }

    private static void removeOperation(MethodNode clone, Type following) {
        clone.desc = withoutOperation(clone.desc);
        if (clone.parameters != null) clone.parameters.remove(8);
        if (clone.localVariables != null) clone.localVariables.removeIf(local -> local.index == 9);
        if (following != null) {
            InsnList copy = new InsnList(); copy.add(new VarInsnNode(following.getOpcode(ILOAD), 9)); copy.add(new VarInsnNode(following.getOpcode(ISTORE), 10)); clone.instructions.insert(copy);
        }
        for (AbstractInsnNode instruction : clone.instructions) if (instruction instanceof FrameNode frame && frame.local != null && frame.local.size() > 9) frame.local.set(9, TOP);
        clone.maxStack = Math.max(clone.maxStack, 10);
    }

    private static String withoutOperation(String descriptor) {
        List<Type> args = new ArrayList<>(List.of(Type.getArgumentTypes(descriptor))); if (!args.remove(8).equals(Type.getObjectType(OP))) throw new IllegalArgumentException("Unexpected Cera Operation parameter");
        return Type.getMethodDescriptor(Type.getReturnType(descriptor), args.toArray(Type[]::new));
    }

    private static boolean clean(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && (call.owner.equals(OP) || call.owner.equals("java/lang/Float") && call.name.equals("valueOf"))) return false;
        return true;
    }

    private static InsnList loads(Context context, boolean self) {
        InsnList code = new InsnList(); if (self) code.add(new VarInsnNode(ALOAD, 0));
        for (int arg = 0; arg < 8; arg++) code.add(new VarInsnNode(context.arguments[arg].getOpcode(ILOAD), arg + 1)); return code;
    }

    private static void replace(MethodNode method, Pack pack, InsnList replacement) {
        method.instructions.insertBefore(pack.first, replacement); AbstractInsnNode after = pack.last.getNext();
        for (AbstractInsnNode instruction = pack.first; instruction != after;) { AbstractInsnNode next = instruction.getNext(); method.instructions.remove(instruction); instruction = next; }
    }

    private static List<Pack> packs(MethodNode method) {
        List<AbstractInsnNode> code = code(method); List<Pack> packs = new ArrayList<>();
        for (int at = 0; at < code.size(); at++) if (variable(code.get(at), ALOAD, 9)) {
            int i = at + 1;
            if (i + 2 >= code.size() || !integer(code.get(i++), 8) || !(code.get(i++) instanceof TypeInsnNode array) || array.getOpcode() != ANEWARRAY || !array.desc.equals("java/lang/Object")) return List.of();
            for (int arg = 0; arg < 8; arg++) {
                if (i + 4 >= code.size() || code.get(i++).getOpcode() != DUP || !integer(code.get(i++), arg) || !variable(code.get(i++), arg < 2 ? ALOAD : FLOAD, arg + 1)) return List.of();
                if (arg >= 2 && !method(code.get(i++), INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;")) return List.of();
                if (code.get(i++).getOpcode() != AASTORE) return List.of();
            }
            if (i + 1 >= code.size() || !method(code.get(i++), INVOKEINTERFACE, OP, "call", "([Ljava/lang/Object;)Ljava/lang/Object;") || code.get(i).getOpcode() != POP || !unbranched(method, code.get(at), code.get(i))) return List.of();
            packs.add(new Pack(code.get(at), code.get(i))); at = i;
        }
        return packs;
    }

    private static MethodNode implementation(ClassNode owner, InvokeDynamicInsnNode dynamic) {
        if (!dynamic.name.equals("call") || !dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") || !dynamic.bsm.getName().equals("metafactory") || dynamic.bsm.getTag() != H_INVOKESTATIC || dynamic.bsm.isInterface()
                || !dynamic.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
                || dynamic.bsmArgs.length != 3 || !(dynamic.bsmArgs[0] instanceof Type generic) || !generic.getDescriptor().equals("([Ljava/lang/Object;)Ljava/lang/Object;")
                || !(dynamic.bsmArgs[1] instanceof Handle handle) || !handle.getOwner().equals(owner.name) || handle.isInterface() || !handle.getName().startsWith("mixinextras$bridge$")
                || !(dynamic.bsmArgs[2] instanceof Type erased) || !erased.getDescriptor().equals("([Ljava/lang/Object;)Ljava/lang/Void;")) return null;
        MethodNode bridge = owner.methods.stream().filter(method -> method.name.equals(handle.getName()) && method.desc.equals(handle.getDesc())).findFirst().orElse(null);
        return bridge != null && bridge.tryCatchBlocks.isEmpty() && handle.getTag() == ((bridge.access & ACC_STATIC) != 0 ? H_INVOKESTATIC : H_INVOKESPECIAL) ? bridge : null;
    }

    private static boolean unbranched(MethodNode method, AbstractInsnNode first, AbstractInsnNode last) {
        int from = method.instructions.indexOf(first), to = method.instructions.indexOf(last);
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump && inside(method, jump.label, from, to)) return false;
            if (instruction instanceof TableSwitchInsnNode branch) { if (inside(method, branch.dflt, from, to)) return false; for (LabelNode label : branch.labels) if (inside(method, label, from, to)) return false; }
            if (instruction instanceof LookupSwitchInsnNode branch) { if (inside(method, branch.dflt, from, to)) return false; for (LabelNode label : branch.labels) if (inside(method, label, from, to)) return false; }
        }
        for (AbstractInsnNode instruction = first; instruction != last; instruction = instruction.getNext()) if (instruction instanceof LabelNode) return false;
        return true;
    }

    private static Frame frameBefore(String owner, MethodNode method, AbstractInsnNode wanted) {
        AnalyzerAdapter analyzer = new AnalyzerAdapter(owner, method.access, method.name, method.desc, null);
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction == wanted) return analyzer.locals == null || analyzer.stack == null ? null : new Frame(values(analyzer.locals), values(analyzer.stack));
            instruction.accept(analyzer);
        }
        return null;
    }

    private static Object[] values(List<Object> source) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) { Object value = source.get(i); if (!(value instanceof String) && !(value instanceof Integer)) throw new IllegalArgumentException("Uninitialized Cera frame"); result.add(value); if ((value.equals(LONG) || value.equals(DOUBLE)) && i + 1 < source.size() && source.get(i + 1).equals(TOP)) i++; }
        while (!result.isEmpty() && result.getLast().equals(TOP)) result.removeLast(); return result.toArray();
    }

    private static FrameNode frame(Frame frame) { return new FrameNode(F_FULL, frame.locals.length, frame.locals, frame.stack.length, frame.stack); }
    private static ClassNode expanded(ClassNode source) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); source.accept(writer); ClassNode result = new ClassNode(ASM9); new ClassReader(writer.toByteArray()).accept(result, ClassReader.EXPAND_FRAMES); return result; }
    private static boolean merged(MethodNode method, String expected) {
        if (method.visibleAnnotations == null) return false;
        for (AnnotationNode annotation : method.visibleAnnotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null) {
            Object mixin = null, priority = null;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) { if (annotation.values.get(i).equals("mixin")) mixin = annotation.values.get(i + 1); if (annotation.values.get(i).equals("priority")) priority = annotation.values.get(i + 1); }
            if (expected.equals(mixin) && Integer.valueOf(1000).equals(priority)) return true;
        }
        return false;
    }

    private static String fingerprint(MethodNode method, Context context) {
        List<AbstractInsnNode> instructions = code(method); StringBuilder text = new StringBuilder(method.access + "|" + normalize(method.desc, context));
        for (AbstractInsnNode instruction : instructions) {
            text.append('|').append(instruction.getOpcode());
            if (instruction instanceof VarInsnNode variable) text.append(':').append(variable.var);
            else if (instruction instanceof IntInsnNode integer) text.append(':').append(integer.operand);
            else if (instruction instanceof TypeInsnNode type) text.append(':').append(normalize(type.desc, context));
            else if (instruction instanceof MethodInsnNode call) {
                String name = call.name;
                if (call.owner.equals(context.owner)) { if (name.equals(context.cera.name)) name = "@cera"; else if (name.equals(context.tint.name)) name = "@tint"; else if (name.equals(context.sugar.name)) name = "@sugar"; }
                text.append(':').append(normalize(call.owner, context)).append('.').append(name).append(normalize(call.desc, context)).append(':').append(call.itf);
            } else if (instruction instanceof FieldInsnNode field) text.append(':').append(normalize(field.owner, context)).append('.').append(field.name).append(normalize(field.desc, context));
            else if (instruction instanceof JumpInsnNode jump) text.append(':').append(position(instructions, jump.label));
            else if (instruction instanceof LdcInsnNode constant) text.append(':').append(normalize(String.valueOf(constant.cst), context));
            else if (!(instruction instanceof InsnNode)) throw new IllegalArgumentException("Unsupported Cera instruction");
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) text.append("|catch:").append(position(instructions, block.start)).append(':').append(position(instructions, block.end)).append(':').append(position(instructions, block.handler)).append(':').append(block.type);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String normalize(String value, Context context) { return value.replace(context.owner, "@owner").replace(context.owner.replace('/', '.'), "@owner").replace(context.arguments[1].getInternalName(), "@entity").replace(context.arguments[1].getClassName(), "@entity"); }
    private static int position(List<AbstractInsnNode> code, AbstractInsnNode instruction) { while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext(); return instruction == null ? code.size() : code.indexOf(instruction); }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction); return result; }
    private static boolean inside(MethodNode method, LabelNode label, int from, int to) { int at = method.instructions.indexOf(label); return at > from && at <= to; }
    private static boolean variable(AbstractInsnNode instruction, int opcode, int slot) { return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot; }
    private static boolean integer(AbstractInsnNode instruction, int value) { return instruction.getOpcode() == ICONST_0 + value && value >= -1 && value <= 5 || instruction instanceof IntInsnNode integer && (integer.getOpcode() == BIPUSH || integer.getOpcode() == SIPUSH) && integer.operand == value; }
    private static boolean method(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) { return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor); }
}
