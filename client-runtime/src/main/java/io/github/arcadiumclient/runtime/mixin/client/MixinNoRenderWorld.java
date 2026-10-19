// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.world.WorldRenderer;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, priority = 2000)
public abstract class MixinNoRenderWorld {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterSky(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.SKY)) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterClouds(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.CLOUDS)) ci.cancel();
    }

    @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterWorldBorder(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.WORLD_BORDER)) ci.cancel();
    }

    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterBlockOutline(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.BLOCK_OUTLINE)) ci.cancel();
    }

    @Inject(method = "renderMiningProgress", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterMiningOverlay(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.BLOCK_BREAKING)) ci.cancel();
    }
}
