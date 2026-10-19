// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.features.ToggleSprintConfig;
import io.github.arcadiumclient.runtime.client.features.ToggleSprintController;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalClientPlayerEntity.class)
public abstract class ToggleSprintMixin {
    @Shadow protected int doubleTapSprintTime;

    @Inject(method = "mobTick", at = @At("HEAD"))
    private void arcadium$sampleToggleKeys(CallbackInfo ci) {
        ToggleSprintController.beforeTick();
        if (ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getDisableWTapSprint()) doubleTapSprintTime = 0;
    }

    @Inject(method = "mobTick", at = @At("TAIL"))
    private void arcadium$flightTransition(CallbackInfo ci) { ToggleSprintController.afterTick(); }

    @WrapOperation(method = "mobTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;isPressed()Z"))
    private boolean arcadium$heldSprint(KeyBinding binding, Operation<Boolean> original) {
        return ToggleSprintController.sprint(original.call(binding));
    }
}
