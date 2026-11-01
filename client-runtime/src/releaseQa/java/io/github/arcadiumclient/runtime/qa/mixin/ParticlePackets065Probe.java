// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.arcadiumclient.runtime.qa.ParticlePackets065Qa;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "io.github.arcadiumclient.runtime.client.optimizations.GpuParticles", remap = false)
public abstract class ParticlePackets065Probe {
    @ModifyReturnValue(method = "queue", at = @At("RETURN"))
    private static boolean particle$original(boolean queued) {
        return queued && ParticlePackets065Qa.preserveNative() ? false : queued;
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private static void particle$verify(BufferBuilder builder, CallbackInfoReturnable<Boolean> callback) {
        if (ParticlePackets065Qa.verifyOriginal(builder)) callback.setReturnValue(false);
    }
}
