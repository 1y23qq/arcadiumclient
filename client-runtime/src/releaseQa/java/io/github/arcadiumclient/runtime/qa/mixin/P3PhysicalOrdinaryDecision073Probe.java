// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline;
import io.github.arcadiumclient.runtime.qa.P3PhysicalOrdinary073Evidence;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=GpuVoxelSkinPipeline.class,remap=false)
public abstract class P3PhysicalOrdinaryDecision073Probe {
    @WrapMethod(method="capturePhysicalLeaf",remap=false)
    private static boolean p3$decision(int list,Operation<Boolean> original){boolean accepted=original.call(list);P3PhysicalOrdinary073Evidence.decision(list,accepted);return accepted;}
}
