// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.PotionVisualizerQa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.potion.OverrideOptionsVisualizer", remap = false)
abstract class PotionVisualizerQaMixin {
    @Inject(method = "visualize", at = @At("RETURN"), remap = false)
    private void arcadium$rendered(CallbackInfo callback) { PotionVisualizerQa.rendered(); }
}
