// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(KeyBindsScreen.class)
public abstract class ConfirmResetKeysMixin {
    @ModifyArg(method = "addFooter", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;", ordinal = 0), index = 1)
    private Button.OnPress arcadium$confirmReset(Button.OnPress original) {
        return button -> {
            Minecraft mc = Minecraft.getInstance();
            Screen parent = (Screen) (Object) this;
            mc.gui.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) original.onPress(button);
                mc.gui.setScreen(parent);
            }, Component.translatable("controls.resetAll"), Component.literal("Restore all key bindings to their default values?")));
        };
    }
}
