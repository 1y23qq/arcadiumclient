// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.NativeHandProbe;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class HandRenderQaMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void arcadium$entered(CallbackInfo ci) { NativeHandProbe.entered(); }
    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void arcadium$completed(CallbackInfo ci) { NativeHandProbe.completed(); }
}
