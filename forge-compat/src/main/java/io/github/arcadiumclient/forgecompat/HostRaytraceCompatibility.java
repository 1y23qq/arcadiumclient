package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostRaytraceCompatibility {
    private static final String WORLD = "net/minecraft/world/World";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String RAY = "(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;";
    private static final String COLLIDE = "(Lnet/minecraft/block/state/IBlockState;Z)Z";

    private HostRaytraceCompatibility() { }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(mappings.classFromSrg(WORLD))) return;
        String name = mappings.methodFromSrg(WORLD, "func_147447_a", RAY);
        String descriptor = mappings.srgToRuntime().mapMethodDesc(RAY);
        MethodNode original = method(node, name, descriptor);
        if (original == null) return;
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        ClassNode expanded = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(expanded, ClassReader.EXPAND_FRAMES);
        MethodNode ray = method(expanded, name, descriptor);
        String block = mappings.classFromSrg(BLOCK);
        String collision = mappings.methodFromSrg(BLOCK, "func_176209_a", COLLIDE);
        String collisionDescriptor = mappings.srgToRuntime().mapMethodDesc(COLLIDE);
        List<MethodInsnNode> calls = Arrays.stream(ray.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(block) && call.name.equals(collision)
            && call.desc.equals(collisionDescriptor)).map(instruction -> (MethodInsnNode) instruction).toList();
        if (calls.size() != 2) throw unsupported("collision-call count");
        int stale = receiver(calls.get(0)), live = receiver(calls.get(1));
        if (stale < 6 || stale == live) return;
        int second = ray.instructions.indexOf(calls.get(1));
        if (!block.equals(frameType(ray, second, stale)) && (ray.localVariables == null || ray.localVariables.stream().noneMatch(local ->
            local.index == stale && local.desc.equals("L" + block + ";") && ray.instructions.indexOf(local.start) <= second
                && ray.instructions.indexOf(local.end) > second))) return;
        Graph graph = graph(ray);
        BitSet component = reachable(graph.forward, second);
        component.and(reachable(graph.reverse, second));
        int header = component.nextSetBit(0);
        if (header < 0 || component.cardinality() < 2 || !(graph.nodes[header] instanceof LabelNode boundary)) throw unsupported("raytrace loop header");
        BitSet afterHeader = reachable(graph.forward, header);
        for (int index = afterHeader.nextSetBit(0); index >= 0; index = afterHeader.nextSetBit(index + 1)) {
            AbstractInsnNode instruction = graph.nodes[index];
            if (instruction instanceof VarInsnNode variable && variable.var == stale
                || instruction instanceof IincInsnNode increment && increment.var == stale) throw unsupported("initial block local is live after the loop header");
        }
        for (int index = afterHeader.nextSetBit(0); index >= 0; index = afterHeader.nextSetBit(index + 1)) {
            if (graph.nodes[index] instanceof FrameNode frame) {
                int entry = frameEntry(frame, stale);
                if (entry >= 0 && block.equals(frame.local.get(entry))) frame.local.set(entry, "java/lang/Object");
            }
        }
        if (ray.localVariables != null) ray.localVariables.removeIf(local -> {
            if (local.index != stale || !local.desc.equals("L" + block + ";") || ray.instructions.indexOf(local.end) <= header) return false;
            if (ray.instructions.indexOf(local.start) >= header) return true;
            local.end = boundary;
            return false;
        });
        narrowAnnotations(ray.visibleLocalVariableAnnotations, ray, stale, header, boundary);
        narrowAnnotations(ray.invisibleLocalVariableAnnotations, ray, stale, header, boundary);
        AbstractInsnNode first = boundary;
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (first == null) throw unsupported("empty raytrace loop");
        InsnList clear = new InsnList(); clear.add(new InsnNode(Opcodes.ACONST_NULL)); clear.add(new VarInsnNode(Opcodes.ASTORE, stale));
        ray.instructions.insertBefore(first, clear);
        original.instructions = ray.instructions; original.tryCatchBlocks = ray.tryCatchBlocks;
        original.localVariables = ray.localVariables; original.visibleLocalVariableAnnotations = ray.visibleLocalVariableAnnotations;
        original.invisibleLocalVariableAnnotations = ray.invisibleLocalVariableAnnotations;
        original.maxStack = Math.max(1, ray.maxStack); original.maxLocals = ray.maxLocals;
    }

    private static int receiver(MethodInsnNode call) {
        AbstractInsnNode flag = previous(call), state = previous(flag), receiver = previous(state);
        if (!(flag instanceof VarInsnNode booleanArgument) || flag.getOpcode() != Opcodes.ILOAD
            || !(state instanceof VarInsnNode) || state.getOpcode() != Opcodes.ALOAD
            || !(receiver instanceof VarInsnNode block) || receiver.getOpcode() != Opcodes.ALOAD || call.itf) throw unsupported("collision receiver shape");
        return block.var;
    }

    private static Object frameType(MethodNode method, int instruction, int slot) {
        for (int i = instruction; i >= 0; i--) if (method.instructions.get(i) instanceof FrameNode frame) {
            int entry = frameEntry(frame, slot); return entry < 0 ? Opcodes.TOP : frame.local.get(entry);
        }
        return Opcodes.TOP;
    }

    private static int frameEntry(FrameNode frame, int slot) {
        if (frame.type != Opcodes.F_NEW) throw unsupported("unexpanded raytrace frame");
        for (int i = 0, current = 0; i < frame.local.size(); i++) {
            if (current == slot) return i;
            Object type = frame.local.get(i); current += type.equals(Opcodes.LONG) || type.equals(Opcodes.DOUBLE) ? 2 : 1;
            if (current > slot) return -1;
        }
        return -1;
    }

    private record Graph(AbstractInsnNode[] nodes, List<List<Integer>> forward, List<List<Integer>> reverse) { }

    private static Graph graph(MethodNode method) {
        AbstractInsnNode[] nodes = method.instructions.toArray();
        if (nodes.length > 100_000) throw unsupported("oversized raytrace method");
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
        List<List<Integer>> forward = new ArrayList<>(), reverse = new ArrayList<>();
        for (int i = 0; i < nodes.length; i++) { indices.put(nodes[i], i); forward.add(new ArrayList<>()); reverse.add(new ArrayList<>()); }
        for (int i = 0; i < nodes.length; i++) {
            AbstractInsnNode instruction = nodes[i]; int opcode = instruction.getOpcode();
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET) throw unsupported("legacy raytrace subroutine");
            if (instruction instanceof JumpInsnNode jump) {
                edge(forward, reverse, i, indices.get(jump.label));
                if (opcode == Opcodes.GOTO) continue;
            } else if (instruction instanceof LookupSwitchInsnNode branch) {
                edge(forward, reverse, i, indices.get(branch.dflt));
                for (LabelNode target : branch.labels) edge(forward, reverse, i, indices.get(target));
                continue;
            } else if (instruction instanceof TableSwitchInsnNode branch) {
                edge(forward, reverse, i, indices.get(branch.dflt));
                for (LabelNode target : branch.labels) edge(forward, reverse, i, indices.get(target));
                continue;
            }
            if (opcode != Opcodes.ATHROW && (opcode < Opcodes.IRETURN || opcode > Opcodes.RETURN) && i + 1 < nodes.length) edge(forward, reverse, i, i + 1);
        }
        if (method.tryCatchBlocks != null) for (var block : method.tryCatchBlocks) {
            int start = indices.get(block.start), end = indices.get(block.end), handler = indices.get(block.handler);
            for (int i = start; i < end; i++) edge(forward, reverse, i, handler);
        }
        return new Graph(nodes, forward, reverse);
    }

    private static void edge(List<List<Integer>> forward, List<List<Integer>> reverse, int from, int to) {
        if (!forward.get(from).contains(to)) { forward.get(from).add(to); reverse.get(to).add(from); }
    }
    private static BitSet reachable(List<List<Integer>> graph, int initial) {
        BitSet visited = new BitSet(graph.size()); ArrayDeque<Integer> pending = new ArrayDeque<>(); pending.add(initial);
        while (!pending.isEmpty()) {
            int next = pending.removeFirst(); if (visited.get(next)) continue; visited.set(next); pending.addAll(graph.get(next));
        }
        return visited;
    }
    private static void narrowAnnotations(List<LocalVariableAnnotationNode> annotations, MethodNode method, int slot, int header, LabelNode boundary) {
        if (annotations == null) return;
        for (var annotation : annotations) for (int i = annotation.index.size() - 1; i >= 0; i--) {
            if (annotation.index.get(i) != slot || method.instructions.indexOf(annotation.end.get(i)) <= header) continue;
            if (method.instructions.indexOf(annotation.start.get(i)) >= header) {
                annotation.index.remove(i); annotation.start.remove(i); annotation.end.remove(i);
            } else annotation.end.set(i, boundary);
        }
        annotations.removeIf(annotation -> annotation.index.isEmpty());
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) { return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null); }
    private static AbstractInsnNode previous(AbstractInsnNode value) { if (value == null) return null; do { value = value.getPrevious(); } while (value != null && value.getOpcode() < 0); return value; }
    private static IllegalStateException unsupported(String detail) { return new IllegalStateException("Unsupported Forge raytrace locals: " + detail); }
}
