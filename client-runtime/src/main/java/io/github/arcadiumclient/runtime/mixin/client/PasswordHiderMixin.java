// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.runtime.client.features.PasswordHider;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextFieldWidget.class)
public abstract class PasswordHiderMixin {
    @ModifyExpressionValue(method = {"render", "mouseClicked", "setSelectionEnd"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;text:Ljava/lang/String;"))
    private String arcadium$privateInput(String text) {
        if (Minecraft.getInstance().screen instanceof ChatScreen chat
            && ((ChatScreenAccessor) chat).arcadium$chatField() == (Object) this) return PasswordHider.display(text);
        return text;
    }
}
