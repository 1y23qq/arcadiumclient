// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import java.util.List;
import io.github.arcadiumclient.runtime.client.features.TabBackgroundConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerTabOverlay.class, priority = 1200)
public class MixinTabBackground {
    @WrapOperation(method = "render", at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;fill(IIIII)V", ordinal = 0),
        @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;fill(IIIII)V", ordinal = 1),
        @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;fill(IIIII)V", ordinal = 3)})
    private void arcadium$tabOpacity(int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        color = TabBackgroundConfig.background(color, false);
        if ((color >>> 24) != 0) original.call(x1, y1, x2, y2, color);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;fill(IIIII)V", ordinal = 2))
    private void arcadium$playerBackground(int x1, int y1, int x2, int y2, int color, Operation<Void> original,
                                           @Local(index = 5) List<PlayerInfo> players, @Local(index = 19) int row) {
        PlayerInfo player = players.get(row);
        var self = Minecraft.getInstance().player;
        boolean ownName = self != null && player.getProfile() != null && self.getUuid().equals(player.getProfile().getId());
        color = TabBackgroundConfig.background(color, ownName);
        TabBackgroundConfig.drawingOwnName(ownName);
        try {
            if ((color >>> 24) != 0) original.call(x1, y1, x2, y2, color);
        } finally {
            TabBackgroundConfig.drawingOwnName(false);
        }
    }
}
