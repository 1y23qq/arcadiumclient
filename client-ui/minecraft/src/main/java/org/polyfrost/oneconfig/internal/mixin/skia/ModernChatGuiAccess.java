// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.mixin.skia;

//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.DeltaTracker;
//~ if >= 26.2 'Gui' -> 'Hud'
import net.minecraft.client.gui.Gui;
//~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

//~ if >= 26.2 'Gui' -> 'Hud'
@Mixin(Gui.class)
public interface ModernChatGuiAccess {
    //~ if >= 26.2 'renderChat' -> 'extractChat'
    @Invoker("renderChat")
    //~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
    void arcadium$renderChat(GuiGraphics graphics, DeltaTracker tracker);
}
*///?}
