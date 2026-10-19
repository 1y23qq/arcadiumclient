package io.github.arcadiumclient.forgecompat;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class HostSplashCompatibility {
    private static final String SPLASH = "net/minecraftforge/fml/client/SplashProgress";

    private HostSplashCompatibility() { }

    public static void normalize(ClassNode node) {
        if (!node.name.equals(SPLASH)) return;
        MethodNode start = node.methods.stream().filter(method -> method.name.equals("start") && method.desc.equals("()V")
            && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)).findFirst().orElseThrow(() -> unsupported("start method"));
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode instruction : start.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
        var stores = code.stream().filter(instruction -> instruction instanceof FieldInsnNode field
            && field.getOpcode() == Opcodes.PUTSTATIC && field.owner.equals(SPLASH) && field.name.equals("enabled") && field.desc.equals("Z")).toList();
        if (stores.size() != 1) throw unsupported("enabled assignment");
        int index = code.indexOf(stores.get(0));
        if (index >= 2 && code.get(index - 2).getOpcode() == Opcodes.POP && code.get(index - 1).getOpcode() == Opcodes.ICONST_0) return;
        long settings = 0;
        for (int cursor = 2; cursor < code.size(); cursor++) if (code.get(cursor) instanceof MethodInsnNode call
            && call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf && call.owner.equals(SPLASH) && call.name.equals("getBool")
            && call.desc.equals("(Ljava/lang/String;Z)Z") && code.get(cursor - 2) instanceof LdcInsnNode literal && literal.cst.equals("enabled")) settings++;
        if (settings != 1 || index < 3 || code.get(index - 3).getOpcode() != Opcodes.ICONST_1
            || code.get(index - 2).getOpcode() != Opcodes.GOTO || code.get(index - 1).getOpcode() != Opcodes.ICONST_0) throw unsupported("enabled selection");
        // The host owns loading on its render thread; Forge already supports this path.
        InsnList selection = new InsnList(); selection.add(new InsnNode(Opcodes.POP)); selection.add(new InsnNode(Opcodes.ICONST_0));
        start.instructions.insertBefore(stores.get(0), selection);
    }

    private static IllegalStateException unsupported(String detail) {
        return new IllegalStateException("Unsupported Forge splash bytecode: " + detail);
    }
}
