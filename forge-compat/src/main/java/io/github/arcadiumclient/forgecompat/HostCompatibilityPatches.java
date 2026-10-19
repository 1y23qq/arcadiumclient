package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeNew;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorConstructor;
import org.spongepowered.asm.mixin.injection.selectors.TargetSelector;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;
import java.util.List;

@InjectionPoint.AtCode("NEW")
public final class HostCompatibilityPatches extends BeforeNew {
    private static final String FORGE_GUI = "net/minecraftforge/client/GuiIngameForge";
    private record RuntimeTypes(String minecraft, String vanillaGui) {}
    private static volatile RuntimeTypes runtimeTypes;
    private final String vanillaGui;
    private final BeforeNew forgeAllocation;

    public static void install(ForgeMappings mappings) {
        runtimeTypes = new RuntimeTypes(mappings.classFromSrg("net/minecraft/client/Minecraft"),
            mappings.classFromSrg("net/minecraft/client/gui/GuiIngame"));
        InjectionPoint.register(HostCompatibilityPatches.class);
        HostExitCompatibility.install(mappings);
    }

    public HostCompatibilityPatches(InjectionPointData data) {
        super(data);
        RuntimeTypes types = runtimeTypes;
        BeforeNew alternative = null;
        String original = null;
        if (types != null && data.getMixin() != null && types.minecraft.equals(data.getMixin().getTargetClassRef())) {
            String requested = data.get("class", data.get("target", "")).replace('.', '/');
            var selector = TargetSelector.parseAndValidate(requested, data.getContext());
            if (selector instanceof ITargetSelectorConstructor constructor && types.vanillaGui.equals(constructor.toCtorType())) {
                String descriptor = constructor.toCtorDesc();
                String target = descriptor == null ? "L" + FORGE_GUI + ";"
                    : descriptor.substring(0, descriptor.length() - 1) + "L" + FORGE_GUI + ";";
                // Matching delegates to BeforeNew; the outer point retains all original metadata.
                alternative = new BeforeNew(new InjectionPointData(data.getContext(), data.getAt(),
                    List.of("class=" + target), target, data.getSlice(), data.getOrdinal(), data.getOpcode(), data.getId(), 0));
                original = types.vanillaGui;
            }
        }
        vanillaGui = original;
        forgeAllocation = alternative;
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> matches) {
        boolean found = super.find(descriptor, instructions, matches);
        if (found || forgeAllocation == null) return found;
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                    && allocation.desc.equals(vanillaGui)) return false;
        }
        return forgeAllocation.find(descriptor, instructions, matches);
    }
}
