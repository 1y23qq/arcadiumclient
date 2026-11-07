// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin;

import com.moulberry.mixinconstraints.ConstraintsMixinPlugin;
import io.github.arcadiumclient.runtime.client.optimizations.GlCallbackFusion;
import io.github.arcadiumclient.runtime.client.optimizations.ParticleOperationFusion;
import io.github.arcadiumclient.runtime.client.optimizations.LayerOperationFusion;
import io.github.arcadiumclient.runtime.client.optimizations.CosmeticUsernameFusion;
import io.github.arcadiumclient.runtime.client.optimizations.LowerNamePreparation;
import io.github.arcadiumclient.runtime.client.optimizations.LowerNamePreparationFusion;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookLateExtension;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class RenderHookConstraintsPlugin extends ConstraintsMixinPlugin {
    private static final boolean RESIDENT_TERRAIN_BOOT=Boolean.getBoolean("arcadium.terrainResident072.enabled");
    private static final boolean FORGE_PREPARED=Boolean.getBoolean("arcadium.forge.prepared");
    private static final boolean VOXEL_PHYSICAL_BOOT=Boolean.getBoolean("arcadium.voxelSkin073.physicalMvp");
    private static final java.util.Set<String> FORGE_ITEM_MIXINS=java.util.Set.of(
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelBakedItemVertices",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelBakedItemAdmission",
            "io.github.arcadiumclient.runtime.mixin.compat.forge.MixinLegacyItemCameraScope"
    );
    private static final java.util.Set<String> RESIDENT_TERRAIN_MIXINS=java.util.Set.of(
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainResidentContext",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainResidentIterator",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainResidentStorage",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainResidentShader",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainResidentShaderSource",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainResidentShaderDelete"
    );
    private static final boolean TERRAIN_EVENT_BOOT=RESIDENT_TERRAIN_BOOT&&Boolean.getBoolean("arcadium.terrainResident073.events");
    private static final java.util.Set<String> TERRAIN_EVENT_MIXINS=java.util.Set.of(
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainInventoryLists",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainInventoryManager",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainInventoryRegion",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainInventoryRegionManager",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainInventoryStorage",
            "io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinTerrainResidentSubmission"
    );
    private static final boolean ACTOR_ROOT_BOOT=Boolean.getBoolean("arcadium.actorRoot073.enabled")&&!Boolean.getBoolean("arcadium.actorScene073.enabled")&&!Boolean.getBoolean("arcadium.actorScene073.fusedInput");
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName) {
        if(!ACTOR_ROOT_BOOT&&(mixinClassName.endsWith(".MixinGpuModelActorRootOrigin073")||mixinClassName.endsWith(".MixinGpuModelActorRootTick073")))return false;
        if(!TERRAIN_EVENT_BOOT&&TERRAIN_EVENT_MIXINS.contains(mixinClassName))return false;
        if(!VOXEL_PHYSICAL_BOOT&&mixinClassName.endsWith(".MixinGpuVoxelSkinCallList"))return false;
        if(!FORGE_PREPARED&&FORGE_ITEM_MIXINS.contains(mixinClassName))return false;
        if(!RESIDENT_TERRAIN_BOOT&&RESIDENT_TERRAIN_MIXINS.contains(mixinClassName))return false;
        return super.shouldApplyMixin(targetClassName,mixinClassName);
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
        if (mixinClassName.endsWith(".TypedEntityNameOperationsMarker")) { RenderHookLateExtension.arm(targetClass, "entity-name", LowerNamePreparationFusion::apply, sites -> { LowerNamePreparation.preparedSites = sites; RenderHookAllocationControl.nameSites += sites; }); return; }
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
