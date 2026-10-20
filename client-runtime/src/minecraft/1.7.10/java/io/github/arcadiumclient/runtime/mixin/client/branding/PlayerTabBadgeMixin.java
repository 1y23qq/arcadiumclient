// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(GameGui.class)
public abstract class PlayerTabBadgeMixin {
    @WrapOperation(method = "render", slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/network/handler/ClientPlayNetworkHandler;onlinePlayers:Ljava/util/List;", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;III)I", ordinal = 0))
    private int arcadium$drawName(TextRenderer font, String name, int x, int y, int color, Operation<Integer> original, @Local PlayerInfo player) {
        if (PlayerBadgeRenderer.inTab(player)) {
            PlayerBadgeRenderer.draw(x, y, color);
            x += PlayerBadgeRenderer.ADVANCE;
        }
        return original.call(font, name, x, y, color);
    }
}
