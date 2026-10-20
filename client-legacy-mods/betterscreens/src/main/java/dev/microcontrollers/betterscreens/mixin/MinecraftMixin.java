package dev.microcontrollers.betterscreens.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import dev.microcontrollers.betterscreens.hook.ContainerScaleHook;
import dev.microcontrollers.betterscreens.hook.ScreenMouseHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @WrapOperation(method = {"openScreen", "resize(II)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;init(Lnet/minecraft/client/Minecraft;II)V"))
    private void initialOpenScale(Screen instance, Minecraft minecraft, int width, int height, Operation<Void> original) {
        if (!ContainerScaleHook.shouldScale(instance)) {
            original.call(instance, minecraft, width, height);
            return;
        }

        ContainerScaleHook.scale(instance, minecraft, width, height, original);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (BetterScreensConfig.dontResetCursor) {
            boolean screenOpen = Minecraft.getInstance().screen != null;
            if (ScreenMouseHook.getWasScreenOpen() && !screenOpen) {
                ScreenMouseHook.setLastCloseTime(System.currentTimeMillis());
            }
            ScreenMouseHook.setWasScreenOpen(screenOpen);
        }
    }
}
