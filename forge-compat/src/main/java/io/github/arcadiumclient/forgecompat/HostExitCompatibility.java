package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostExitCompatibility {
    private static final String FML = "net/minecraftforge/fml/common/FMLCommonHandler";
    private record Target(String minecraft, String method, String descriptor) {}
    private static volatile Target target;

    private HostExitCompatibility() {}

    static void install(ForgeMappings mappings) {
        String descriptor = "(Lnet/minecraft/crash/CrashReport;)V";
        target = new Target(mappings.classFromSrg("net/minecraft/client/Minecraft"),
            mappings.methodFromSrg("net/minecraft/client/Minecraft", "func_71377_b", descriptor),
            mappings.srgToRuntime().mapMethodDesc(descriptor));
        BeforeForgeInvoke.install(mappings);
    }

    public static void normalize(ClassNode node) {
        Target expected = target;
        if (expected == null || !node.name.equals(expected.minecraft)) return;
        for (var method : node.methods) {
            if (!method.name.equals(expected.method) || !method.desc.equals(expected.descriptor)) continue;
            MethodInsnNode exit = null;
            MethodInsnNode instance = null;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !call.owner.equals(FML) || !call.name.equals("handleExit") || !call.desc.equals("(I)V")) continue;
                AbstractInsnNode argument = previous(call);
                AbstractInsnNode receiver = previous(argument);
                if (exit != null || !(argument instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ILOAD
                        || !(receiver instanceof MethodInsnNode factory) || factory.getOpcode() != Opcodes.INVOKESTATIC
                        || !factory.owner.equals(FML) || !factory.name.equals("instance")
                        || !factory.desc.equals("()L" + FML + ";") || factory.itf || call.itf) {
                    throw new IllegalStateException("Unsupported Forge crash-report exit bytecode in " + node.name + "." + method.name + ".");
                }
                exit = call;
                instance = factory;
            }
            if (exit != null) {
                method.instructions.remove(instance);
                method.instructions.set(exit, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "io/github/arcadiumclient/forgecompat/HostExitCompatibility", "handleExit", "(I)V", false));
            }
        }
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    public static void handleExit(int status) {
        FMLCommonHandler.instance().handleExit(status);
    }
}
