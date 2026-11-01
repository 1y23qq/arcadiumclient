// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.GpuParticles;
import net.minecraft.client.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class GpuParticlePassMixin {
    @WrapMethod(method="render")
    private void arcadium$particles(Entity camera,float delta,Operation<Void> original){GpuParticles.begin(true);try{original.call(camera,delta);}finally{GpuParticles.end();}}
    @WrapMethod(method="renderLit")
    private void arcadium$litParticles(Entity camera,float delta,Operation<Void> original){GpuParticles.begin(false);try{original.call(camera,delta);}finally{GpuParticles.end();}}
    @Inject(method="tick",at=@At("TAIL"))
    private void arcadium$invalidateParticleRecipes(CallbackInfo callback){GpuParticles.tick();}
}
