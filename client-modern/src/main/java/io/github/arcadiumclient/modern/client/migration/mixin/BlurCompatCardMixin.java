// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.compat.MidnightLibCompat", remap = false)
public class BlurCompatCardMixin {
    @Inject(method = "onInit", at = @At("HEAD"), cancellable = true)
    private static void arcadium$legacyControls(String id, Class<?> type, CallbackInfo callback) { if ("blur".equals(id)) callback.cancel(); }
}
