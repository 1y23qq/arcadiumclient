// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerTabOverlay.class, priority = 1600)
public abstract class MixinBedwarsTabStats {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void arcadium$bedwarsStats(PlayerInfo info, CallbackInfoReturnable<String> cir) {
        if (info == null || info.getProfile() == null) return;
        cir.setReturnValue(BedwarsOverlay.tabLine(info.getProfile().getId(), info.getProfile().getName(), cir.getReturnValue()));
    }
}
