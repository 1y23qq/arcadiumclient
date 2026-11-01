// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public abstract class GpuPlayerPipelineMixin {
    @WrapWithCondition(method="render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V"))
    private boolean arcadium$compactPlayers(LivingEntityRenderer<?> renderer,LivingEntity entity,double x,double y,double z,float yaw,float delta){
        return !GpuPlayerPipeline.capture((PlayerRenderer)(Object)this,(ClientPlayerEntity)entity,x,y,z,delta);
    }
}
