//? if <= 1.8.9 {
/*package org.polyfrost.oneconfig.internal.mixin.fixes;

import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.internal.legacy.FullscreenCursor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class Mixin_LegacyFullscreenCursor {
    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void arcadium$drawFullscreenCursor(CallbackInfo ci) {
        FullscreenCursor.render();
    }
}
*///?}
