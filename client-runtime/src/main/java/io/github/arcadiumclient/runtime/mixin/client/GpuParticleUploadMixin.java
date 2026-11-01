// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.optimizations.GpuParticles;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BufferUploader.class,priority=1500)
public abstract class GpuParticleUploadMixin {
    @Inject(method="end",at=@At("HEAD"),cancellable=true)
    private void arcadium$drawParticles(BufferBuilder builder,CallbackInfo callback){if(GpuParticles.draw(builder))callback.cancel();}
}
