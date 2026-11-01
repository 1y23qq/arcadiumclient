// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.OptimizationsBetaQa;
import io.github.arcadiumclient.runtime.qa.NextRenderingQa;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class OptimizationWorldProbe {
    @WrapMethod(method="renderWorld(FJ)V")
    private void optimization$timeWorld(float delta,long deadline,Operation<Void> original) {
        if(!OptimizationsBetaQa.enabled()&&!NextRenderingQa.enabled()){original.call(delta,deadline);return;}
        boolean next=NextRenderingQa.enabled();long cpu=System.nanoTime();
        int query=next?NextRenderingQa.beginGpuTime():OptimizationsBetaQa.beginGpuTime();
        try {original.call(.5f,deadline);} finally {if(next)NextRenderingQa.endGpuTime(query,cpu);else OptimizationsBetaQa.endGpuTime(query);}
    }
}
