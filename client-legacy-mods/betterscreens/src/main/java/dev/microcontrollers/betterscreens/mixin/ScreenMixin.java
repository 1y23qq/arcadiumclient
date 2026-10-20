package dev.microcontrollers.betterscreens.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Screen.class)
public class ScreenMixin {
    @WrapWithCondition(method = "renderBackground(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;fillGradient(IIIIII)V"))
    private boolean shouldRenderBackground(Screen instance, int i, int j, int k, int l, int m, int n) {
        return BetterScreensConfig.containerBackgroundOpacity > 0;
    }

    @ModifyArgs(method = "renderBackground(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;fillGradient(IIIIII)V"))
    private void changeContainerBackgroundOpacity(Args args) {
        args.set(4, betterscreens$withOpacity(args.get(4), Math.max(BetterScreensConfig.containerBackgroundOpacity / 100F - 16 / 255F, 0)));
        args.set(5, betterscreens$withOpacity(args.get(5), BetterScreensConfig.containerBackgroundOpacity / 100F));
    }

    @Unique
    private int betterscreens$withOpacity(int color, float opacity) {
        return (int) (opacity * 255) << 24 | (color & 0xFFFFFF);
    }
}
