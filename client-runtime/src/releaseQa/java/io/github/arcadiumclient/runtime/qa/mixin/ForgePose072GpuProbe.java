// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ForgePose072Qa;
import java.nio.IntBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer", remap = false)
public abstract class ForgePose072GpuProbe {
    @Shadow private GlMutableBuffer instanceBuffer;
    @Unique private IntBuffer forgePose$pending;
    @Inject(method = "upload", at = @At("HEAD"), remap = false)
    private void forgePose$source(CommandList commands, IntBuffer payload, CallbackInfo callback) {
        if (!ForgePose072Qa.enabled() || payload.remaining() % 19 != 0) return;
        ForgePose072Qa.source(payload);
        if (ForgePose072Qa.verifyEnabled(payload)) { forgePose$pending = IntBuffer.allocate(payload.remaining()); forgePose$pending.put(payload.duplicate()).flip(); }
    }
    @Inject(method = "draw(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V", at = @At("HEAD"), remap = false)
    private void forgePose$verify(CommandList commands, int vertices, int instances, CallbackInfo callback) {
        if (forgePose$pending == null) return;
        ForgePose072Qa.verify(instanceBuffer.handle(), forgePose$pending); forgePose$pending = null;
    }
}
