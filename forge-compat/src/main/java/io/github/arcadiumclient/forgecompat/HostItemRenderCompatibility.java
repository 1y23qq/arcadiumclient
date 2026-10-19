package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostItemRenderCompatibility {
    private static final String RENDER_ITEM = "net/minecraft/client/renderer/entity/RenderItem";
    private static final String BUFFER = "net/minecraft/client/renderer/WorldRenderer";
    private static final String QUAD = "net/minecraft/client/renderer/block/model/BakedQuad";
    private static final String STACK = "net/minecraft/item/ItemStack";
    private static final String MODEL = "net/minecraft/client/resources/model/IBakedModel";
    private static final String CAMERA = "net/minecraft/client/renderer/block/model/ItemCameraTransforms";
    private static final String TRANSFORM = CAMERA + "$TransformType";
    private static final String LIGHT = "net/minecraftforge/client/model/pipeline/LightUtil";
    private static final String HOOKS = "net/minecraftforge/client/ForgeHooksClient";
    private static final String PERSPECTIVE = "net/minecraftforge/client/model/IPerspectiveAwareModel";

    private HostItemRenderCompatibility() { }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(mappings.classFromSrg(RENDER_ITEM))) return;
        ClassNode restored = new ClassNode();
        node.accept(restored);
        restoreQuadCall(restored, mappings);
        restoreCameraCall(restored, mappings);
        node.methods = restored.methods;
    }

    private static void restoreQuadCall(ClassNode node, ForgeMappings mappings) {
        String quadDescriptor = mappings.descriptorFromSrg("(L" + BUFFER + ";L" + QUAD + ";I)V");
        String listDescriptor = mappings.descriptorFromSrg("(L" + BUFFER + ";Ljava/util/List;IL" + STACK + ";)V");
        String quadName = mappings.methodFromSrg(RENDER_ITEM, "func_175033_a", "(L" + BUFFER + ";L" + QUAD + ";I)V");
        String listName = mappings.methodFromSrg(RENDER_ITEM, "func_175032_a", "(L" + BUFFER + ";Ljava/util/List;IL" + STACK + ";)V");
        MethodNode list = method(node, listName, listDescriptor);
        if (list == null) return;
        List<MethodInsnNode> calls = calls(list, LIGHT, "renderQuadColor", quadDescriptor);
        if (calls.isEmpty()) return;
        MethodNode quad = method(node, quadName, quadDescriptor);
        if (calls.size() != 1 || quad == null || !instanceMethod(list) || !instanceMethod(quad)
                || !calls(list, node.name, quadName, quadDescriptor).isEmpty()) {
            throw unsupported("quad delegation");
        }
        MethodInsnNode call = calls.get(0);
        AbstractInsnNode color = previous(call), face = previous(color), buffer = previous(face);
        if (call.getOpcode() != Opcodes.INVOKESTATIC || call.itf || !load(color, Opcodes.ILOAD, -1)
                || !load(face, Opcodes.ALOAD, -1) || !load(buffer, Opcodes.ALOAD, 1)
                || buffer.getNext() != face || face.getNext() != color || color.getNext() != call) {
            throw unsupported("quad operands");
        }
        list.instructions.insertBefore(buffer, new VarInsnNode(Opcodes.ALOAD, 0));
        call.setOpcode((quad.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL);
        call.owner = node.name;
        call.name = quadName;
        list.maxStack = Math.max(list.maxStack, 4);

        quad.instructions = new InsnList();
        quad.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        quad.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        quad.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        quad.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIGHT, "renderQuadColor", quadDescriptor, false));
        quad.instructions.add(new InsnNode(Opcodes.RETURN));
        quad.tryCatchBlocks.clear();
        quad.localVariables = null;
        quad.visibleLocalVariableAnnotations = null;
        quad.invisibleLocalVariableAnnotations = null;
        quad.maxStack = 3;
        quad.maxLocals = 4;
    }

    private static void restoreCameraCall(ClassNode node, ForgeMappings mappings) {
        String descriptor = mappings.descriptorFromSrg("(L" + STACK + ";L" + MODEL + ";L" + TRANSFORM + ";)V");
        String name = mappings.methodFromSrg(RENDER_ITEM, "func_175040_a", "(L" + STACK + ";L" + MODEL + ";L" + TRANSFORM + ";)V");
        MethodNode render = method(node, name, descriptor);
        if (render == null) return;
        String model = mappings.classFromSrg(MODEL), camera = mappings.classFromSrg(CAMERA);
        String transform = mappings.classFromSrg(TRANSFORM);
        String hookDescriptor = "(L" + model + ";L" + transform + ";)L" + model + ";";
        String applyDescriptor = "(L" + transform + ";)V";
        String applyName = mappings.methodFromSrg(CAMERA, "func_181689_a", "(L" + TRANSFORM + ";)V");
        String getter = mappings.methodFromSrg(MODEL, "func_177552_f", "()L" + CAMERA + ";");
        List<MethodInsnNode> forge = calls(render, HOOKS, "handleCameraTransforms", hookDescriptor);
        if (forge.isEmpty()) return;
        List<MethodInsnNode> ordinary = calls(render, camera, applyName, applyDescriptor);
        if (forge.size() == 1 && ordinary.size() == 1 && normalizedCamera(render, ordinary.get(0), forge.get(0), model, camera, getter)) return;
        if (forge.size() != 1 || !ordinary.isEmpty() || !instanceMethod(render) || render.maxLocals != 4) {
            throw unsupported("camera delegation");
        }
        MethodInsnNode call = forge.get(0);
        AbstractInsnNode type = previous(call), bakedModel = previous(type), store = next(call), before = previous(bakedModel);
        if (call.getOpcode() != Opcodes.INVOKESTATIC || call.itf || !load(type, Opcodes.ALOAD, 3)
                || !load(bakedModel, Opcodes.ALOAD, 2) || !load(store, Opcodes.ASTORE, 2)
                || bakedModel.getNext() != type || type.getNext() != call || call.getNext() != store
                || !(before instanceof MethodInsnNode setup) || setup.getOpcode() != Opcodes.INVOKESTATIC || !setup.desc.equals("()V")) {
            throw unsupported("camera operands");
        }
        LabelNode custom = new LabelNode(), done = new LabelNode();
        Object[] locals = {node.name, mappings.classFromSrg(STACK), model, transform};
        InsnList body = new InsnList();
        body.add(new VarInsnNode(Opcodes.ALOAD, 2));
        body.add(new TypeInsnNode(Opcodes.INSTANCEOF, PERSPECTIVE));
        body.add(new JumpInsnNode(Opcodes.IFNE, custom));
        body.add(new VarInsnNode(Opcodes.ALOAD, 2));
        body.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, model, getter, "()L" + camera + ";", true));
        body.add(new VarInsnNode(Opcodes.ALOAD, 3));
        body.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, camera, applyName, applyDescriptor, false));
        body.add(new VarInsnNode(Opcodes.ALOAD, 2));
        body.add(new JumpInsnNode(Opcodes.GOTO, done));
        body.add(custom);
        body.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        body.add(new VarInsnNode(Opcodes.ALOAD, 2));
        body.add(new VarInsnNode(Opcodes.ALOAD, 3));
        body.add(new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf));
        body.add(done);
        body.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 1, new Object[]{model}));
        render.instructions.insertBefore(bakedModel, body);
        render.instructions.remove(bakedModel);
        render.instructions.remove(type);
        render.instructions.remove(call);
        render.maxStack = Math.max(render.maxStack, 2);
    }

    private static boolean normalizedCamera(MethodNode method, MethodInsnNode ordinary, MethodInsnNode forge,
                                            String model, String camera, String getter) {
        long guards = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof TypeInsnNode type
                && type.getOpcode() == Opcodes.INSTANCEOF && type.desc.equals(PERSPECTIVE)).count();
        AbstractInsnNode type = previous(ordinary), transforms = previous(type), source = previous(transforms);
        AbstractInsnNode result = next(ordinary), jump = next(result);
        return guards == 1 && load(type, Opcodes.ALOAD, 3) && load(source, Opcodes.ALOAD, 2)
            && transforms instanceof MethodInsnNode get && get.getOpcode() == Opcodes.INVOKEINTERFACE
            && get.owner.equals(model) && get.name.equals(getter) && get.desc.equals("()L" + camera + ";")
            && load(result, Opcodes.ALOAD, 2) && jump instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.GOTO
            && load(previous(forge), Opcodes.ALOAD, 3) && load(previous(previous(forge)), Opcodes.ALOAD, 2)
            && load(next(forge), Opcodes.ASTORE, 2) && method.maxLocals == 4;
    }

    private static boolean instanceMethod(MethodNode method) {
        return (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0;
    }
    private static boolean load(AbstractInsnNode instruction, int opcode, int slot) {
        return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && (slot < 0 || variable.var == slot);
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, String descriptor) {
        return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)).map(MethodInsnNode.class::cast).toList();
    }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static IllegalStateException unsupported(String shape) {
        return new IllegalStateException("Unsupported Forge item-render " + shape);
    }
}
