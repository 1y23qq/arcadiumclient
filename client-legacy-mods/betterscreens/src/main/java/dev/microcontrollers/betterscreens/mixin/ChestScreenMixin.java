package dev.microcontrollers.betterscreens.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.gui.screen.inventory.menu.ChestScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChestScreen.class)
public class ChestScreenMixin {
    @ModifyExpressionValue(method = "renderLabels", at = @At(value = "CONSTANT", args = "intValue=4210752", ordinal = 0))
    private int changeContainerLabelColor(int original) {
        return BetterScreensConfig.containerLabelColor.getArgb();
    }

    @ModifyExpressionValue(method = "renderLabels", at = @At(value = "CONSTANT", args = "intValue=4210752", ordinal = 1))
    private int changePlayerLabelColor(int original) {
        return BetterScreensConfig.playerInventoryLabelColor.getArgb();
    }
}
