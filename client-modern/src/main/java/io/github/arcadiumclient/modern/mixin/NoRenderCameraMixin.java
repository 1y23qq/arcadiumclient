// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoRenderCameraMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void arcadium$hurt(PoseStack pose, float partialTick, CallbackInfo ci) {
        var animation = AnimationsConfig.badlion();
        if (NoRender.effect(Effect.HURT_CAMERA) || animation != null && animation.noShake()) ci.cancel();
    }
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void arcadium$bob(CallbackInfo ci) { if (NoRender.effect(Effect.VIEW_BOBBING)) ci.cancel(); }
    @WrapOperation(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private void arcadium$intensity(PoseStack pose, Quaternionfc rotation, Operation<Void> original) {
        var settings = AnimationsConfig.badlion();
        if (settings != null && rotation.x() == 0 && rotation.y() == 0 && rotation.z() != 0)
            rotation = new Quaternionf().slerp(rotation, settings.hurtCameraScale());
        original.call(pose, rotation);
    }
}
