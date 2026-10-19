// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumWatermark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameMenuScreen.class)
public abstract class MixinArcadiumPauseTitle {
    @ModifyArg(method = "render(IIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/menu/GameMenuScreen;drawCenteredString(Lnet/minecraft/client/render/TextRenderer;Ljava/lang/String;III)V"), index = 3)
    private int arcadium$keepTitleAboveBrand(int y) {
        return ArcadiumWatermark.pauseTitleY((Screen) (Object) this);
    }
}
