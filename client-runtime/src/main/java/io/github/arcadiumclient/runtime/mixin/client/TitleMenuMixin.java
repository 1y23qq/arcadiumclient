// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.runtime.client.background.MenuPanoramaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleMenuMixin {
    @Shadow private int time;

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void arcadium$panorama(int mouseX, int mouseY, float tickDelta, CallbackInfo callback) {
        MenuPanoramaRenderer.draw(time + tickDelta);
        callback.cancel();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void arcadium$menu(CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if ((Object) client.screen != this) return;
        client.openScreen(new ArcadiumMainMenu());
    }
}
