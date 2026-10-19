package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;

import java.util.Collection;
import java.util.List;

@InjectionPoint.AtCode("INVOKE")
public final class BeforeForgeInvoke extends BeforeInvoke {
    private static final String WRAPPER = "net/minecraftforge/fml/common/asm/transformers/TerminalTransformer$ExitVisitor";
    private static final String CRASH_EXIT = "io/github/arcadiumclient/forgecompat/HostExitCompatibility";
    private static volatile String minecraft;
    private record DrawTarget(String renderer, String screen, String method, String descriptor) { }
    private static volatile DrawTarget drawTarget;
    private final BeforeInvoke terminalExit;
    private final BeforeInvoke crashExit;
    private final BeforeInvoke drawScreen;
    private final MemberInfo originalDraw;

    static void install(ForgeMappings mappings) {
        minecraft = mappings.classFromSrg("net/minecraft/client/Minecraft");
        drawTarget = new DrawTarget(mappings.classFromSrg("net/minecraft/client/renderer/EntityRenderer"),
            mappings.classFromSrg("net/minecraft/client/gui/GuiScreen"),
            mappings.methodFromSrg("net/minecraft/client/gui/GuiScreen", "func_73863_a", "(IIF)V"), "(IIF)V");
        InjectionPoint.register(BeforeForgeInvoke.class);
    }

    public BeforeForgeInvoke(InjectionPointData data) {
        super(data);
        if (mixin != null && mixin.getTargetClassRef().equals(minecraft) && target instanceof MemberInfo member
                && "java/lang/System".equals(member.getOwner()) && "exit".equals(member.getName())
                && "(I)V".equals(member.getDesc())) {
            terminalExit = equivalent(data, member, WRAPPER, "systemExitCalled");
            crashExit = equivalent(data, member, CRASH_EXIT, "handleExit");
        } else {
            terminalExit = null;
            crashExit = null;
        }
        DrawTarget draw = drawTarget;
        if (draw != null && mixin != null && mixin.getTargetClassRef().equals(draw.renderer)
                && target instanceof MemberInfo member && draw.screen.equals(member.getOwner())
                && draw.method.equals(member.getName()) && draw.descriptor.equals(member.getDesc())) {
            originalDraw = member;
            drawScreen = equivalent(data, member, "net/minecraftforge/client/ForgeHooksClient", "drawScreen",
                "(L" + draw.screen + ";IIF)V");
        } else {
            originalDraw = null;
            drawScreen = null;
        }
    }

    private static BeforeInvoke equivalent(InjectionPointData data, MemberInfo member, String owner, String name) {
        return equivalent(data, member, owner, name, "(I)V");
    }

    private static BeforeInvoke equivalent(InjectionPointData data, MemberInfo member, String owner, String name, String descriptor) {
        String selector = member.remapUsing(new MappingMethod(owner, name, descriptor), true).toString();
        return new BeforeInvoke(new InjectionPointData(data.getContext(), data.getAt(),
            List.of("log=" + data.get("log", false)), selector, data.getSlice(), data.getOrdinal(), data.getOpcode(),
            data.getId(), data.isUnsafe() ? 1 : 0)) {
            @Override protected boolean matchesInsn(AbstractInsnNode instruction) {
                return instruction.getOpcode() == Opcodes.INVOKESTATIC && super.matchesInsn(instruction);
            }
        };
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> matches) {
        boolean found = super.find(descriptor, instructions, matches);
        if (found) return true;
        if (drawScreen != null) {
            for (AbstractInsnNode instruction : instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(originalDraw.getOwner())
                        && call.name.equals(originalDraw.getName()) && call.desc.equals(originalDraw.getDesc())) return false;
            }
            return drawScreen.find(descriptor, instructions, matches);
        }
        if (terminalExit == null) return false;
        boolean terminal = false;
        boolean crash = false;
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof MethodInsnNode call && call.desc.equals("(I)V")) {
                if (call.owner.equals("java/lang/System") && call.name.equals("exit")) return false;
                if (call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                terminal |= call.owner.equals(WRAPPER) && call.name.equals("systemExitCalled");
                crash |= call.owner.equals(CRASH_EXIT) && call.name.equals("handleExit");
            }
        }
        if (terminal == crash) return false;
        return (terminal ? terminalExit : crashExit).find(descriptor, instructions, matches);
    }
}
