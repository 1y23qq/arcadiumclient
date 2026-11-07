// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import io.github.arcadiumclient.runtime.client.optimizations.GpuResolvedInstanceCache;
import java.nio.IntBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.system.MemoryUtil;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer", remap = false)
public abstract class MixinGpuModelUpload {
    @Shadow private GlMutableBuffer instanceBuffer;

    @WrapMethod(method = "upload")
    private void arcadium$expandTransforms(CommandList commands, IntBuffer instances, Operation<Void> original) throws Throwable {
        io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.requireOwner();
        int token = GpuResolvedInstanceCache.beginUpload(this, commands, instances, instanceBuffer);
        try {
            io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.requireOwner();original.call(commands, instances);io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.requireOwner();
            if (GpuResolvedInstanceCache.reused(token, instanceBuffer)) return;
            if (GpuResolvedInstanceCache.needsRepair(token))
                commands.uploadData(instanceBuffer, MemoryUtil.memAddress(instances), (long)instances.remaining() * Integer.BYTES, GlBufferUsage.STREAM_DRAW);
            if (!GpuModelTransforms.hasJobs(instances)) { GpuResolvedInstanceCache.abort(token); return; }
            try {
                io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.requireOwner();
                GpuModelTransforms.expand(instanceBuffer.handle(), instances);
                GpuResolvedInstanceCache.complete(token, instanceBuffer);
            } catch (Throwable error) {
                GpuResolvedInstanceCache.abort(token);
                if(io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.foreign(error))throw error;io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.requireOwner();
                if(io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.failure(error))io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.disablePrivate();else GpuModelTransforms.disable(error); GpuModelTransforms.recover(instances);
                io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.requireOwner();original.call(commands, instances);io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073.requireOwner();
            }
        } catch (Throwable error) {
            GpuResolvedInstanceCache.abort(token); throw error;
        } finally { GpuResolvedInstanceCache.endUpload(token); }
    }

    @Inject(method = "delete", at = @At("HEAD"), remap = false)
    private void arcadium$forgetBuffer(CommandList commands, CallbackInfo callback) {
        GpuResolvedInstanceCache.invalidate(this);
    }
}
