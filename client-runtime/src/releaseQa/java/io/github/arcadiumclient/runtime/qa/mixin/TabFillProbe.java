package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.Feature058Qa;
import net.minecraft.client.gui.GuiElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiElement.class, priority = 5000)
public class TabFillProbe {
    @Inject(method = "fill", at = @At("HEAD"))
    private static void release$fill(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
        if (Feature058Qa.recording) Feature058Qa.colors.add(color);
    }
}
