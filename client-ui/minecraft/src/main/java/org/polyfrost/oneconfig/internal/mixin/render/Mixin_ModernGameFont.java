// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.mixin.render;

//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.polyfrost.oneconfig.internal.ui.compose.ModernGameFonts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class Mixin_ModernGameFont {
    @Shadow @Final private Font.Provider provider;
    @Unique private ModernGameFonts arcadium$fonts;

    @Inject(method = "getGlyphSource", at = @At("RETURN"), cancellable = true)
    private void arcadium$fontSource(FontDescription description, CallbackInfoReturnable<GlyphSource> callback) {
        if (arcadium$fonts == null) arcadium$fonts = new ModernGameFonts();
        callback.setReturnValue(arcadium$fonts.select(provider, description, callback.getReturnValue()));
    }
}
*///?}
