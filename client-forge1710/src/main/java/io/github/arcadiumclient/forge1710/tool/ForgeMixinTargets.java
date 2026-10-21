// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.tool;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class ForgeMixinTargets {
    static byte[] apply(String name, byte[] bytes) {
        if (name.equals("net/ornithemc/osl/resource/loader/impl/adapter/ResourceManagerAdapter")) {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(bytes).accept(node, 0);
            node.superName = "net/minecraft/client/resources/SimpleReloadableResourceManager";
            for (var method : node.methods) {
                if (!method.name.equals("<init>")) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/Object") && call.name.equals("<init>")) {
                        method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));
                        call.owner = node.superName;
                        call.desc = "(Lnet/minecraft/client/resources/data/IMetadataSerializer;)V";
                        method.maxStack = Math.max(method.maxStack, 2);
                    }
                }
            }
            ClassWriter output = new ClassWriter(0);
            node.accept(output);
            return output.toByteArray();
        }
        if (name.equals("net/ornithemc/osl/core/impl/util/ModLoader") ||
            name.equals("org/polyfrost/oneconfig/api/platform/v1/internal/CompatibilityPlatformImpl")) {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(bytes).accept(node, 0);
            if (name.endsWith("/ModLoader")) for (var method : node.methods) {
                if (!method.name.equals("resolve")) continue;
                // The ported resource API retains its data format and uses Forge-backed metadata.
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, name, "FABRIC", "L" + name + ";"));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                method.maxStack = 1;
                method.maxLocals = 0;
            }
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (!(instruction instanceof LdcInsnNode constant)) continue;
                if (name.endsWith("/ModLoader") && "Fabric Loader".equals(constant.cst)) constant.cst = "Forge";
                if (name.endsWith("/ModLoader") && "fabricloader".equals(constant.cst)) constant.cst = "Forge";
                if (name.endsWith("/CompatibilityPlatformImpl") && method.name.equals("loader") && "fabric".equals(constant.cst))
                    constant.cst = "forge";
            }
            ClassWriter output = new ClassWriter(0);
            node.accept(output);
            return output.toByteArray();
        }
        if (!name.toLowerCase(java.util.Locale.ROOT).contains("mixin")) return bytes;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        for (var method : node.methods) {
            if (name.equals("net/ornithemc/osl/networking/impl/mixin/common/PlayerManagerMixin") && method.visibleAnnotations != null)
                for (var annotation : method.visibleAnnotations) playerLoginTarget(annotation);
            if (name.equals("io/github/arcadiumclient/runtime/mixin/client/ChatTweaksMixin") && method.name.equals("arcadium$wrapped")) {
                method.desc = method.desc.replace("(Ljava/util/List;", "(Ljava/util/ArrayList;");
                if (method.signature != null) method.signature = method.signature.replace("(Ljava/util/List<", "(Ljava/util/ArrayList<");
                if (method.localVariables != null) for (var local : method.localVariables)
                    if (local.index == 1) { local.desc = "Ljava/util/ArrayList;"; local.signature = null; }
                if (method.visibleAnnotations != null) for (var annotation : method.visibleAnnotations) chatIterator(annotation);
            }
            boolean resourceInit = name.equals("net/ornithemc/osl/resource/loader/impl/mixin/client/MinecraftMixin")
                && method.name.equals("osl$resource_loader$initResourceManager");
            if (method.visibleAnnotations != null) for (var annotation : method.visibleAnnotations) replace(annotation, resourceInit);
            if (method.invisibleAnnotations != null) for (var annotation : method.invisibleAnnotations) replace(annotation, resourceInit);
            if (name.equals("org/polyfrost/polyweather/mixin/client/Mixin_FixWorldColors") && method.visibleAnnotations != null)
                for (var annotation : method.visibleAnnotations) weatherTargets(annotation);
            if (java.util.Set.of("org/polyfrost/glintcolorizer/mixin/native1710/NativeGuiGlintMixin",
                "xyz/tryfle/oitemphysic/mixin/ItemEntityRendererMixin").contains(name) && method.visibleAnnotations != null)
                for (var annotation : method.visibleAnnotations) droppedGlintTargets(annotation);
            if (name.equals("org/polyfrost/overflowanimations/mixins/legacy/native1710/MixinGuiUsageTexture1710") && method.visibleAnnotations != null)
                for (var annotation : method.visibleAnnotations) guiTextureTarget(annotation);
            if (java.util.Set.of("org/polyfrost/overflowanimations/mixins/legacy/native1710/MixinTintGlint1710",
                "org/polyfrost/overflowanimations/mixins/legacy/native1710/MixinHandGlint1710").contains(name) && method.visibleAnnotations != null)
                for (var annotation : method.visibleAnnotations) handItemTarget(annotation);
            if (name.equals("dev/microcontrollers/overlaytweaks/mixin/ItemInHandRendererMixin") && method.visibleAnnotations != null)
                for (var annotation : method.visibleAnnotations) overlayTarget(annotation);
        }
        ClassWriter result = new ClassWriter(0);
        node.accept(result);
        return result.toByteArray();
    }

    private static void playerLoginTarget(AnnotationNode annotation) {
        if (annotation.values == null) return;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (!annotation.values.get(i).equals("method")) continue;
            @SuppressWarnings("unchecked") var methods = (java.util.List<String>) annotation.values.get(i + 1);
            methods.replaceAll(method -> method.equals("func_72355_a") ? "initializeConnectionToPlayer" : method);
        }
    }

    private static void chatIterator(AnnotationNode node) {
        if (node.values == null) return;
        for (int i = 0; i < node.values.size(); i += 2) {
            Object value = node.values.get(i + 1);
            if (node.values.get(i).equals("target") && "Ljava/util/List;iterator()Ljava/util/Iterator;".equals(value))
                node.values.set(i + 1, "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;");
            else if (value instanceof AnnotationNode annotation) chatIterator(annotation);
            else if (value instanceof java.util.List<?> list) for (Object child : list)
                if (child instanceof AnnotationNode annotation) chatIterator(annotation);
        }
    }

    private static void overlayTarget(AnnotationNode node) {
        if (node.values == null) return;
        for (int i = 0; i < node.values.size(); i += 2) {
            Object value = node.values.get(i + 1);
            if (node.values.get(i).equals("target") && "Lnet/minecraft/client/entity/AbstractClientPlayer;func_82150_aj()Z".equals(value))
                node.values.set(i + 1, "Lnet/minecraft/client/entity/EntityClientPlayerMP;func_82150_aj()Z");
            else if (value instanceof AnnotationNode annotation) overlayTarget(annotation);
            else if (value instanceof java.util.List<?> list) for (Object child : list)
                if (child instanceof AnnotationNode annotation) overlayTarget(annotation);
        }
    }

    private static void handItemTarget(AnnotationNode annotation) {
        if (annotation.values == null) return;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            Object value = annotation.values.get(i + 1);
            if (annotation.values.get(i).equals("method")) {
                @SuppressWarnings("unchecked") var methods = (java.util.List<String>) value;
                methods.replaceAll(method -> method.equals("func_78443_a")
                    ? "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V" : method);
            } else if (value instanceof AnnotationNode child) handItemTarget(child);
            else if (value instanceof java.util.List<?> list) for (Object item : list)
                if (item instanceof AnnotationNode child) handItemTarget(child);
            if (annotation.values.get(i).equals("target") && "Lnet/minecraft/item/ItemStack;func_77962_s()Z".equals(value))
                annotation.values.set(i + 1, "Lnet/minecraft/item/ItemStack;hasEffect(I)Z");
        }
    }

    private static void guiTextureTarget(AnnotationNode annotation) {
        if (annotation.values == null) return;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (!annotation.values.get(i).equals("method")) continue;
            @SuppressWarnings("unchecked")
            var methods = (java.util.List<String>) annotation.values.get(i + 1);
            methods.replaceAll(method -> method.equals("func_82406_b") ? "func_77015_a" : method);
        }
    }

    private static void droppedGlintTargets(AnnotationNode annotation) {
        if (annotation.values == null) return;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (!annotation.values.get(i).equals("method")) continue;
            @SuppressWarnings("unchecked")
            var methods = (java.util.List<String>) annotation.values.get(i + 1);
            methods.replaceAll(method -> method.equals("func_77020_a(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFF)V")
                ? "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V" : method);
        }
    }

    private static void weatherTargets(AnnotationNode annotation) {
        if (annotation.values == null) return;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (!annotation.values.get(i).equals("method")) continue;
            @SuppressWarnings("unchecked")
            var methods = (java.util.List<String>) annotation.values.get(i + 1);
            methods.replaceAll(method -> switch (method) {
                case "func_72833_a" -> "getSkyColorBody";
                case "func_72971_b" -> "getSunBrightnessBody";
                case "func_72824_f" -> "drawCloudsBody";
                default -> method;
            });
        }
    }

    private static void replace(AnnotationNode node, boolean resourceInit) {
        if (node.values == null) return;
        for (int i = 1; i < node.values.size(); i += 2) {
            Object value = node.values.get(i);
            if (node.values.get(i - 1).equals("target") && value instanceof String target) {
                target = target.replace("Lorg/lwjglx/input/", "Lorg/lwjgl/input/");
                target = target.replace("Lorg/lwjglx/opengl/Display;", "Lorg/lwjgl/opengl/Display;");
                if (resourceInit && target.equals("Lnet/minecraft/client/gui/GuiIngame;")) target = "Lnet/minecraftforge/client/GuiIngameForge;";
                node.values.set(i, target);
            }
            else if (value instanceof AnnotationNode annotation) replace(annotation, resourceInit);
            else if (value instanceof java.util.List<?> list) for (Object child : list)
                if (child instanceof AnnotationNode annotation) replace(annotation, resourceInit);
        }
    }
}
