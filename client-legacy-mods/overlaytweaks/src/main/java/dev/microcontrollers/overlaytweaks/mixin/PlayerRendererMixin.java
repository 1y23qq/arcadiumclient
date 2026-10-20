// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package dev.microcontrollers.overlaytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
    @WrapOperation(method = "renderRightHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor3f(FFF)V"))
    private void invisibleHandAlpha(float red, float green, float blue, Operation<Void> original,
                                    @Local(argsOnly = true) PlayerEntity player) {
        if (player.isInvisible() && OverlayTweaksConfig.handInvisibilityOpacity > 0f) {
            GL11.glColor4f(red, green, blue, Math.max(0f, Math.min(1f, OverlayTweaksConfig.handInvisibilityOpacity / 100f)));
        } else original.call(red, green, blue);
    }
}
