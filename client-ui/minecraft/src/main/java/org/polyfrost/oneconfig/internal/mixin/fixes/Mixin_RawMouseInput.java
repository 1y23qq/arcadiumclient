//? if <= 1.8.9 {
/*package org.polyfrost.oneconfig.internal.mixin.fixes;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLHints;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class Mixin_RawMouseInput {
    @Unique private static Boolean arcadium$rawApplied;

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void arcadium$configureRawInput(CallbackInfo ci) {
        boolean raw = OneConfigConfig.rawMouseInput;
        if (arcadium$rawApplied != null && arcadium$rawApplied == raw) return;
        if (SDLHints.SDL_SetHintWithPriority(SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE,
                raw ? "0" : "1", SDLHints.SDL_HINT_OVERRIDE)) {
            arcadium$rawApplied = raw;
        }
    }
}
*///?}
