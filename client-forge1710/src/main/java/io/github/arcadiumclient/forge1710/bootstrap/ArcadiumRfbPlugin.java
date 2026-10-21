// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.bootstrap;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;
import java.nio.charset.StandardCharsets;
import java.util.jar.Manifest;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class ArcadiumRfbPlugin implements RfbPlugin {
    @Override public RfbClassTransformer[] makeEarlyTransformers() { return new RfbClassTransformer[]{new MixinAsmSymbols(), new SyntheticArgsTypes()}; }

    private static final class SyntheticArgsTypes implements RfbClassTransformer {
        @Override public String id() { return "synthetic-args-types"; }
        @Override public boolean shouldTransformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            return name.replace('/', '.').equals("org.spongepowered.asm.mixin.transformer.ClassInfo");
        }
        @Override public void transformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            ClassNode node = handle.getNode();
            boolean found = false;
            for (var method : node.methods) {
                if (!method.name.equals("forName") || !method.desc.equals("(Ljava/lang/String;)Lorg/spongepowered/asm/mixin/transformer/ClassInfo;")) continue;
                LabelNode ordinary = new LabelNode();
                InsnList prefix = new InsnList();
                prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/arcadiumclient/forge1710/bootstrap/SyntheticArgsMetadata", "node", "(Ljava/lang/String;)Lorg/objectweb/asm/tree/ClassNode;", false));
                prefix.add(new InsnNode(Opcodes.DUP));
                prefix.add(new JumpInsnNode(Opcodes.IFNULL, ordinary));
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, "fromClassNode", "(Lorg/objectweb/asm/tree/ClassNode;)Lorg/spongepowered/asm/mixin/transformer/ClassInfo;", false));
                prefix.add(new InsnNode(Opcodes.ARETURN));
                prefix.add(ordinary);
                prefix.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[]{"org/objectweb/asm/tree/ClassNode"}));
                prefix.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(prefix);
                method.maxStack = Math.max(method.maxStack, 2);
                found = true;
            }
            if (!found) throw new IllegalStateException("The pinned Mixin class metadata API has changed.");
            handle.markDirty();
        }
    }

    private static final class MixinAsmSymbols implements RfbClassTransformer {
        @Override public String id() { return "mixin-asm-symbols"; }
        @Override public String[] additionalExclusions() { return new String[]{"io.github.arcadiumclient.forge1710.bootstrap."}; }

        @Override public boolean shouldTransformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            name = name.replace('/', '.');
            if (!handle.isPresent() || name.startsWith("org.spongepowered.asm.") || name.startsWith("org.objectweb.asm.")) return false;
            String constants = new String(handle.getOriginalBytes(), StandardCharsets.ISO_8859_1);
            return constants.contains("org/spongepowered/asm/lib/") || constants.contains("org/spongepowered/libraries/org/objectweb/asm/")
                || constants.contains("org/apache/logging/log4j/core/helpers/Integers");
        }

        @Override public void transformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            ClassNode output = new ClassNode(Opcodes.ASM9);
            handle.getNode().accept(new ClassRemapper(output, new Remapper(Opcodes.ASM9) {
                @Override public String map(String type) {
                    if (type.equals("org/apache/logging/log4j/core/helpers/Integers")) return "org/apache/logging/log4j/core/util/Integers";
                    for (String prefix : new String[]{"org/spongepowered/asm/lib/", "org/spongepowered/libraries/org/objectweb/asm/"})
                        if (type.startsWith(prefix)) return "org/objectweb/asm/" + type.substring(prefix.length());
                    return type;
                }
            }));
            handle.setNode(output);
        }
    }
}
