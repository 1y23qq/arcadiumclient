package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.Feature058Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.api.render.gui.hud.HudBatch$Colored", remap = false)
public class TabBatchProbe {
    @Inject(method = "fill", at = @At("HEAD"), remap = false)
    private void release$fill(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
        if (Feature058Qa.recording) Feature058Qa.colors.add(color);
    }
}
