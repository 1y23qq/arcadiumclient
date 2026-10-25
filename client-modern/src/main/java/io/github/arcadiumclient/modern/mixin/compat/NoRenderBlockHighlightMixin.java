// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("custom-block-highlight")
@Mixin(targets = "tektonikal.customblockhighlight.Renderer", remap = false)
public abstract class NoRenderBlockHighlightMixin {
    @Inject(method = "mainLoop", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcadium$highlight(CallbackInfo ci) { if (NoRender.effect(Effect.BLOCK_OUTLINE)) ci.cancel(); }
}
