// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.ResolvedInstance072Qa;
import java.nio.IntBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer", remap = false)
public abstract class ResolvedInstance072BufferProbe {
    @Shadow private GlMutableBuffer instanceBuffer;
    @Inject(method = "upload", at = @At("HEAD"), remap = false)
    private void resolved$upload(CommandList commands, IntBuffer data, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.upload(this, instanceBuffer, data); }
    @Inject(method = "upload", at = @At("RETURN"), remap = false)
    private void resolved$uploaded(CommandList commands, IntBuffer data, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.uploaded(); }
    @Inject(method = "draw(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V", at = @At("HEAD"), remap = false)
    private void resolved$draw(CommandList commands, int vertices, int instances, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.draw(this, instanceBuffer, vertices, instances); }
    @Inject(method = "delete", at = @At("HEAD"), remap = false)
    private void resolved$delete(CommandList commands, CallbackInfo callback) { ResolvedInstance072Qa.deleted(); }
}
