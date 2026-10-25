// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.mixin.render;

//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.gui.font.FontManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.polyfrost.oneconfig.internal.ui.compose.ModernFontResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FontManager.class)
public class Mixin_ModernFontResources {
    @ModifyVariable(method = "prepare", at = @At("HEAD"), argsOnly = true)
    private ResourceManager arcadium$fontResources(ResourceManager resources) {
        return new ModernFontResources(resources);
    }
}
*///?}
