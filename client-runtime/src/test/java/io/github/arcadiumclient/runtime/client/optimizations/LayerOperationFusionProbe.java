// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class LayerOperationFusionProbe implements Opcodes {
    private LayerOperationFusionProbe() { }
    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(Path.of(args[0])); ClassNode target = read(original);
        Map<String, String> fingerprints = LayerOperationFusion.audit(target); var sites = LayerOperationFusion.apply(target);
        require(sites.size() == 1, "Expected exactly one actual layer site: " + fingerprints);
        int verified = 0;
        for (MethodNode method : target.methods) if ((method.access & ACC_ABSTRACT) == 0) { new Analyzer<>(new BasicVerifier()).analyze(target.name, method); verified++; }
        MethodNode typed = target.methods.stream().filter(method -> method.name.equals("arcadium$typedLayerCapture")).findFirst().orElseThrow();
        require(typed.tryCatchBlocks.size() == 2 && typed.tryCatchBlocks.stream().allMatch(block -> block.type == null), "The native finally regions were not retained");
        require(typed.instructions.size() > 0 && Arrays.stream(typed.instructions.toArray()).noneMatch(node -> node instanceof MethodInsnNode call && (call.owner.endsWith("/Operation") || call.owner.equals("java/lang/Float") && call.name.equals("valueOf"))), "Typed layer method retained boxing or Operation calls");
        require(Arrays.stream(typed.instructions.toArray()).filter(node -> node instanceof MethodInsnNode call && call.getOpcode() == INVOKEINTERFACE).count() == 1, "Original virtual layer dispatch was not preserved");
        require(LayerOperationFusion.apply(target).isEmpty(), "A repeated transform duplicated the layer path");
        List<String> negatives = new ArrayList<>();
        reject(original, "unknown mixin owner", node -> merged(wrapper(node)).values.set(merged(wrapper(node)).values.indexOf("mixin") + 1, "unknown.LayerMixin"), negatives);
        reject(original, "different injection priority", node -> merged(wrapper(node)).values.set(merged(wrapper(node)).values.indexOf("priority") + 1, 1500), negatives);
        reject(original, "extra wrapper effect", node -> wrapper(node).instructions.insert(new InsnNode(NOP)), negatives);
        reject(original, "additional operation consumer", node -> { MethodNode method = wrapper(node); method.instructions.insertBefore(method.instructions.getLast(), new VarInsnNode(ALOAD, 10)); method.instructions.insertBefore(method.instructions.getLast(), new InsnNode(POP)); }, negatives);
        reject(original, "changed finally catch type", node -> wrapper(node).tryCatchBlocks.getFirst().type = "java/lang/RuntimeException", negatives);
        reject(original, "changed cleanup range", node -> wrapper(node).tryCatchBlocks.getFirst().end = wrapper(node).tryCatchBlocks.getFirst().handler, negatives);
        reject(original, "changed virtual bridge", node -> { for (MethodNode method : node.methods) if (method.name.startsWith("mixinextras$bridge$")) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKEINTERFACE && call.desc.endsWith(";FFFFFFF)V")) call.name = "unknownRender"; }, negatives);
        reject(original, "interior operand boundary", node -> { MethodNode method = caller(node); MethodInsnNode call = captureCall(node, method); List<AbstractInsnNode> code = code(method); int at = code.indexOf(call); LabelNode boundary = new LabelNode(); method.instructions.insertBefore(code.get(at - 5), boundary); method.instructions.insert(boundary, new LineNumberNode(999, boundary)); }, negatives);
        reject(original, "foreign jump into operand preloads", node -> { MethodNode method = caller(node); List<AbstractInsnNode> code = code(method); int at = code.indexOf(captureCall(node, method)); LabelNode into = new LabelNode(); method.instructions.insertBefore(code.get(at - 5), into); method.instructions.insertBefore(method.instructions.getLast(), new JumpInsnNode(GOTO, into)); }, negatives);
        reject(original, "duplicate matched site", node -> { MethodNode method = caller(node), copy = new MethodNode(ASM9, method.access, "unknownSecondLayerPass", method.desc, method.signature, method.exceptions.toArray(String[]::new)); method.accept(copy); node.methods.add(copy); }, negatives);
        Map<String, Object> report = new LinkedHashMap<>(); report.put("passed", true); report.put("actualSites", sites.size()); report.put("verifiedMethods", verified); report.put("nativeFinallyRegions", typed.tryCatchBlocks.size()); report.put("unknownPatternsRejectedWithoutMutation", negatives); report.put("fingerprints", fingerprints); report.put("idempotent", true);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (args.length > 2) Files.write(Path.of(args[2]), bytes(target));
        System.out.println("Actual layer contract: one typed site, " + verified + " verified methods, two native finally regions, " + negatives.size() + " unsupported shapes rejected unchanged.");
    }
    private static void reject(byte[] original, String name, Consumer<ClassNode> mutate, List<String> passed) {
        ClassNode target = read(original); mutate.accept(target); byte[] before = bytes(target);
        try { require(LayerOperationFusion.apply(target).isEmpty(), "Unsupported shape was transformed: " + name); }
        catch (IllegalArgumentException unsupported) { }
        require(Arrays.equals(before, bytes(target)), "Unsupported shape was partially mutated: " + name); passed.add(name);
    }
    private static MethodNode wrapper(ClassNode node) { return node.methods.stream().filter(method -> method.name.endsWith("$celeritas$captureLayer")).findFirst().orElseThrow(); }
    private static AnnotationNode merged(MethodNode method) { return method.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/MixinMerged;")).findFirst().orElseThrow(); }
    private static MethodNode caller(ClassNode node) { return node.methods.stream().filter(method -> !method.name.endsWith("$celeritas$captureLayer") && Arrays.stream(method.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals(wrapper(node).name))).findFirst().orElseThrow(); }
    private static MethodInsnNode captureCall(ClassNode node, MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call && call.name.equals(wrapper(node).name)).map(instruction -> (MethodInsnNode) instruction).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction); return result; }
    private static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(ASM9); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
