// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.ResolvedInstance072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "org.taumc.celeritas.lwjgl.lwjgl3.LWJGL3Service", remap = false)
public abstract class ResolvedInstance072ServiceProbe {
    @Inject(method = "glBufferData(IJJI)V", at = @At("HEAD"), remap = false)
    private void resolved$raw(int target, long size, long address, int usage, CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.raw(target, size, address); }
}
