// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.features.ToggleSprintController;
import net.minecraft.client.entity.living.player.KeyboardInput;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public abstract class ToggleSneakInputMixin {
    @Shadow @Final private GameOptions options;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;isPressed()Z"))
    private boolean arcadium$heldSneak(KeyBinding binding, Operation<Boolean> original) {
        boolean down = original.call(binding);
        return binding == options.sneakKey ? ToggleSprintController.sneak(down) : down;
    }
}
