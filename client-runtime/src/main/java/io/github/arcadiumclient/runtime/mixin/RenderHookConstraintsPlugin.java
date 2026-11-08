// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin;

import com.moulberry.mixinconstraints.ConstraintsMixinPlugin;
import io.github.arcadiumclient.runtime.client.optimizations.GlCallbackFusion;
import io.github.arcadiumclient.runtime.client.optimizations.ParticleOperationFusion;
import io.github.arcadiumclient.runtime.client.optimizations.LayerOperationFusion;
import io.github.arcadiumclient.runtime.client.optimizations.CosmeticUsernameFusion;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookLateExtension;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class RenderHookConstraintsPlugin extends ConstraintsMixinPlugin {
    private static final java.util.Set<String> FORGE_ITEM_FIXES = java.util.Set.of(
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelBakedItemVertices",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelBakedItemAdmission",
            "io.github.arcadiumclient.runtime.mixin.compat.forge.MixinLegacyItemCameraScope");

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (FORGE_ITEM_FIXES.contains(mixinClassName) && !Boolean.getBoolean("arcadium.forge.prepared")) return false;
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        super.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
        if (mixinClassName.endsWith(".TrustedCosmeticUsernameMarker")) {
            try {
                int sites = CosmeticUsernameFusion.apply(targetClass).size(); RenderHookAllocationControl.cosmeticSites += sites;
                LogManager.getLogger("Arcadium/Render Hooks").info("Prepared {} exact cosmetic username gates", sites);
            } catch (RuntimeException unsupported) { LogManager.getLogger("Arcadium/Render Hooks").warn("Keeping original cosmetic username checks", unsupported); }
            return;
        }
        if (mixinClassName.endsWith(".TypedLayerOperationsMarker")) { RenderHookLateExtension.arm(targetClass, "layer", node -> LayerOperationFusion.apply(node).size(), sites -> RenderHookAllocationControl.layerSites += sites); return; }
        boolean particles = mixinClassName.endsWith(".TypedParticleOperationsMarker");
        if (particles) { RenderHookLateExtension.arm(targetClass, "particle", node -> ParticleOperationFusion.apply(node).size(), sites -> RenderHookAllocationControl.particleSites += sites); return; }
        if (!mixinClassName.endsWith(".ReducedRenderHookMarker")) return;
        ClassNode original = new ClassNode(Opcodes.ASM9);
        targetClass.accept(original);
        try {
            int sites = GlCallbackFusion.apply(targetClass).size();
            RenderHookAllocationControl.fusedSites += sites;
            LogManager.getLogger("Arcadium/Render Hooks").info("Prepared {} typed matrix capture gates", sites);
        } catch (RuntimeException unsupported) {
            targetClass.methods = original.methods;
            LogManager.getLogger("Arcadium/Render Hooks").warn("Keeping original render hooks", unsupported);
        }
    }
}
