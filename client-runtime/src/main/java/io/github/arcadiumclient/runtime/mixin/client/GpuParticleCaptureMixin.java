// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.client.optimizations.GpuParticles;
import io.github.arcadiumclient.runtime.client.optimizations.ParticleRecipeAccess;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Particle.class,priority=500)
public abstract class GpuParticleCaptureMixin implements ParticleRecipeAccess {
    @Unique private Object arcadium$particleRecipe;
    @Unique private long arcadium$particleRecipeEpoch=-1;
    @Override public Object arcadium$recipe(){return arcadium$particleRecipe;}
    @Override public long arcadium$recipeEpoch(){return arcadium$particleRecipeEpoch;}
    @Override public void arcadium$recipe(Object value,long epoch){arcadium$particleRecipe=value;arcadium$particleRecipeEpoch=epoch;}
    @Shadow protected float red,green,blue,alpha;
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void arcadium$cachedAppearance(BufferBuilder builder,Entity camera,float delta,float dx,float dy,float dz,float forward,float side,CallbackInfo callback){
        if(GpuParticles.tryRecipe((Particle)(Object)this,builder,delta,dx,dy,dz,forward,side))callback.cancel();
    }
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/vertex/BufferBuilder;vertex(DDD)Lnet/minecraft/client/render/vertex/BufferBuilder;",ordinal=0),cancellable=true)
    private void arcadium$billboard(BufferBuilder builder,Entity camera,float delta,float dx,float dy,float dz,float forward,float side,CallbackInfo callback,
                                    @Local(index=9) float u0,@Local(index=10) float u1,@Local(index=11) float v0,@Local(index=12) float v1,
                                    @Local(index=13) float size,@Local(index=14) float x,@Local(index=15) float y,@Local(index=16) float z,@Local(index=17) int light){
        if(GpuParticles.queue((Particle)(Object)this,builder,x,y,z,size,u0,u1,v0,v1,red,green,blue,alpha,dx,dy,dz,forward,side,light))callback.cancel();
    }
}
