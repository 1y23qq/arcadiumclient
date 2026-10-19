package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public final class HostHeadRotationCompatibility {
    private static final String RENDERER = "net/minecraft/client/renderer/entity/RendererLivingEntity";
    private static final String DESCRIPTOR = "(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V";

    private HostHeadRotationCompatibility() { }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(mappings.classFromSrg(RENDERER))) return;
        String name = mappings.methodFromSrg(RENDERER, "func_76986_a", DESCRIPTOR);
        String descriptor = mappings.descriptorFromSrg(DESCRIPTOR);
        MethodNode target = node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
        if (target == null) return;
        List<FrameNode> frames = frames(target);
        if (frames.stream().allMatch(frame -> frame.type == Opcodes.F_FULL)) return;
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        ClassNode expanded = new ClassNode(); new ClassReader(writer.toByteArray()).accept(expanded, ClassReader.EXPAND_FRAMES);
        MethodNode source = expanded.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow();
        List<FrameNode> fullFrames = frames(source);
        if (frames.size() != fullFrames.size()) throw new IllegalStateException("The living renderer stack frame count changed while expanding its metadata.");
        Map<Integer, LabelNode> originalLabels = new HashMap<>();
        int position = 0;
        for (AbstractInsnNode instruction : target.instructions) {
            if (instruction instanceof LabelNode label) originalLabels.putIfAbsent(position, label);
            if (instruction.getOpcode() >= 0) position++;
        }
        Map<LabelNode, LabelNode> labels = new HashMap<>(); position = 0;
        for (AbstractInsnNode instruction : source.instructions) {
            if (instruction instanceof LabelNode label) {
                LabelNode original = originalLabels.get(position);
                if (original == null) throw new IllegalStateException("A living renderer stack frame refers to a new bytecode location.");
                labels.put(label, original);
            }
            if (instruction.getOpcode() >= 0) position++;
        }
        for (int index = 0; index < frames.size(); index++) {
            FrameNode frame = (FrameNode) fullFrames.get(index).clone(labels);
            frame.type = Opcodes.F_FULL;
            target.instructions.set(frames.get(index), frame);
        }
    }

    private static List<FrameNode> frames(MethodNode method) {
        List<FrameNode> frames = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof FrameNode frame) frames.add(frame);
        return frames;
    }
}
