package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.adjuster.tools.type.RemappableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class HostCameraCompatibility implements MixinAnnotationAdjuster {
    private static final String MIXIN = "org.codeberg.chromatic.freelook.mixin.legacy.MixinGameRenderer";
    private static final String CAMERA = "org.codeberg.chromatic.freelook.mixin.MixinCamera";
    private static final String WORLD = "org.codeberg.chromatic.freelook.mixin.legacy.MixinWorldRenderer";
    private static final String HURT = "net.uku3lig.betterhurtcam.mixin.GameRendererMixin";
    private static final String CONTROLLER = "org/codeberg/chromatic/freelook/handler/FreelookHandler";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private record Rule(String target, List<String> methods, String receiver, boolean isStatic, String field, String controller) { }
    private final String renderer, entity, hurt, rotate;
    private final Map<String, Map<String, Rule>> groups;

    public HostCameraCompatibility(ForgeMappings mappings) {
        renderer = mappings.classFromSrg("net/minecraft/client/renderer/EntityRenderer");
        entity = mappings.classFromSrg("net/minecraft/entity/Entity");
        List<String> orient = List.of(mappings.methodFromSrg("net/minecraft/client/renderer/EntityRenderer", "func_78467_g", "(F)V"));
        String player = mappings.classFromSrg("net/minecraft/entity/player/EntityPlayer"), camera = mappings.classFromSrg("net/minecraft/client/renderer/ActiveRenderInfo");
        List<String> update = List.of(mappings.methodFromSrg("net/minecraft/client/renderer/ActiveRenderInfo", "func_74583_a", "(Lnet/minecraft/entity/player/EntityPlayer;Z)V"));
        String world = mappings.classFromSrg("net/minecraft/client/renderer/RenderGlobal");
        List<String> terrain = List.of(mappings.methodFromSrg("net/minecraft/client/renderer/RenderGlobal", "func_174970_a", "(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V"),
            mappings.methodFromSrg("net/minecraft/client/renderer/RenderGlobal", "func_174962_a", "(Lnet/minecraft/entity/Entity;D)Lorg/lwjgl/util/vector/Vector3f;"));
        groups = Map.of(MIXIN, Map.of(
            "modifyYaw", rule(mappings, renderer, orient, entity, false, "field_70177_z", "cameraYaw"),
            "modifyLastYaw", rule(mappings, renderer, orient, entity, false, "field_70126_B", "cameraYaw"),
            "modifyPitch", rule(mappings, renderer, orient, entity, false, "field_70125_A", "cameraPitch"),
            "modifyLastPitch", rule(mappings, renderer, orient, entity, false, "field_70127_C", "cameraPitch")),
            CAMERA, Map.of("modifyYaw", rule(mappings, camera, update, player, true, "field_70177_z", "cameraYaw"),
                "modifyPitch", rule(mappings, camera, update, player, true, "field_70125_A", "cameraPitch")),
            WORLD, Map.of("modifyYaw", rule(mappings, world, terrain, entity, false, "field_70177_z", "cameraYaw"),
                "modifyPitch", rule(mappings, world, terrain, entity, false, "field_70125_A", "cameraPitch")));
        hurt = mappings.methodFromSrg("net/minecraft/client/renderer/EntityRenderer", "func_78482_e", "(F)V");
        rotate = "L" + mappings.classFromSrg("net/minecraft/client/renderer/GlStateManager") + ";"
            + mappings.methodFromSrg("net/minecraft/client/renderer/GlStateManager", "func_179114_b", "(FFFF)V") + "(FFFF)V";
    }

    @Override public AdjustableAnnotationNode adjust(List<String> targets, String mixin, MethodNode handler, AdjustableAnnotationNode annotation) {
        String source = mixin.replace('/', '.');
        if (source.equals(HURT)) return adjustHurt(targets, mixin, handler, annotation);
        Map<String, Rule> rules = groups.get(source); if (rules == null) return annotation;
        Rule rule = rules.get(handler.name);
        if (rule == null || !annotation.desc.equals(REDIRECT) && !annotation.desc.equals(WRAP)) return annotation;
        if (targets.size() != 1 || !targets.get(0).replace('.', '/').equals(rule.target)) return annotation;
        if (((handler.access & Opcodes.ACC_STATIC) != 0) != rule.isStatic || !rule.methods.equals(value(annotation, "method"))) throw unsupported(handler);
        String originalDescriptor = "(L" + rule.receiver + ";)F", wrappedDescriptor = "(L" + rule.receiver + ";L" + OPERATION + ";)F";
        int receiver = rule.isStatic ? 0 : 1, operation = receiver + 1;
        Object raw = value(annotation, "at"); List<?> points = raw instanceof List<?> list ? list : raw instanceof AnnotationNode at ? List.of(at) : List.of();
        if (points.size() != 1 || !(points.get(0) instanceof AnnotationNode at) || !"FIELD".equals(value(at, "value"))
            || !("L" + rule.receiver + ";" + rule.field + ":F").equals(value(at, "target")) || !Integer.valueOf(Opcodes.GETFIELD).equals(value(at, "opcode"))) throw unsupported(handler);
        List<AbstractInsnNode> body = Arrays.stream(handler.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        if (annotation.desc.equals(WRAP)) {
            if (!handler.desc.equals(wrappedDescriptor) || body.stream().filter(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(OPERATION) && call.name.equals("call")).count() != 1) throw unsupported(handler);
            synchronize(mixin, handler, annotation);
            return annotation;
        }
        if (!handler.desc.equals(originalDescriptor) || body.size() != 6
            || !(body.get(0) instanceof FieldInsnNode singleton) || singleton.getOpcode() != Opcodes.GETSTATIC || !singleton.owner.equals(CONTROLLER)
            || !singleton.name.equals("INSTANCE") || !singleton.desc.equals("L" + CONTROLLER + ";") || !load(body.get(1), receiver) || !load(body.get(2), receiver)
            || !(body.get(3) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD || !field.owner.equals(rule.receiver)
            || !field.name.equals(rule.field) || !field.desc.equals("F") || !(body.get(4) instanceof MethodInsnNode call)
            || call.getOpcode() != Opcodes.INVOKEVIRTUAL || call.itf || !call.owner.equals(CONTROLLER) || !call.name.equals(rule.controller)
            || !call.desc.equals("(L" + entity + ";F)F") || body.get(5).getOpcode() != Opcodes.FRETURN) throw unsupported(handler);
        AdjustableAnnotationNode wrapped = wrap(annotation, points);
        InsnList fallback = new InsnList(); fallback.add(new VarInsnNode(Opcodes.ALOAD, operation)); fallback.add(new InsnNode(Opcodes.ICONST_1));
        fallback.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object")); fallback.add(new InsnNode(Opcodes.DUP));
        fallback.add(new InsnNode(Opcodes.ICONST_0)); fallback.add(new VarInsnNode(Opcodes.ALOAD, receiver)); fallback.add(new InsnNode(Opcodes.AASTORE));
        fallback.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        fallback.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
        fallback.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
        handler.instructions.insertBefore(body.get(2), fallback); handler.instructions.remove(body.get(2)); handler.instructions.remove(body.get(3));
        handler.desc = wrappedDescriptor; handler.maxLocals = Math.max(handler.maxLocals, operation + 1); handler.maxStack = Math.max(handler.maxStack, 7);
        if (handler.parameters != null) handler.parameters.add(new ParameterNode("original", 0));
        synchronize(mixin, handler, wrapped);
        return wrapped;
    }

    private AdjustableAnnotationNode adjustHurt(List<String> targets, String mixin, MethodNode handler, AdjustableAnnotationNode annotation) {
        if (targets.size() != 1 || !targets.get(0).replace('.', '/').equals(renderer) || !handler.name.equals("betterhurtcam$modifyHurtCam")
            || !annotation.desc.equals(REDIRECT) && !annotation.desc.equals(WRAP)) return annotation;
        if ((handler.access & Opcodes.ACC_STATIC) != 0 || !List.of(hurt).equals(value(annotation, "method"))) throw unsupported(handler);
        Object raw = value(annotation, "at"); List<?> points = raw instanceof List<?> list ? list : raw instanceof AnnotationNode at ? List.of(at) : List.of();
        if (points.size() != 1 || !(points.get(0) instanceof AnnotationNode at) || !"INVOKE".equals(value(at, "value")) || !rotate.equals(value(at, "target"))) throw unsupported(handler);
        List<AbstractInsnNode> body = Arrays.stream(handler.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        String wrapped = "(FFFFL" + OPERATION + ";)V";
        if (annotation.desc.equals(WRAP)) {
            if (!handler.desc.equals(wrapped) || body.stream().filter(i -> i instanceof MethodInsnNode call && call.owner.equals(OPERATION) && call.name.equals("call")).count() != 1) throw unsupported(handler);
            synchronize(mixin, handler, annotation); return annotation;
        }
        List<MethodInsnNode> rotations = body.stream().filter(i -> i instanceof MethodInsnNode call && ("L" + call.owner + ";" + call.name + call.desc).equals(rotate)).map(MethodInsnNode.class::cast).toList();
        if (!handler.desc.equals("(FFFF)V") || body.size() != 19 || rotations.size() != 1 || rotations.get(0).getOpcode() != Opcodes.INVOKESTATIC
            || body.get(17) != rotations.get(0) || body.get(18).getOpcode() != Opcodes.RETURN
            || !(body.get(4) instanceof MethodInsnNode type) || !type.owner.equals("net/uku3lig/betterhurtcam/BetterHurtCam") || !type.name.equals("getType")
            || !(body.get(11) instanceof MethodInsnNode multiplier) || !multiplier.owner.equals("net/uku3lig/betterhurtcam/BetterHurtCam") || !multiplier.name.equals("getMultiplier")) throw unsupported(handler);
        AdjustableAnnotationNode replacement = wrap(annotation, points); InsnList call = new InsnList();
        for (int slot = 4; slot >= 1; slot--) call.add(new VarInsnNode(Opcodes.FSTORE, slot));
        call.add(new VarInsnNode(Opcodes.ALOAD, 5)); call.add(new InsnNode(Opcodes.ICONST_4)); call.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        for (int slot = 1; slot <= 4; slot++) {
            call.add(new InsnNode(Opcodes.DUP)); call.add(new InsnNode(Opcodes.ICONST_0 + slot - 1)); call.add(new VarInsnNode(Opcodes.FLOAD, slot));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false)); call.add(new InsnNode(Opcodes.AASTORE));
        }
        call.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true)); call.add(new InsnNode(Opcodes.POP));
        handler.instructions.insertBefore(rotations.get(0), call); handler.instructions.remove(rotations.get(0));
        handler.desc = wrapped; handler.maxLocals = Math.max(handler.maxLocals, 6); handler.maxStack = Math.max(handler.maxStack, 5);
        if (handler.parameters != null) handler.parameters.add(new ParameterNode("original", 0)); synchronize(mixin, handler, replacement); return replacement;
    }

    private static AdjustableAnnotationNode wrap(AdjustableAnnotationNode annotation, List<?> points) {
        AnnotationNode replacement = new AnnotationNode(WRAP); annotation.accept(replacement); put(replacement, "at", new ArrayList<>(points));
        AdjustableAnnotationNode wrapped = AdjustableAnnotationNode.fromNode(replacement);
        if (annotation instanceof RemappableAnnotationNode source && wrapped instanceof RemappableAnnotationNode target) target.setRemapper(source.getRemapper());
        return wrapped;
    }

    private static void synchronize(String mixin, MethodNode handler, AnnotationNode annotation) {
        MethodNode copy = new MethodNode(handler.access, handler.name, handler.desc, handler.signature, handler.exceptions.toArray(String[]::new)); handler.accept(copy);
        if (copy.visibleAnnotations != null) for (int index = 0; index < copy.visibleAnnotations.size(); index++) {
            String descriptor = copy.visibleAnnotations.get(index).desc;
            if (descriptor.equals(REDIRECT) || descriptor.equals(WRAP)) { AnnotationNode value = new AnnotationNode(WRAP); annotation.accept(value); copy.visibleAnnotations.set(index, value); }
        }
        ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, mixin.replace('.', '/'), null, "java/lang/Object", null); node.methods.add(copy);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); HostClassMetadata.synchronize(node.name, writer.toByteArray());
    }

    private static String field(ForgeMappings mappings, String name) { return mappings.fieldFromSrg("net/minecraft/entity/Entity", name, "F"); }
    private static Rule rule(ForgeMappings mappings, String target, List<String> methods, String receiver, boolean isStatic, String field, String controller) { return new Rule(target, methods, receiver, isStatic, field(mappings, field), controller); }
    private static boolean load(AbstractInsnNode value, int slot) { return value instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == slot; }
    private static Object value(AnnotationNode node, String key) { if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) return node.values.get(i + 1); return null; }
    private static void put(AnnotationNode node, String key, Object value) { if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) { node.values.set(i + 1, value); return; } if (node.values == null) node.values = new ArrayList<>(); node.values.add(key); node.values.add(value); }
    private static IllegalStateException unsupported(MethodNode handler) { return new IllegalStateException("Unsupported native Freelook camera handler: " + handler.name + handler.desc); }
}
