// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CustomF3AxisMixin {
    @Inject(method = "renderAxisIndicators", at = @At("HEAD"), cancellable = true)
    private void arcadium$axes(CallbackInfo callback) { if (CustomF3Config.getEnabled() && CustomF3Config.getHideDebugCrosshair()) callback.cancel(); }
}
