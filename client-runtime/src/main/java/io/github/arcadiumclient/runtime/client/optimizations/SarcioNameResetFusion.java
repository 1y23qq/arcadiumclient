// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class SarcioNameResetFusion {
    private static final String MIXIN = "dev.rdh.sarcio.mixin.mem.alloc.render.LivingEntityRendererMixin";
    private static final String SOURCE_HASH = "a60f76a30c41fe1e5cd0031aa708b9e53458cbc9602b3f52539810f6c00a52af";
    private static final String HELPER = "io/github/arcadiumclient/runtime/client/optimizations/EntityNameFormatting";
    private static Boolean pinned;
    private SarcioNameResetFusion() { }

    static int apply(ClassNode target) {
        if (pinned == null) {
            try (var input = SarcioNameResetFusion.class.getClassLoader().getResourceAsStream(MIXIN.replace('.', '/') + ".class")) {
                pinned = input != null && SOURCE_HASH.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readNBytes(128 << 10))));
            } catch (Exception unavailable) { pinned = false; }
        }
        return pinned ? applyPinned(target) : 0;
    }

    static int applyPinned(ClassNode target) {
        if (!target.name.equals("net/minecraft/client/render/entity/LivingEntityRenderer") && !target.name.equals("net/minecraft/unmapped/C_71252617")) return 0;
        record Site(MethodNode method, InvokeDynamicInsnNode call) { }
        List<Site> sites = new ArrayList<>();
        for (MethodNode method : target.methods) {
            if (!method.name.endsWith("$prepareRenderedName") && !method.name.equals("prepareRenderedName")) continue;
            Type[] arguments = Type.getArgumentTypes(method.desc);
            if (arguments.length != 1 || arguments[0].getSort() != Type.OBJECT || Type.getReturnType(method.desc).getSort() != Type.OBJECT || !merged(method)) continue;
            List<InvokeDynamicInsnNode> concatenations = new ArrayList<>(); boolean writesName = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD && field.name.endsWith("sarcio$renderedName") && field.desc.equals("Ljava/lang/String;")) writesName = true;
                if (instruction instanceof InvokeDynamicInsnNode call && reset(call)) concatenations.add(call);
            }
            if (writesName && concatenations.size() == 1) sites.add(new Site(method, concatenations.getFirst()));
        }
        if (sites.size() != 1) return 0;
        Site site = sites.getFirst();
        site.method.instructions.set(site.call, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "appendReset", "(Ljava/lang/String;)Ljava/lang/String;", false));
        return 1;
    }

    private static boolean reset(InvokeDynamicInsnNode call) {
        return call.name.equals("makeConcatWithConstants") && call.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")
                && call.bsm.getTag() == Opcodes.H_INVOKESTATIC && call.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")
                && call.bsm.getName().equals("makeConcatWithConstants")
                && call.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
                && !call.bsm.isInterface() && call.bsmArgs.length == 1 && "\u0001\u00a7r".equals(call.bsmArgs[0]);
    }

    private static boolean merged(MethodNode method) {
        for (List<AnnotationNode> annotations : List.of(method.visibleAnnotations == null ? List.<AnnotationNode>of() : method.visibleAnnotations,
                method.invisibleAnnotations == null ? List.<AnnotationNode>of() : method.invisibleAnnotations))
            for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
                for (int index = 0; index + 1 < annotation.values.size(); index += 2)
                    if (annotation.values.get(index).equals("mixin") && MIXIN.equals(annotation.values.get(index + 1))) return true;
        return false;
    }
}
