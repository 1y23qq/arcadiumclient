package dev.microcontrollers.betterscreens.mixin;

import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.gui.screen.inventory.menu.InventoryMenuScreen;
import net.minecraft.client.gui.screen.inventory.menu.PlayerInventoryScreen;
import net.minecraft.inventory.menu.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventoryScreen.class)
public abstract class PlayerInventoryScreenMixin extends InventoryMenuScreen {
    public PlayerInventoryScreenMixin(InventoryMenu menu) {
        super(menu);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void shiftX(CallbackInfo ci) {
        if (BetterScreensConfig.removeStatusEffectsShift) this.x = (this.width - this.backgroundWidth) / 2;
    }
}
