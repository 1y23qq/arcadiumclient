// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import io.github.arcadiumclient.runtime.client.gui.SkinChangerScreen;
import io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class MixinSkinChangerPause {
    private static final int ARCADIUM_SKINS = 36077;

    @Inject(method = "init", at = @At("TAIL"))
    private void arcadium$skinButton(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        ((ScreenAccess) this).arcadium$buttons().add(new ButtonWidget(ARCADIUM_SKINS,
                screen.width / 2 - 100, screen.height / 4 + 56, 200, 20, "Skins"));
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void arcadium$openSkins(ButtonWidget button, CallbackInfo ci) {
        if (button.id != ARCADIUM_SKINS) return;
        Minecraft.getInstance().openScreen(new SkinChangerScreen((Screen) (Object) this));
        ci.cancel();
    }
}
