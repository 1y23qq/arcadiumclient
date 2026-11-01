// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.NextRenderingQa;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import java.nio.*;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer",remap=false)
public abstract class NextGpuReferenceProbe {
    @Shadow private GlMutableBuffer instanceBuffer;
    @Unique private boolean next$pending;
    @Unique private int next$words;
    @Unique private IntBuffer next$equipment;
    @Inject(method="upload",at=@At("HEAD"),remap=false)
    private void next$source(CommandList commands,IntBuffer payload,CallbackInfo callback){
        if(!NextRenderingQa.enabled()||payload.remaining()%19!=0||payload.remaining()/19<128)return;
        if(NextRenderingQa.modelFaultRequested&&GpuModelTransforms.hasJobs(payload)){
            NextRenderingQa.modelFaultRequested=false;NextRenderingQa.modelFaultInjected=true;GpuModelTransforms.disable(new IllegalStateException("QA: simulated pose compute failure"));
        }
        if(NextRenderingQa.captureGolden()&&!GpuModelTransforms.hasJobs(payload))NextRenderingQa.storeGolden(payload);
        if(NextRenderingQa.validateDense()&&GpuModelTransforms.hasJobs(payload)){next$pending=true;next$words=payload.remaining();}
        if(NextRenderingQa.validateEquipment()&&GpuModelTransforms.hasJobs(payload)&&(payload.get(payload.position()+17)>>>16)==32){next$pending=true;next$words=payload.remaining();next$equipment=IntBuffer.allocate(next$words);next$equipment.put(payload.duplicate()).flip();}
    }
    @Inject(method="draw(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V",at=@At("HEAD"),remap=false)
    private void next$verify(CommandList commands,int vertices,int instances,CallbackInfo callback){
        if(next$pending){next$pending=false;if(next$equipment!=null){NextRenderingQa.verifyEquipmentGpu(instanceBuffer.handle(),next$equipment);next$equipment=null;}else NextRenderingQa.verifyGpu(instanceBuffer.handle(),next$words);}
    }
}
