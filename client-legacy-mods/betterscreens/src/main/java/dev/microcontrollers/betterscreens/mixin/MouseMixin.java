package dev.microcontrollers.betterscreens.mixin;

import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import dev.microcontrollers.betterscreens.hook.ScreenMouseHook;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "unlock", at = @At("HEAD"), cancellable = true)
    private void lockCursor(CallbackInfo ci) {
        if (ScreenMouseHook.getLastCloseTime() >= 0 && System.currentTimeMillis() - ScreenMouseHook.getLastCloseTime() < BetterScreensConfig.cursorResetTimer) {
            org.lwjgl.input.Mouse.setGrabbed(false);
            ci.cancel();
        }
    }
}
