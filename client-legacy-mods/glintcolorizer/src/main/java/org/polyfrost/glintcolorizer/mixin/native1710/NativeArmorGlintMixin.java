// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.glintcolorizer.mixin.native1710;

//? if =1.7.10 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.resource.Identifier;
import org.polyfrost.glintcolorizer.legacy1710.NativeGlint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class NativeArmorGlintMixin {
    @WrapMethod(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V")
    private void arcadium$armor(LivingEntity entity, double x, double y, double z, float yaw, float delta, Operation<Void> original) {
        NativeGlint.enter(null, NativeGlint.ARMOR);
        try { original.call(entity, x, y, z, yaw, delta); } finally { NativeGlint.exit(); }
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;bindTexture(Lnet/minecraft/resource/Identifier;)V"))
    private void arcadium$texture(LivingEntityRenderer renderer, Identifier texture, Operation<Void> original) { NativeGlint.texture(texture); original.call(renderer, texture); }

    @WrapOperation(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glMatrixMode(I)V"))
    private void arcadium$matrix(int mode, Operation<Void> original) { NativeGlint.matrix(mode); original.call(mode); }

    @WrapOperation(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
    private void arcadium$light(float r, float g, float b, float a, Operation<Void> original) { NativeGlint.color(r, g, b); original.call(r, g, b, a); }

    @WrapOperation(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V"))
    private void arcadium$rotation(float angle, float x, float y, float z, Operation<Void> original) { original.call(NativeGlint.rotation(angle, x, y, z), x, y, z); }
}
//?}
