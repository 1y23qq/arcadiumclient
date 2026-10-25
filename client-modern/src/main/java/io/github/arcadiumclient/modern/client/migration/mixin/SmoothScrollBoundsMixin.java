// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import io.github.arcadiumclient.modern.client.migration.LegacySmoothScrollingConfig;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractScrollArea.class)
public abstract class SmoothScrollBoundsMixin {
    @Shadow private double scrollAmount;
    @Shadow public abstract int maxScrollAmount();
    @Inject(method = "setScrollAmount", at = @At("RETURN"))
    private void arcadium$disableOverscroll(double value, CallbackInfo callback) {
        if (!LegacySmoothScrollingConfig.pushBack) scrollAmount = Math.clamp(scrollAmount, 0, maxScrollAmount());
    }
}
