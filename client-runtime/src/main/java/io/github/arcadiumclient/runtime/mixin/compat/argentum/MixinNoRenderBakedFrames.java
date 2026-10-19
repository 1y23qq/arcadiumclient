// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.util.List;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.blockentity.BakedItemFrames", remap = false)
public abstract class MixinNoRenderBakedFrames {
    @Inject(method = "collect", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcadium$filterBakedFrames(CallbackInfoReturnable<List<?>> cir) {
        if (NoRender.policy().hides(NoRenderPolicy.EntityPart.ITEM_FRAMES)) cir.setReturnValue(List.of());
    }
}
