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
    @Override public RfbClassTransformer[] makeEarlyTransformers() {
        OptimizationDefaults.install();
        return new RfbClassTransformer[]{new MixinAsmSymbols(), new SyntheticArgsTypes(), new ArchaicMixinSelection(), new HostedMixinContainer(), new LiteCacheSave()};
    }

    private static final class LiteCacheSave implements RfbClassTransformer {
        @Override public String id() { return "lite-cache-save-snapshot"; }
        @Override public boolean shouldTransformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            return name.replace('/', '.').equals("makamys.coretweaks.optimization.transformercache.lite.TransformerCache");
        }
        @Override public void transformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            try (var pin = ArcadiumRfbPlugin.class.getResourceAsStream("/arcadium-coretweaks-cache.sha256")) {
                if (pin == null) throw new IllegalStateException("Missing CoreTweaks cache integration pin");
                String expected = new String(pin.readAllBytes(), StandardCharsets.US_ASCII).trim();
                String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(handle.getOriginalBytes()));
                if (!expected.equals(actual)) throw new IllegalStateException("The pinned CoreTweaks LITE cache implementation has changed");
            } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
            ClassNode node = handle.getNode();
            int snapshots = 0;
            for (var method : node.methods) {
                if (!method.name.equals("saveTransformerCache") || !method.desc.equals("()V")) continue;
                for (var instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                            || !field.owner.equals(node.name) || !field.name.equals("transformerMap") || !field.desc.equals("Ljava/util/Map;")) continue;
                    method.instructions.insert(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "io/github/arcadiumclient/forge1710/bootstrap/CoreCacheSnapshot", "snapshot", "(Ljava/util/Map;)Ljava/util/Map;", false));
                    snapshots++;
                }
            }
            if (snapshots != 1) throw new IllegalStateException("The pinned CoreTweaks LITE save boundary has changed");
            handle.markDirty();
        }
    }

    private static final class HostedMixinContainer implements RfbClassTransformer {
        @Override public String id() { return "hosted-mixin-container"; }
        @Override public boolean shouldTransformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            return name.replace('/', '.').equals("io.github.legacymoddingmc.unimixins.mixin.MixinSanityCheck");
        }
        @Override public void transformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            ClassNode node = handle.getNode();
            boolean found = false;
            for (var method : node.methods) {
                if (!method.name.equals("checkMixinContainer") || !method.desc.equals("()Ljava/util/List;")) continue;
                for (var instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                    method.instructions.insertBefore(instruction, new org.objectweb.asm.tree.LdcInsnNode(org.objectweb.asm.Type.getObjectType(node.name)));
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "io/github/arcadiumclient/forge1710/bootstrap/HostedMixinRuntime", "checkContainer",
                        "(Ljava/util/List;Ljava/lang/Class;)Ljava/util/List;", false));
                    method.maxStack = Math.max(method.maxStack, 2);
                    found = true;
                }
            }
            if (!found) throw new IllegalStateException("The pinned UniMixins container check API has changed");
            handle.markDirty();
        }
    }

    private static final class ArchaicMixinSelection implements RfbClassTransformer {
        @Override public String id() { return "archaic-mixin-selection"; }
        @Override public boolean shouldTransformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            return name.replace('/', '.').equals("org.embeddedt.archaicfix.ArchaicCore");
        }
        @Override public void transformClass(ExtensibleClassLoader loader, Context context, Manifest manifest, String name, ClassNodeHandle handle) {
            ClassNode node = handle.getNode();
            boolean found = false;
            for (var method : node.methods) {
                if (!method.name.equals("getMixins") || !method.desc.equals("(Ljava/util/Set;)Ljava/util/List;")) continue;
                for (var instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "io/github/arcadiumclient/forge1710/bootstrap/OptimizationMixinPolicy", "archaicMixins",
                        "(Ljava/util/List;)Ljava/util/List;", false));
                    found = true;
                }
            }
            if (!found) throw new IllegalStateException("The pinned ArchaicFix mixin selection API has changed");
            handle.markDirty();
        }
    }

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
