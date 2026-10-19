// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.features.ToggleSprintController;
import net.minecraft.entity.living.player.PlayerAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAbilities.class)
public abstract class ToggleFlightBoostMixin {
    @Inject(method = "getFlySpeed", at = @At("RETURN"), cancellable = true)
    private void arcadium$singleplayerFlight(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ToggleSprintController.flight((PlayerAbilities) (Object) this, cir.getReturnValue()));
    }
}
