package io.github.arcadiumclient.forgecompat;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class HostClassLoaderCompatibility {
    private static final String LEGACY = "net/minecraft/launchwrapper/LaunchClassLoader";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostClassLoaderCompatibility";

    private HostClassLoaderCompatibility() { }

    public static LaunchClassLoader fromClassLoader(ClassLoader loader) {
        if (loader == null || loader instanceof LaunchClassLoader) return (LaunchClassLoader) loader;
        LaunchClassLoader configured = Launch.classLoader;
        if (configured != null && loader == configured.getParent()) return configured;
        return (LaunchClassLoader) loader;
    }

    public static void normalize(ClassNode node) {
        for (var method : node.methods) {
            Set<LabelNode> entries = new HashSet<>();
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof JumpInsnNode jump) entries.add(jump.label);
                else if (instruction instanceof LookupSwitchInsnNode branch) { entries.add(branch.dflt); entries.addAll(branch.labels); }
                else if (instruction instanceof TableSwitchInsnNode branch) { entries.add(branch.dflt); entries.addAll(branch.labels); }
            }
            for (var handler : method.tryCatchBlocks) entries.add(handler.handler);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof TypeInsnNode cast) || cast.getOpcode() != Opcodes.CHECKCAST || !cast.desc.equals(LEGACY)) continue;
                AbstractInsnNode source = cast.getPrevious();
                while (source != null && source.getOpcode() < 0 && !(source instanceof LabelNode label && entries.contains(label))) source = source.getPrevious();
                if (source instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals("java/lang/Class") && call.name.equals("getClassLoader")
                    && call.desc.equals("()Ljava/lang/ClassLoader;") && !call.itf) {
                    method.instructions.set(cast, new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "fromClassLoader",
                        "(Ljava/lang/ClassLoader;)L" + LEGACY + ";", false));
                }
            }
        }
    }
}
