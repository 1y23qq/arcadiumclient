// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat.evergreenhud;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.features.HudValuePalette;
import net.minecraft.client.Minecraft;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@IfModLoaded("evergreenhud")
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.BiomeHud", remap = false)
public abstract class MixinBiomeHud {
    @Unique @Switch(title = "Biome Colors", subcategory = "Colors") private boolean biomeColors = true;
    protected PolyColor valueColor() {
        Minecraft mc = Minecraft.getInstance();
        if (!biomeColors || mc.level == null || mc.player == null) return null;
        var biome = mc.level.getBiome(mc.player.blockPosition()).value();
        return new PolyColor(HudValuePalette.readableBiomeColor(biome.getGrassColor(mc.player.getX(), mc.player.getZ())));
    }
}
