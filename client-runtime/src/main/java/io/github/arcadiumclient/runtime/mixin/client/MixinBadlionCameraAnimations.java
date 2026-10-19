// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.GameRenderer;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class MixinBadlionCameraAnimations {
    @WrapOperation(method = "applyHurtCam", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V"))
    private void arcadium$hurtShake(float angle, float x, float y, float z, Operation<Void> original) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings != null && z != 0f) angle *= settings.hurtCameraScale();
        original.call(angle, x, y, z);
    }
}
