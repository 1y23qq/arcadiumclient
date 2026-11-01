// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import java.nio.IntBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.*;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer", remap = false)
public abstract class MixinGpuModelUpload {
    @Shadow private GlMutableBuffer instanceBuffer;

    @WrapMethod(method = "upload")
    private void arcadium$expandTransforms(CommandList commands, IntBuffer instances, Operation<Void> original) {
        original.call(commands, instances);
        if (!GpuModelTransforms.hasJobs(instances)) return;
        try { GpuModelTransforms.expand(instanceBuffer.handle(), instances); }
        catch (Throwable error) {
            GpuModelTransforms.disable(error); GpuModelTransforms.recover(instances);
            original.call(commands, instances);
        }
    }
}
