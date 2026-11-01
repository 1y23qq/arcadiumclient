// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.OptimizationsBetaQa;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import java.nio.*;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer",remap=false)
public abstract class OptimizationGpuProbe {
    @Shadow private GlMutableBuffer instanceBuffer;
    @Unique private IntBuffer optimization$pending;
    @Inject(method="upload",at=@At("HEAD"),remap=false)
    private void optimization$source(CommandList commands,IntBuffer payload,CallbackInfo callback) {
        if(OptimizationsBetaQa.enabled() && OptimizationsBetaQa.verifiedDraws<2 && GpuModelTransforms.hasJobs(payload)) {
            optimization$pending=IntBuffer.allocate(payload.remaining());optimization$pending.put(payload.duplicate()).flip();
        }
    }
    @Inject(method="draw(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V",at=@At("HEAD"),remap=false)
    private void optimization$verify(CommandList commands,int vertices,int instances,CallbackInfo callback) {
        if(optimization$pending!=null){OptimizationsBetaQa.verify(instanceBuffer.handle(),optimization$pending);optimization$pending=null;}
    }
}
