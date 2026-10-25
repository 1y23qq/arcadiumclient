// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.mixin.skia;

//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.DeltaTracker;
//~ if >= 26.2 'Gui' -> 'Hud'
import net.minecraft.client.gui.Gui;
//~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
import net.minecraft.client.gui.GuiGraphics;
import org.polyfrost.oneconfig.internal.ui.compose.ModernOptionsChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//~ if >= 26.2 'Gui' -> 'Hud'
@Mixin(Gui.class)
public abstract class Mixin_ModernOptionsChat {
    //~ if >= 26.2 'renderChat' -> 'extractChat'
    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    //~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
    private void arcadium$deferOptionsChat(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo callback) {
        if (ModernOptionsChat.defer(tracker)) callback.cancel();
    }
}
*///?}
