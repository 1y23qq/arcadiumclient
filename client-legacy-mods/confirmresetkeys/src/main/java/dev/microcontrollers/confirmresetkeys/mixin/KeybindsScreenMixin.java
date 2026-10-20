package dev.microcontrollers.confirmresetkeys.mixin;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ControlsOptionsScreen.class)
public abstract class KeybindsScreenMixin extends Screen {
    @Inject(method = "buttonClicked", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;keyBindings:[Lnet/minecraft/client/options/KeyBinding;", opcode = Opcodes.GETFIELD), cancellable = true)
    private void addConfirmScreen(ButtonWidget button, CallbackInfo ci) {
        this.minecraft.openScreen(new ConfirmScreen(
                (confirmed, id) -> {
                    if (confirmed) {
                        for (KeyBinding keyBinding : this.minecraft.options.keyBindings) {
                            keyBinding.setKeyCode(keyBinding.getDefaultKeyCode());
                        }
                        KeyBinding.resetMapping();
                    }
                    this.minecraft.openScreen(this);
                },
                I18n.translate("confirmresetkeys.title"),
                I18n.translate("confirmresetkeys.message"),
                0
        ));
        ci.cancel();
    }
}
