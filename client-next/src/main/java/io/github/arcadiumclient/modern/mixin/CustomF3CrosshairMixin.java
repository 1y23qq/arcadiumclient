// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.CustomF3Config;
import net.minecraft.client.renderer.DebugCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugCrosshairRenderer.class)
public abstract class CustomF3CrosshairMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void arcadium$axes(CallbackInfo callback) { if (CustomF3Config.getEnabled() && CustomF3Config.getHideDebugCrosshair()) callback.cancel(); }
}
