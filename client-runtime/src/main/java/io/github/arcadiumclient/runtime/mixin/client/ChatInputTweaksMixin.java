// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatInputTweaksMixin {
    @Shadow protected TextFieldWidget chatField;

    @Inject(method = {"init", "tick"}, at = @At("TAIL"))
    private void arcadium$localCommandLength(CallbackInfo ci) {
        if (chatField != null) chatField.setMaxLength(ChatTweaksConfig.getBypassCommandLimit() && chatField.getText().stripLeading().startsWith("/") ? 32767 : 100);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void arcadium$commandPaste(char character, int key, CallbackInfo ci) {
        if (chatField == null || !ChatTweaksConfig.getBypassCommandLimit()) return;
        boolean command = chatField.getText().stripLeading().startsWith("/") || character == '/' && chatField.getText().isBlank();
        if (key == 47 && Screen.isControlDown()) command |= Screen.getClipboard().stripLeading().startsWith("/");
        if (command) chatField.setMaxLength(32767);
    }

    @WrapOperation(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V", ordinal = 1))
    private void arcadium$shiftEnter(Minecraft client, Screen next, Operation<Void> original) {
        if (next == null && ChatTweaksConfig.getShiftChat() && Screen.isShiftDown()) chatField.setText("");
        else original.call(client, next);
    }
}
