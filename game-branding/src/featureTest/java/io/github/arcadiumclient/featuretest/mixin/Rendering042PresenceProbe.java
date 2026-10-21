// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.Rendering042Qa;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.presence.ArcadiumPresence", remap = false)
public abstract class Rendering042PresenceProbe {
    @Inject(method = "isOnline", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcadium$testBadge(UUID player, CallbackInfoReturnable<Boolean> ci) {
        if (Rendering042Qa.enabled() && Rendering042Qa.PLAYER.equals(player)) ci.setReturnValue(Rendering042Qa.badge);
    }
}
