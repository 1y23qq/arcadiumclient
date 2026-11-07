// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfModLoaded("legacy-skin-layers")
@Mixin(GlStateManager.class)
public abstract class MixinGpuVoxelSkinCallList {
    @WrapOperation(method="callList",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glCallList(I)V",remap=false))
    private static void arcadium$physicalVoxelLeaf(int list,Operation<Void> original){
        if(!GpuVoxelSkinPipeline.capturePhysicalLeaf(list))original.call(list);
    }
}
