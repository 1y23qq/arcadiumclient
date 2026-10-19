// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabBadgeMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;getWidth(Ljava/lang/String;)I", ordinal = 0))
    private int arcadium$measureName(TextRenderer font, String name, Operation<Integer> original,
                                     @Local PlayerInfo player) {
        return original.call(font, name) + (PlayerBadgeRenderer.inTab(player) ? PlayerBadgeRenderer.ADVANCE : 0);
    }

    @WrapOperation(method = "render", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I", ordinal = 1),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I", ordinal = 2)})
    private int arcadium$drawName(TextRenderer font, String name, float x, float y, int color,
                                  Operation<Integer> original, @Local PlayerInfo player) {
        if (PlayerBadgeRenderer.inTab(player)) {
            PlayerBadgeRenderer.draw(x, y, color);
            x += PlayerBadgeRenderer.ADVANCE;
        }
        return original.call(font, name, x, y, color);
    }
}
