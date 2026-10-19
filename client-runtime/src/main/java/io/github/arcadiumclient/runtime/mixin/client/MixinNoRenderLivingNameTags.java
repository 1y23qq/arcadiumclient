// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityRenderer.class, priority = 2000)
public abstract class MixinNoRenderLivingNameTags {
    @Inject(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterLivingNameTag(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.NAME_TAGS)) ci.cancel();
    }
}
