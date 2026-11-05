// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.ResolvedInstance072Qa;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = GlMutableBuffer.class, remap = false)
public abstract class ResolvedInstance072MutableProbe {
    @Inject(method = "setSize", at = @At("HEAD"), remap = false)
    private void resolved$size(long size, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.sized(this); }
}
