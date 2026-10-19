// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.entity.EntityRenderer;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = 2000)
public abstract class MixinNoRenderEntityEffects {
    @Inject(method = "renderOnFire", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterEntityFire(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.ENTITY_FIRE)) ci.cancel();
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterShadow(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.ENTITY_SHADOWS)) ci.cancel();
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterNameTag(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.NAME_TAGS)) ci.cancel();
    }
}
