package dev.microcontrollers.betterscreens.mixin;

import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.locale.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin extends Screen {
    @Inject(method = "init()V", at = @At("TAIL"))
    private void betterscreens$serverButton(CallbackInfo ci) {
        if (!BetterScreensConfig.replaceOpenToLan || minecraft.isSingleplayer()) return;
        for (Object entry : buttons) if (entry instanceof ButtonWidget button && button.id == 7) {
            button.message = I18n.translate("betterscreens.server-list");
            button.active = true;
        }
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void betterscreens$serverList(ButtonWidget button, CallbackInfo ci) {
        if (!button.active || button.id != 7 || !BetterScreensConfig.replaceOpenToLan || minecraft.isSingleplayer()) return;
        minecraft.openScreen(new MultiplayerScreen(null));
        ci.cancel();
    }
}
