// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.ModernFrameSampler;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class BenchmarkFrameMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void arcadium$measureFrame(CallbackInfo ci) { ModernFrameSampler.renderedFrame(); }
}
