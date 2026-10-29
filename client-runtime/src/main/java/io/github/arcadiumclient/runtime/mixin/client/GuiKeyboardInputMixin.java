// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.features.GuiKeyboardInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class GuiKeyboardInputMixin {
    @Inject(method = "openScreen", at = @At("RETURN"))
    private void arcadium$screenInput(CallbackInfo ci) { GuiKeyboardInput.update(true); }

    @Inject(method = "tick", at = @At("HEAD"))
    private void arcadium$focusedInput(CallbackInfo ci) { GuiKeyboardInput.update(false); }
}
