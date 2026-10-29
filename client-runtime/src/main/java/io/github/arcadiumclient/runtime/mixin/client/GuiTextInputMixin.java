// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.features.GuiKeyboardInput;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class GuiTextInputMixin {
    @Inject(method = {"handleMouse", "handleKeyboard"}, at = @At("TAIL"))
    private void arcadium$textFocusChanged(CallbackInfo ci) { GuiKeyboardInput.update(false); }
}
