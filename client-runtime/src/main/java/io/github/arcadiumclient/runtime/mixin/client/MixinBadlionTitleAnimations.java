// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public abstract class MixinBadlionTitleAnimations {
    @ModifyExpressionValue(method = "render(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GameGui;titleTime:I"))
    private int arcadium$hideTitles(int time) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        return settings != null && settings.removeTitles() ? 0 : time;
    }

    @WrapOperation(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;translatef(FFF)V", ordinal = 1))
    private void arcadium$titlePosition(float x, float y, float z, Operation<Void> original) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings != null && settings.movableTitles()) {
            Window window = new Window(Minecraft.getInstance());
            x += settings.titleOffsetX(window.getWidth());
            y += settings.titleOffsetY(window.getHeight());
        }
        original.call(x, y, z);
    }
}
