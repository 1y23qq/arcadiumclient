// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface PlayerPipeline072FogAccess {
    @Accessor("fogRed") float player072$fogRed();
    @Accessor("fogGreen") float player072$fogGreen();
    @Accessor("fogBlue") float player072$fogBlue();
    @Accessor("fogBrightness") float player072$fogBrightness();
    @Accessor("lastFogBrightness") float player072$lastFogBrightness();
    @Accessor("skyDarkness") float player072$skyDarkness();
    @Accessor("lastSkyDarkness") float player072$lastSkyDarkness();
}
