// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class SarcioNameResetFusionTest implements Opcodes {
    private static final String MIXIN = "dev.rdh.sarcio.mixin.mem.alloc.render.LivingEntityRendererMixin";
    private static ClassNode fixture(String owner, String recipe) {
        ClassNode node = new ClassNode(ASM9); node.version = V17; node.access = ACC_PUBLIC; node.name = "net/minecraft/client/render/entity/LivingEntityRenderer"; node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(ACC_PRIVATE, "redirect$fixture$prepareRenderedName", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        AnnotationNode merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        merged.values = new ArrayList<>(List.of("mixin", owner)); method.visibleAnnotations = new ArrayList<>(List.of(merged));
        method.instructions.add(new VarInsnNode(ALOAD, 0)); method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new TypeInsnNode(CHECKCAST, "java/lang/String"));
        Handle bootstrap = new Handle(H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
        method.instructions.add(new InvokeDynamicInsnNode("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", bootstrap, recipe));
        method.instructions.add(new FieldInsnNode(PUTFIELD, node.name, "sarcio$renderedName", "Ljava/lang/String;"));
        method.instructions.add(new InsnNode(ACONST_NULL)); method.instructions.add(new InsnNode(ARETURN));
        node.methods.add(method); return node;
    }

    @Test void changesOnlyTheExactResetOperationAndIsIdempotent() {
        ClassNode node = fixture(MIXIN, "\u0001§r"); MethodNode method = node.methods.getFirst();
        int count = method.instructions.size(); AbstractInsnNode first = method.instructions.getFirst(), last = method.instructions.getLast();
        assertEquals(1, SarcioNameResetFusion.applyPinned(node));
        assertEquals(count, method.instructions.size()); assertSame(first, method.instructions.getFirst()); assertSame(last, method.instructions.getLast());
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        assertEquals(1, calls.size()); assertEquals("appendReset", calls.getFirst().name); assertEquals("(Ljava/lang/String;)Ljava/lang/String;", calls.getFirst().desc);
        assertEquals(0, SarcioNameResetFusion.applyPinned(node));
    }

    @Test void preservesChangedOwnersRecipesAndUnknownTargets() {
        assertEquals(0, SarcioNameResetFusion.applyPinned(fixture("custom.OtherMixin", "\u0001§r")));
        assertEquals(0, SarcioNameResetFusion.applyPinned(fixture(MIXIN, "\u0001§a")));
        ClassNode unknown = fixture(MIXIN, "\u0001§r"); unknown.name = "custom/Renderer";
        assertEquals(0, SarcioNameResetFusion.applyPinned(unknown));
    }

    @Test void declinesAmbiguousHandlers() {
        ClassNode node = fixture(MIXIN, "\u0001§r"); node.methods.add(fixture(MIXIN, "\u0001§r").methods.getFirst());
        assertEquals(0, SarcioNameResetFusion.applyPinned(node));
    }
}
