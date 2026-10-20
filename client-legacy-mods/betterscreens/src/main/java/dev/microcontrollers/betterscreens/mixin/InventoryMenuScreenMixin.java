package dev.microcontrollers.betterscreens.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.menu.InventoryMenuScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryMenuScreen.class)
public class InventoryMenuScreenMixin {
    @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;touchscreen:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    private boolean clickOut(boolean original) {
        return original || BetterScreensConfig.clickOutOfContainers;
    }

    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private void properlyCloseMenu(Minecraft instance, Screen screen, Operation<Void> original) {
        instance.player.closeMenu();
    }
}
