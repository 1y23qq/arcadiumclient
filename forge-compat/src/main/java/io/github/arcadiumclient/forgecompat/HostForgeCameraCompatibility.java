package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.*;

final class HostForgeCameraCompatibility implements Opcodes {
    static final String TARGET = "net/minecraftforge/client/ForgeHooksClient";
    static final String BRIDGE = Type.getInternalName(ForgeItemCameraBridge.class);
    private static final String PERSPECTIVE = "net/minecraftforge/client/model/IPerspectiveAwareModel";
    private static final String PAIR = "org/apache/commons/lang3/tuple/Pair";
    private static final String MATRIX = "javax/vecmath/Matrix4f";
    private static final String MODEL = "net/minecraft/client/resources/model/IBakedModel";
    private static final String CAMERA = "net/minecraft/client/renderer/block/model/ItemCameraTransforms";
    private static final String TRANSFORM = CAMERA + "$TransformType";
    private static final String APPLY = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z";

    private HostForgeCameraCompatibility() { }

    static boolean normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(TARGET)) return false;
        String model = mappings.classFromSrg(MODEL), camera = mappings.classFromSrg(CAMERA), transform = mappings.classFromSrg(TRANSFORM);
        String descriptor = "(L" + model + ";L" + transform + ";)L" + model + ";";
        List<MethodNode> matches = node.methods.stream().filter(method -> method.name.equals("handleCameraTransforms") && method.desc.equals(descriptor)).toList();
        if (matches.size() != 1) return false;
        MethodNode original = matches.get(0);
        if (original.access != (ACC_PUBLIC | ACC_STATIC) || original.maxLocals != 3 || original.signature != null || !original.exceptions.isEmpty() || !original.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> code = code(original);
        if (code.size() != 25 || !load(code.get(0), 0) || !type(code.get(1), INSTANCEOF, PERSPECTIVE)
            || !jump(code.get(2), IFEQ, code, 19) || !load(code.get(3), 0) || !type(code.get(4), CHECKCAST, PERSPECTIVE) || !load(code.get(5), 1)
            || !call(code.get(6), INVOKEINTERFACE, PERSPECTIVE, "handlePerspective", "(L" + transform + ";)L" + PAIR + ";", true)
            || !(code.get(7) instanceof VarInsnNode store) || store.getOpcode() != ASTORE || store.var != 2 || !load(code.get(8), 2)
            || !call(code.get(9), INVOKEVIRTUAL, PAIR, "getRight", "()Ljava/lang/Object;", false) || !jump(code.get(10), IFNULL, code, 15)
            || !load(code.get(11), 2) || !call(code.get(12), INVOKEVIRTUAL, PAIR, "getRight", "()Ljava/lang/Object;", false)
            || !type(code.get(13), CHECKCAST, MATRIX) || !call(code.get(14), INVOKESTATIC, TARGET, "multiplyCurrentGlMatrix", "(L" + MATRIX + ";)V", false)
            || !load(code.get(15), 2) || !call(code.get(16), INVOKEVIRTUAL, PAIR, "getLeft", "()Ljava/lang/Object;", false)
            || !type(code.get(17), CHECKCAST, model) || code.get(18).getOpcode() != ARETURN || !load(code.get(19), 0)
            || !call(code.get(20), INVOKEINTERFACE, model, mappings.methodFromSrg(MODEL, "func_177552_f", "()L" + CAMERA + ";"), "()L" + camera + ";", true)
            || !load(code.get(21), 1) || !call(code.get(22), INVOKEVIRTUAL, camera, mappings.methodFromSrg(CAMERA, "func_181689_a", "(L" + TRANSFORM + ";)V"), "(L" + transform + ";)V", false)
            || !load(code.get(23), 0) || code.get(24).getOpcode() != ARETURN) return false;
        MethodNode method;
        try {
            method = expanded(node, original);
            AnalyzerAdapter analyzer = new AnalyzerAdapter(node.name, method.access, method.name, method.desc, null);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode multiply && multiply.name.equals("multiplyCurrentGlMatrix")) {
                    if (!List.of(model, transform, PAIR).equals(analyzer.locals) || !List.of(MATRIX).equals(analyzer.stack)) return false;
                    break;
                }
                instruction.accept(analyzer);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException unsupported) { return false; }
        List<AbstractInsnNode> expanded = code(method); AbstractInsnNode multiply = expanded.get(14);
        LabelNode nativeMultiply = new LabelNode(), done = ((JumpInsnNode) expanded.get(10)).label;
        InsnList bridge = new InsnList(); bridge.add(new InsnNode(DUP));
        bridge.add(new VarInsnNode(ALOAD, 0)); bridge.add(new VarInsnNode(ALOAD, 1)); bridge.add(new VarInsnNode(ALOAD, 2));
        bridge.add(new MethodInsnNode(INVOKESTATIC, BRIDGE, "apply", APPLY, false)); bridge.add(new JumpInsnNode(IFEQ, nativeMultiply));
        bridge.add(new InsnNode(POP)); bridge.add(new JumpInsnNode(GOTO, done)); bridge.add(nativeMultiply);
        bridge.add(new FrameNode(F_NEW, 3, new Object[]{model, transform, PAIR}, 1, new Object[]{MATRIX}));
        method.instructions.insertBefore(multiply, bridge); method.maxStack = Math.max(method.maxStack, 5);
        node.methods.set(node.methods.indexOf(original), method); return true;
    }

    private static MethodNode expanded(ClassNode owner, MethodNode method) {
        ClassNode holder = new ClassNode(); holder.version = owner.version; holder.access = ACC_PUBLIC; holder.name = owner.name; holder.superName = "java/lang/Object"; holder.methods.add(method);
        ClassWriter writer = new ClassWriter(0); holder.accept(writer); ClassNode result = new ClassNode(); new ClassReader(writer.toByteArray()).accept(result, ClassReader.EXPAND_FRAMES);
        return result.methods.get(0);
    }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction); return result; }
    private static boolean load(AbstractInsnNode instruction, int slot) { return instruction instanceof VarInsnNode load && load.getOpcode() == ALOAD && load.var == slot; }
    private static boolean type(AbstractInsnNode instruction, int opcode, String name) { return instruction instanceof TypeInsnNode type && type.getOpcode() == opcode && type.desc.equals(name); }
    private static boolean call(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor, boolean itf) { return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor) && call.itf == itf; }
    private static boolean jump(AbstractInsnNode instruction, int opcode, List<AbstractInsnNode> code, int target) { return instruction instanceof JumpInsnNode jump && jump.getOpcode() == opcode && position(jump.label, code) == target; }
    private static int position(AbstractInsnNode instruction, List<AbstractInsnNode> code) { while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext(); return code.indexOf(instruction); }
}
