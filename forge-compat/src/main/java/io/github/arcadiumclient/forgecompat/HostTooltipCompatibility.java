package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostTooltipCompatibility {
    private static final String SCREEN = "net/minecraft/client/gui/GuiScreen";
    private static final String FONT = "net/minecraft/client/gui/FontRenderer";
    private static final String UTILS = "net/minecraftforge/fml/client/config/GuiUtils";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostTooltipCompatibility";
    private static final String FONT_STATE = "arcadium$forgeTooltipFont";
    private static final String THREE = "(Ljava/util/List;II)V";
    private static final int BACKGROUND = -267386864;
    private static final Object NULL_FONT = new Object();

    private HostTooltipCompatibility() { }

    public static Object fontToken(Object font) { return font == null ? NULL_FONT : font; }
    public static Object selectFont(Object token, Object fallback) { return token == NULL_FONT ? null : token == null ? fallback : token; }

    public static void normalize(ClassNode node, ForgeMappings mappings, FabricLauncher launcher) throws IOException {
        if (!node.name.equals(mappings.classFromSrg(SCREEN))) return;
        String font = mappings.classFromSrg(FONT);
        String methodName = mappings.methodFromSrg(SCREEN, "func_146283_a", THREE);
        MethodNode three = method(node, methodName, THREE);
        String fourDescriptor = "(Ljava/util/List;IIL" + font + ";)V";
        MethodNode four = method(node, "drawHoveringText", fourDescriptor);
        if (three == null || four == null) return;
        if (node.fields.stream().anyMatch(field -> field.name.equals(FONT_STATE))) {
            if (Arrays.stream(four.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(BRIDGE) && call.name.equals("fontToken"))) return;
            throw unsupported("reserved font state field");
        }
        String fontField = mappings.fieldFromSrg(SCREEN, "field_146289_q", "L" + FONT + ";");
        String width = mappings.fieldFromSrg(SCREEN, "field_146294_l", "I");
        String height = mappings.fieldFromSrg(SCREEN, "field_146295_m", "I");
        String bodyDescriptor = "(Ljava/util/List;IIIIIL" + font + ";)V";
        validateWrappers(node, three, four, fontField, font, width, height, bodyDescriptor);
        byte[] bytes = launcher.getClassByteArray(UTILS.replace('/', '.'), false);
        if (bytes == null || bytes.length > 16 * 1024 * 1024) throw unsupported("missing trusted GuiUtils class");
        ClassNode utils = new ClassNode();
        new ClassReader(bytes).accept(utils, ClassReader.EXPAND_FRAMES);
        if (!utils.name.equals(UTILS)) throw unsupported("GuiUtils runtime namespace");
        MethodNode source = method(utils, "drawHoveringText", bodyDescriptor);
        if (source == null || (source.access & Opcodes.ACC_STATIC) == 0) throw unsupported("GuiUtils renderer");
        MethodNode body = new MethodNode();
        source.accept(body);
        remapBody(node, utils, body, font, fontField, width, height);
        MethodNode wrapper = fontWrapper(node.name, methodName, font);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, FONT_STATE, "Ljava/lang/Object;", null, null));
        copyCode(body, three);
        copyCode(wrapper, four);
    }

    private static void validateWrappers(ClassNode node, MethodNode three, MethodNode four, String fontField,
                                         String font, String width, String height, String bodyDescriptor) {
        List<AbstractInsnNode> small = instructions(three), custom = instructions(four);
        if (small.size() != 8 || !load(small.get(0), Opcodes.ALOAD, 0) || !load(small.get(1), Opcodes.ALOAD, 1)
            || !load(small.get(2), Opcodes.ILOAD, 2) || !load(small.get(3), Opcodes.ILOAD, 3)
            || !load(small.get(4), Opcodes.ALOAD, 0) || !field(small.get(5), node.name, fontField, "L" + font + ";")
            || !(small.get(6) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
            || !call.owner.equals(node.name) || !call.name.equals(four.name) || !call.desc.equals(four.desc)
            || small.get(7).getOpcode() != Opcodes.RETURN || custom.size() != 11
            || !load(custom.get(0), Opcodes.ALOAD, 1) || !load(custom.get(1), Opcodes.ILOAD, 2)
            || !load(custom.get(2), Opcodes.ILOAD, 3) || !load(custom.get(3), Opcodes.ALOAD, 0)
            || !field(custom.get(4), node.name, width, "I") || !load(custom.get(5), Opcodes.ALOAD, 0)
            || !field(custom.get(6), node.name, height, "I") || custom.get(7).getOpcode() != Opcodes.ICONST_M1
            || !load(custom.get(8), Opcodes.ALOAD, 4) || !(custom.get(9) instanceof MethodInsnNode draw)
            || draw.getOpcode() != Opcodes.INVOKESTATIC || !draw.owner.equals(UTILS)
            || !draw.name.equals("drawHoveringText") || !draw.desc.equals(bodyDescriptor)
            || custom.get(10).getOpcode() != Opcodes.RETURN) throw unsupported("GuiScreen wrappers");
    }

    private static void remapBody(ClassNode screen, ClassNode utils, MethodNode body, String font, String fontField,
                                  String width, String height) {
        boolean backgroundStored = false;
        int constants = 0;
        for (AbstractInsnNode instruction : body.instructions.toArray()) {
            if (instruction instanceof VarInsnNode variable) {
                if (variable.var >= 3 && variable.var <= 6) {
                    InsnList replacement = new InsnList();
                    if (variable.var == 5 && variable.getOpcode() == Opcodes.ILOAD) replacement.add(new InsnNode(Opcodes.ICONST_M1));
                    else if ((variable.var == 3 || variable.var == 4) && variable.getOpcode() == Opcodes.ILOAD) {
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, screen.name, variable.var == 3 ? width : height, "I"));
                    } else if (variable.var == 6 && variable.getOpcode() == Opcodes.ALOAD) {
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, screen.name, FONT_STATE, "Ljava/lang/Object;"));
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, screen.name, fontField, "L" + font + ";"));
                        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "selectFont", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
                        replacement.add(new TypeInsnNode(Opcodes.CHECKCAST, font));
                    } else throw unsupported("mutated renderer argument");
                    body.instructions.insertBefore(variable, replacement);
                    body.instructions.remove(variable);
                } else variable.var = slot(variable.var);
            } else if (instruction instanceof IincInsnNode increment) increment.var = slot(increment.var);
            else if (instruction instanceof FrameNode frame) {
                if (frame.type != Opcodes.F_NEW) throw unsupported("unexpanded renderer frame");
                List<Object> locals = new ArrayList<>();
                locals.add(screen.name);
                for (int i = 0; i < frame.local.size(); i++) {
                    Object type = frame.local.get(i);
                    if (type.equals(Opcodes.LONG) || type.equals(Opcodes.DOUBLE)) throw unsupported("wide renderer local");
                    if (i >= 3 && i <= 6) continue;
                    int index = slot(i);
                    while (locals.size() <= index) locals.add(Opcodes.TOP);
                    locals.set(index, type);
                }
                frame.local = locals;
            } else if (instruction instanceof LdcInsnNode constant && Integer.valueOf(BACKGROUND).equals(constant.cst)) {
                constants++;
                if (!backgroundStored) {
                    AbstractInsnNode next = next(constant);
                    if (!load(next, Opcodes.ISTORE, 14)) throw unsupported("tooltip background local");
                    backgroundStored = true;
                } else body.instructions.set(constant, new VarInsnNode(Opcodes.ILOAD, slot(14)));
            } else if (instruction instanceof MethodInsnNode call && call.owner.equals(UTILS)) {
                MethodNode referenced = method(utils, call.name, call.desc);
                if (referenced == null || (referenced.access & Opcodes.ACC_PUBLIC) == 0) throw unsupported("non-public GuiUtils helper");
            }
        }
        if (constants < 2 || !backgroundStored) throw unsupported("tooltip background constants");
        if (body.localVariables != null) {
            body.localVariables.removeIf(local -> local.index >= 3 && local.index <= 6);
            for (LocalVariableNode local : body.localVariables) local.index = slot(local.index);
        } else body.localVariables = new ArrayList<>();
        remapAnnotations(body.visibleLocalVariableAnnotations);
        remapAnnotations(body.invisibleLocalVariableAnnotations);
        LabelNode start = new LabelNode(), end = new LabelNode();
        body.instructions.insert(start); body.instructions.add(end);
        body.localVariables.add(new LocalVariableNode("this", "L" + screen.name + ";", null, start, end, 0));
        body.maxLocals = Math.max(4, body.maxLocals - 3);
        body.maxStack += 2;
    }

    private static int slot(int original) {
        return switch (original) {
            case 0 -> 1; case 1 -> 2; case 2 -> 3; case 7 -> 4; case 10 -> 5; case 11 -> 6;
            case 8 -> 8; case 9 -> 9; case 12 -> 7;
            default -> {
                if (original < 13) throw unsupported("renderer local layout");
                yield original - 3;
            }
        };
    }

    private static void remapAnnotations(List<LocalVariableAnnotationNode> annotations) {
        if (annotations == null) return;
        for (LocalVariableAnnotationNode annotation : annotations) for (int i = annotation.index.size() - 1; i >= 0; i--) {
            int original = annotation.index.get(i);
            if (original >= 3 && original <= 6) {
                annotation.index.remove(i); annotation.start.remove(i); annotation.end.remove(i);
            } else annotation.index.set(i, slot(original));
        }
        annotations.removeIf(annotation -> annotation.index.isEmpty());
    }

    private static MethodNode fontWrapper(String screen, String name, String font) {
        MethodNode method = new MethodNode();
        method.tryCatchBlocks = new ArrayList<>();
        LabelNode start = new LabelNode(), end = new LabelNode(), failure = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, screen, FONT_STATE, "Ljava/lang/Object;"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "fontToken", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, screen, FONT_STATE, "Ljava/lang/Object;"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, screen, name, THREE, false));
        method.instructions.add(end);
        restoreFont(method.instructions, screen);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(failure);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, 6,
            new Object[]{screen, "java/util/List", Opcodes.INTEGER, Opcodes.INTEGER, font, "java/lang/Object"}, 1, new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 6));
        restoreFont(method.instructions, screen);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, failure, "java/lang/Throwable"));
        method.maxStack = 4; method.maxLocals = 7;
        return method;
    }

    private static void restoreFont(InsnList code, String screen) {
        code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(new VarInsnNode(Opcodes.ALOAD, 5));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, screen, FONT_STATE, "Ljava/lang/Object;"));
    }
    private static void copyCode(MethodNode source, MethodNode target) {
        target.instructions = source.instructions;
        target.tryCatchBlocks = source.tryCatchBlocks == null ? new ArrayList<>() : source.tryCatchBlocks;
        target.localVariables = source.localVariables; target.visibleLocalVariableAnnotations = source.visibleLocalVariableAnnotations;
        target.invisibleLocalVariableAnnotations = source.invisibleLocalVariableAnnotations;
        target.maxStack = source.maxStack; target.maxLocals = source.maxLocals;
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static List<AbstractInsnNode> instructions(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
    private static AbstractInsnNode next(AbstractInsnNode value) { do { value = value.getNext(); } while (value != null && value.getOpcode() < 0); return value; }
    private static boolean load(AbstractInsnNode value, int opcode, int slot) { return value instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot; }
    private static boolean field(AbstractInsnNode value, String owner, String name, String descriptor) { return value instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor); }
    private static IllegalStateException unsupported(String detail) { return new IllegalStateException("Unsupported Forge tooltip bytecode: " + detail); }
}
