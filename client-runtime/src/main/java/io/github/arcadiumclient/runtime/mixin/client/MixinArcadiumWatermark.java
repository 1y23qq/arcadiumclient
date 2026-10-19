// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumWatermark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinArcadiumWatermark {
    @Inject(method = "render(IIF)V", at = @At("TAIL"))
    private void arcadium$drawWatermark(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        ArcadiumWatermark.render((Screen) (Object) this);
    }
}
