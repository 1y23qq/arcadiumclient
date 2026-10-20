// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GameGui;
import io.github.arcadiumclient.runtime.client.features.BackgroundOpacity;
import io.github.arcadiumclient.runtime.client.features.TabBackgroundConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(GameGui.class)
public class MixinTabBackground {
    @WrapOperation(method = "render", slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/network/handler/ClientPlayNetworkHandler;onlinePlayers:Ljava/util/List;", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;fill(IIIII)V"))
    private void arcadium$tabOpacity(int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (TabBackgroundConfig.getEnabled()) color = BackgroundOpacity.withAlpha(color, TabBackgroundConfig.getOpacity());
        if ((color >>> 24) != 0) original.call(x1, y1, x2, y2, color);
    }
}
