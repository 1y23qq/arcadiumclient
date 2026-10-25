// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class NoRenderScreenEffectsMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void arcadium$fire(CallbackInfo ci) { if (NoRender.effect(Effect.FIRE_OVERLAY)) ci.cancel(); }
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void arcadium$water(CallbackInfo ci) { if (NoRender.effect(Effect.WATER_OVERLAY)) ci.cancel(); }
    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void arcadium$wall(CallbackInfo ci) { if (NoRender.effect(Effect.IN_WALL_OVERLAY)) ci.cancel(); }
}
