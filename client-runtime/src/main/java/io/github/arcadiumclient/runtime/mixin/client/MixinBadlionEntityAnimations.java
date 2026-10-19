// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinBadlionEntityAnimations {
    @Shadow private PlayerRenderer defaultPlayerRenderer;

    @Inject(method = "getRenderer(Lnet/minecraft/entity/Entity;)Lnet/minecraft/client/render/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private void arcadium$wideArms(Entity entity, CallbackInfoReturnable<EntityRenderer<?>> cir) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings != null && settings.noSmallArms() && entity instanceof ClientPlayerEntity) cir.setReturnValue(defaultPlayerRenderer);
    }

    @WrapWithCondition(method = "renderHitbox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderOutlineShape(Lnet/minecraft/util/math/Box;IIII)V", ordinal = 1))
    private boolean arcadium$oldHitboxEyes(Box box, int red, int green, int blue, int alpha) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        return settings == null || !settings.hitBox();
    }

    @WrapWithCondition(method = "renderHitbox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;nextVertex()V"))
    private boolean arcadium$oldHitboxDirection(BufferBuilder buffer) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        return settings == null || !settings.hitBox();
    }
}
