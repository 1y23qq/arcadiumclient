// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuResolvedInstanceCache;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.taumc.celeritas.lwjgl.LWJGLService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "org.embeddedt.embeddium.impl.gl.device.GLRenderDevice$ImmediateCommandList", remap = false)
public abstract class MixinGpuResolvedDriverUpload {
    @WrapWithCondition(method = "uploadData(Lorg/embeddedt/embeddium/impl/gl/buffer/GlMutableBuffer;JJLorg/embeddedt/embeddium/impl/gl/buffer/GlBufferUsage;)V",
            at = @At(value = "INVOKE", target = "Lorg/taumc/celeritas/lwjgl/LWJGLService;glBufferData(IJJI)V"), remap = false)
    private boolean arcadium$retainResolved(LWJGLService service, int target, long size, long address, int usage,
                                           GlMutableBuffer buffer, long sourceAddress, long sourceBytes, GlBufferUsage hint) {
        return !GpuResolvedInstanceCache.skipDriver(this, buffer, service, target, size, address, usage);
    }
}
