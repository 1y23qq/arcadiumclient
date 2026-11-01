// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.HologramNative065Qa;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class Hologram065Probe {
    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("HEAD"))
    private void arcadium$hologramBody(LivingEntity entity, double x, double y, double z, float yaw, float delta, CallbackInfo callback) {
        if (entity instanceof ArmorStandEntity) HologramNative065Qa.noteBody();
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At("HEAD"))
    private void arcadium$hologramName(LivingEntity entity, double x, double y, double z, CallbackInfo callback) {
        if (entity instanceof ArmorStandEntity) HologramNative065Qa.noteName();
    }
}
