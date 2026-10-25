// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.ToggleSprintConfig;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class ToggleDoubleTapMixin {
    @Shadow protected int sprintTriggerTime;
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void arcadium$doubleTap(CallbackInfo ci) { if (ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getDisableWTapSprint()) sprintTriggerTime = 0; }
}
