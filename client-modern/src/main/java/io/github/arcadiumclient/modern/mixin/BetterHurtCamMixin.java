// SPDX-License-Identifier: MIT
// Copyright (c) 2022 uku
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.math.Axis;
import io.github.arcadiumclient.modern.client.features.BetterHurtCamConfig;
import io.github.arcadiumclient.modern.client.features.HurtCameraMath;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class BetterHurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void arcadium$enabled(CallbackInfo ci) { if (!BetterHurtCamConfig.INSTANCE.enabled) ci.cancel(); }
    @WrapOperation(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"))
    private Quaternionf arcadium$rotation(Axis axis, float degrees, Operation<Quaternionf> original) {
        return original.call(axis, HurtCameraMath.angle(degrees, BetterHurtCamConfig.INSTANCE.multiplier, axis == Axis.YP || axis == Axis.YN, BetterHurtCamConfig.oldMode()));
    }
}
