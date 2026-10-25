// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class NoRenderWorldEffectsMixin {
    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void arcadium$outline(CallbackInfo ci) { if (NoRender.effect(Effect.BLOCK_OUTLINE)) ci.cancel(); }
    @Inject(method = "renderBlockDestroyAnimation", at = @At("HEAD"), cancellable = true)
    private void arcadium$breaking(CallbackInfo ci) { if (NoRender.effect(Effect.BLOCK_BREAKING)) ci.cancel(); }
}
