package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class HostCreativeTabCompatibility implements MixinAnnotationAdjuster {
    private static final String MIXIN = "dev.rdh.argentum.mixin.features.hud.CreativeInventoryScreenMixin";
    private static final String PREFIX = "textures/gui/container/creative_inventory/tab_";
    private static final String SLICE = "arcadium$creativePanel";
    private static final String CALLBACK = "(FIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    @FunctionalInterface interface TargetReader { byte[] read(String name) throws IOException; }
    private final String creative, render, textures, bind, resource, tabs, background;
    private final TargetReader reader;
    private volatile Integer ordinal;

    public HostCreativeTabCompatibility(ForgeMappings mappings) {
        this(mappings, name -> FabricLauncherBase.getLauncher().getClassByteArray(name.replace('/', '.'), false));
    }
    HostCreativeTabCompatibility(ForgeMappings mappings, TargetReader reader) {
        creative = mappings.classFromSrg("net/minecraft/client/gui/inventory/GuiContainerCreative");
        render = mappings.methodFromSrg("net/minecraft/client/gui/inventory/GuiContainerCreative", "func_146976_a", "(FII)V");
        textures = mappings.classFromSrg("net/minecraft/client/renderer/texture/TextureManager");
        resource = mappings.classFromSrg("net/minecraft/util/ResourceLocation");
        bind = mappings.methodFromSrg("net/minecraft/client/renderer/texture/TextureManager", "func_110577_a", "(Lnet/minecraft/util/ResourceLocation;)V");
        tabs = mappings.classFromSrg("net/minecraft/creativetab/CreativeTabs");
        background = mappings.methodFromSrg("net/minecraft/creativetab/CreativeTabs", "func_78015_f", "()Ljava/lang/String;");
        this.reader = reader;
    }

    @Override public AdjustableAnnotationNode adjust(List<String> targets, String mixin, MethodNode handler, AdjustableAnnotationNode annotation) {
        if (!mixin.replace('/', '.').equals(MIXIN) || targets.size() != 1 || !targets.get(0).replace('.', '/').equals(creative)
            || !handler.name.equals("argentum$drawTabBatch") || !annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return annotation;
        if (!handler.desc.equals(CALLBACK) || (handler.access & Opcodes.ACC_STATIC) != 0 || !List.of(render).equals(value(annotation, "method"))) throw unsupported("handler or method selector");
        List<?> points = value(annotation, "at") instanceof List<?> list ? list : List.of();
        if (points.size() != 1 || !(points.get(0) instanceof AnnotationNode point) || !"INVOKE".equals(value(point, "value"))
            || !("L" + textures + ";" + bind + "(L" + resource + ";)V").equals(value(point, "target")) || value(point, "shift") != null) throw unsupported("texture selector");
        Object slices = value(annotation, "slice");
        if (Integer.valueOf(0).equals(value(point, "ordinal")) && SLICE.equals(value(point, "slice"))) {
            if (!canonicalSlice(slices)) throw unsupported("installed slice");
            return annotation;
        }
        if (!Integer.valueOf(1).equals(value(point, "ordinal")) || value(point, "slice") != null || slices != null) throw unsupported("original ordinal or slice");
        List<AbstractInsnNode> body = Arrays.stream(handler.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        if (body.size() < 3 || !(body.get(0) instanceof VarInsnNode receiver) || receiver.getOpcode() != Opcodes.ALOAD || receiver.var != 0
            || body.get(1).getOpcode() != Opcodes.ICONST_0 || !(body.get(2) instanceof FieldInsnNode flag) || flag.getOpcode() != Opcodes.PUTFIELD
            || !flag.name.equals("argentum$batchingTabs") || !flag.desc.equals("Z")
            || body.stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("dev/rdh/argentum/api/render/gui/hud/HudBatch$Textured") && call.name.equals("draw") && call.desc.equals("()V")).count() != 1) throw unsupported("batch flush body");
        validatedOrdinal();
        AnnotationNode from = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        from.values = new ArrayList<>(List.of("value", "CONSTANT", "args", new ArrayList<>(List.of("stringValue=" + PREFIX)), "ordinal", 0));
        AnnotationNode slice = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Slice;");
        slice.values = new ArrayList<>(List.of("id", SLICE, "from", from));
        AnnotationNode replacement = new AnnotationNode(point.desc); point.accept(replacement); put(replacement, "ordinal", 0); put(replacement, "slice", SLICE);
        annotation.set("at", new ArrayList<>(List.of(replacement))); annotation.set("slice", new ArrayList<>(List.of(slice)));
        return annotation;
    }

    int validatedOrdinal() {
        Integer found = ordinal;
        if (found == null) synchronized (this) {
            found = ordinal;
            if (found == null) {
                try {
                    byte[] bytes = reader.read(creative); if (bytes == null) throw unsupported("missing raw creative screen");
                    ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); found = ordinal = panelOrdinal(node);
                } catch (IOException error) { throw new IllegalStateException("The Forge creative panel bytecode could not be read", error); }
            }
        }
        return found;
    }

    int panelOrdinal(ClassNode node) {
        if (!node.name.equals(creative)) throw unsupported("creative class identity");
        MethodNode method = node.methods.stream().filter(candidate -> candidate.name.equals(render) && candidate.desc.equals("(FII)V")).findFirst().orElseThrow(() -> unsupported("creative render method"));
        List<AbstractInsnNode> code = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        List<LdcInsnNode> constants = code.stream().filter(instruction -> instruction instanceof LdcInsnNode constant && PREFIX.equals(constant.cst)).map(LdcInsnNode.class::cast).toList();
        if (constants.size() != 1 || !method.tryCatchBlocks.isEmpty() || code.stream().noneMatch(instruction -> instruction.getOpcode() == Opcodes.RETURN)) throw unsupported("panel texture prefix or exits");
        int at = code.indexOf(constants.get(0));
        if (at + 7 >= code.size() || !call(code.get(at + 1), Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            || !(code.get(at + 2) instanceof VarInsnNode selected) || selected.getOpcode() != Opcodes.ALOAD
            || !call(code.get(at + 3), Opcodes.INVOKEVIRTUAL, tabs, background, "()Ljava/lang/String;")
            || !call(code.get(at + 4), Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            || !call(code.get(at + 5), Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
            || !call(code.get(at + 6), Opcodes.INVOKESPECIAL, resource, "<init>", "(Ljava/lang/String;)V") || !textureBind(code.get(at + 7))) throw unsupported("main panel texture binding");
        AbstractInsnNode panel = code.get(at + 7); List<AbstractInsnNode> all = Arrays.asList(method.instructions.toArray());
        int target = all.indexOf(panel);
        if (reachable(all, 0, target, -1, true) || reachable(all, target + 1, -1, target, false)) throw unsupported("panel control flow");
        int ordinal = 0; for (int i = 0; i < at + 7; i++) if (textureBind(code.get(i))) ordinal++;
        return ordinal;
    }

    private static boolean reachable(List<AbstractInsnNode> code, int start, int avoid, int target, boolean normalReturn) {
        Map<AbstractInsnNode, Integer> indexes = new IdentityHashMap<>(); for (int i = 0; i < code.size(); i++) indexes.put(code.get(i), i);
        ArrayDeque<Integer> queue = new ArrayDeque<>(); BitSet visited = new BitSet(); queue.add(start);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst(); if (index < 0 || index >= code.size() || index == avoid || visited.get(index)) continue; visited.set(index);
            AbstractInsnNode instruction = code.get(index); int opcode = instruction.getOpcode();
            if (index == target || normalReturn && opcode == Opcodes.RETURN) return true;
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET) throw unsupported("legacy subroutine");
            if (instruction instanceof JumpInsnNode jump) { queue.add(indexes.get(jump.label)); if (opcode != Opcodes.GOTO) queue.add(index + 1); }
            else if (instruction instanceof LookupSwitchInsnNode branch) { queue.add(indexes.get(branch.dflt)); for (LabelNode label : branch.labels) queue.add(indexes.get(label)); }
            else if (instruction instanceof TableSwitchInsnNode branch) { queue.add(indexes.get(branch.dflt)); for (LabelNode label : branch.labels) queue.add(indexes.get(label)); }
            else if (opcode != Opcodes.ATHROW && (opcode < Opcodes.IRETURN || opcode > Opcodes.RETURN)) queue.add(index + 1);
        }
        return false;
    }
    private static boolean canonicalSlice(Object raw) {
        if (!(raw instanceof List<?> list) || list.size() != 1 || !(list.get(0) instanceof AnnotationNode slice) || !SLICE.equals(value(slice, "id")) || value(slice, "to") != null) return false;
        return value(slice, "from") instanceof AnnotationNode from && "CONSTANT".equals(value(from, "value")) && List.of("stringValue=" + PREFIX).equals(value(from, "args")) && Integer.valueOf(0).equals(value(from, "ordinal"));
    }
    private boolean textureBind(AbstractInsnNode instruction) { return call(instruction, Opcodes.INVOKEVIRTUAL, textures, bind, "(L" + resource + ";)V"); }
    private static boolean call(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) { return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor) && !call.itf; }
    private static Object value(AnnotationNode node, String key) { if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) return node.values.get(i + 1); return null; }
    private static void put(AnnotationNode node, String key, Object value) { if (node.values == null) node.values = new ArrayList<>(); for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) { node.values.set(i + 1, value); return; } node.values.add(key); node.values.add(value); }
    private static IllegalStateException unsupported(String detail) { return new IllegalStateException("Unsupported creative tab batching: " + detail); }
}
