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
        int token = GpuResolvedInstanceCache.beginUpload(this, commands, instances);
        try {
            original.call(commands, instances);
            if (GpuResolvedInstanceCache.reused(token, instanceBuffer)) return;
            if (GpuResolvedInstanceCache.needsRepair(token))
                commands.uploadData(instanceBuffer, MemoryUtil.memAddress(instances), (long)instances.remaining() * Integer.BYTES, GlBufferUsage.STREAM_DRAW);
            if (!GpuModelTransforms.hasJobs(instances)) { GpuResolvedInstanceCache.abort(token); return; }
            try {
                GpuModelTransforms.expand(instanceBuffer.handle(), instances);
                GpuResolvedInstanceCache.complete(token, instanceBuffer);
            } catch (Throwable error) {
                GpuResolvedInstanceCache.abort(token);
                GpuModelTransforms.disable(error); GpuModelTransforms.recover(instances);
                original.call(commands, instances);
            }
        } catch (Throwable error) {
            GpuResolvedInstanceCache.abort(token); throw error;
        } finally { GpuResolvedInstanceCache.endUpload(token); }
    }

    @Inject(method = "upload", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;uploadData(Lorg/embeddedt/embeddium/impl/gl/buffer/GlMutableBuffer;JJLorg/embeddedt/embeddium/impl/gl/buffer/GlBufferUsage;)V", shift = At.Shift.BEFORE), remap = false)
    private void arcadium$beginDriver(CommandList commands, IntBuffer instances, CallbackInfo callback) {
        if (GpuResolvedInstanceCache.activeUpload(this)) GpuResolvedInstanceCache.beginDriver(this, commands, instanceBuffer,
                MemoryUtil.memAddress(instances), (long)instances.remaining() * Integer.BYTES, GlBufferUsage.STREAM_DRAW);
    }

    @Inject(method = "upload", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;uploadData(Lorg/embeddedt/embeddium/impl/gl/buffer/GlMutableBuffer;JJLorg/embeddedt/embeddium/impl/gl/buffer/GlBufferUsage;)V", shift = At.Shift.AFTER), remap = false)
    private void arcadium$endDriver(CommandList commands, IntBuffer instances, CallbackInfo callback) { GpuResolvedInstanceCache.endDriver(this); }

    @Inject(method = "delete", at = @At("HEAD"), remap = false)
    private void arcadium$forgetBuffer(CommandList commands, CallbackInfo callback) {
        GpuResolvedInstanceCache.invalidate(this);
    }
}
