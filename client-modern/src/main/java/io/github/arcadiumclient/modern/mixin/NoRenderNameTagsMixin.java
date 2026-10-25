// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NameTagFeatureRenderer.class)
public abstract class NoRenderNameTagsMixin {
    @Inject(method = {"render"}, at = @At("HEAD"), cancellable = true)
    private void arcadium$render(CallbackInfo ci) { if (NoRender.effect(Effect.NAME_TAGS)) ci.cancel(); }
}
