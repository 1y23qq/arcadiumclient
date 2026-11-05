// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.ResolvedInstance072Qa;
import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.gl.device.GLRenderDevice$ImmediateCommandList", remap = false)
public abstract class ResolvedInstance072DriverProbe {
    @Inject(method = "uploadData(Lorg/embeddedt/embeddium/impl/gl/buffer/GlMutableBuffer;JJLorg/embeddedt/embeddium/impl/gl/buffer/GlBufferUsage;)V", at = @At("HEAD"), remap = false)
    private void resolved$driver(GlMutableBuffer buffer, long address, long bytes, GlBufferUsage usage, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.driverStart(buffer, address, bytes); }
    @Inject(method = "uploadData(Lorg/embeddedt/embeddium/impl/gl/buffer/GlMutableBuffer;JJLorg/embeddedt/embeddium/impl/gl/buffer/GlBufferUsage;)V", at = @At("RETURN"), remap = false)
    private void resolved$return(GlMutableBuffer buffer, long address, long bytes, GlBufferUsage usage, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.driverEnd(buffer, address, bytes); }
    @Inject(method = "bindBuffer", at = @At("HEAD"), remap = false)
    private void resolved$binding(GlBufferTarget target, GlBuffer buffer, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.bound(buffer, target); }
}
