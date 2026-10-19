package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostHudCompatibility {
    private static final String HUD = "net/minecraftforge/client/GuiIngameForge";
    static final String CORE = "org/polyfrost/oneconfig/internal/OneConfig";
    static final String CONTEXT = "org/polyfrost/evergreenhud/client/hooks/SmuggledHudDrawContext";
    static final String PREVIEW = "org/polyfrost/evergreenhud/client/hooks/PlayerPreviewOffscreen";
    static final String VANILLA = "org/polyfrost/vanillahud/hud/VanillaHud";
    static final String VANILLA_CONTEXT = "org/polyfrost/vanillahud/compat/LegacyDrawContext";
    private static final String ELEMENT = "net/minecraftforge/client/event/RenderGameOverlayEvent$ElementType";

    private HostHudCompatibility() { }

    public static void normalize(ClassNode node, ForgeMappings mappings, boolean evergreenHud, boolean vanillaHud) {
        if (!node.name.equals(HUD)) return;
        String render = mappings.methodFromSrg("net/minecraft/client/gui/GuiIngame", "func_175180_a", "(F)V");
        var method = node.methods.stream().filter(value -> value.name.equals(render) && value.desc.equals("(F)V"))
            .findFirst().orElseThrow(() -> unsupported("render method"));
        var code = Arrays.stream(method.instructions.toArray()).filter(value -> value.getOpcode() >= 0).toList();
        long coreCalls = code.stream().filter(value -> value instanceof MethodInsnNode call && call.owner.equals(CORE)
            && call.name.equals("render") && call.desc.equals("()V")).count();
        if (coreCalls == 1) return;
        if (coreCalls != 0 || code.stream().filter(value -> value.getOpcode() == Opcodes.RETURN).count() != 2
            || code.size() < 3 || code.get(code.size() - 1).getOpcode() != Opcodes.RETURN
            || !(code.get(code.size() - 2) instanceof MethodInsnNode post) || !post.owner.equals(HUD)
            || !post.name.equals("post") || !post.desc.equals("(L" + ELEMENT + ";)V")
            || !(code.get(code.size() - 3) instanceof FieldInsnNode element) || element.getOpcode() != Opcodes.GETSTATIC
            || !element.owner.equals(ELEMENT) || !element.name.equals("ALL")) throw unsupported("overlay completion");
        InsnList head = new InsnList();
        if (vanillaHud) {
            head.add(new FieldInsnNode(Opcodes.GETSTATIC, VANILLA_CONTEXT, "INSTANCE", "L" + VANILLA_CONTEXT + ";"));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, VANILLA, "beginFrame", "(L" + VANILLA_CONTEXT + ";)V", false));
        }
        if (evergreenHud) {
            head.add(new VarInsnNode(Opcodes.FLOAD, 1));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "setSmuggledHudPartialTick", "(F)V", false));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PREVIEW, "render", "()V", false));
        }
        if (head.size() > 0) method.instructions.insert(head);
        method.instructions.insertBefore(code.get(code.size() - 1),
            new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, "render", "()V", false));
    }

    private static IllegalStateException unsupported(String detail) {
        return new IllegalStateException("Unsupported Forge HUD bytecode: " + detail);
    }
}
