package dev.microcontrollers.confirmdisconnect.mixin;

import dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen;
import dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    @Unique private static boolean confirmDisconnect$confirmDisconnect = false;
    @Unique private static int confirmDisconnect$delayTicker;

    @Inject(method = "buttonClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;disconnect()V"), cancellable = true)
    private void onDisconnect(ButtonWidget button, CallbackInfo ci) {
        Screen screen = minecraft.screen;

        if (screen instanceof ConfirmDisconnectScreen) {
            ci.cancel();
        }

        boolean isLocal = minecraft.isSingleplayer();
        if (ConfirmDisconnectConfig.confirmEnabled && !confirmDisconnect$confirmDisconnect && ((isLocal && ConfirmDisconnectConfig.enableInSingleplayer) || (!isLocal && ConfirmDisconnectConfig.enableInMultiplayer))) {
            confirmDisconnect$delayTicker = 0;
            if (ConfirmDisconnectConfig.useDiscreteConfirmation) {
                ((ButtonWidget) this.buttons.getFirst()).active = false;
                ((ButtonWidget) this.buttons.getFirst()).message = I18n.translate("confirm-disconnect.are-you-sure-discrete");
                confirmDisconnect$confirmDisconnect = true;
            } else {
                minecraft.openScreen(new ConfirmDisconnectScreen(screen));
            }
            ci.cancel();
        }
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void addCustomDisconnectButton(CallbackInfo ci) {
        confirmDisconnect$confirmDisconnect = false;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickDelay(CallbackInfo ci) {
        if (confirmDisconnect$delayTicker < 20 * ConfirmDisconnectConfig.confirmDelay) {
            confirmDisconnect$delayTicker++;
        } else {
            Screen screen = minecraft.screen;
            if (screen instanceof GameMenuScreen) {
                ((ButtonWidget) this.buttons.getFirst()).active = true;
            }
        }
    }
}
