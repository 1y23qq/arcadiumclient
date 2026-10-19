// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.evergreenhud;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.Minecraft;
import net.minecraft.world.biome.Biome;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import io.github.arcadiumclient.runtime.client.features.HudValuePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@IfModLoaded("evergreenhud")
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.BiomeHud", remap = false)
public abstract class MixinBiomeHud {
    @Unique
    @Switch(title = "Biome Colors", description = "Uses Minecraft's built-in biome palette. Dark colors are brightened for readable text.", subcategory = "Colors")
    private boolean biomeColors = true;

    protected PolyColor valueColor() {
        if (!biomeColors) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null || mc.player == null) return null;
        Biome biome = mc.world.getBiome(mc.player.getCommandSourceBlockPos());
        return new PolyColor(HudValuePalette.readableBiomeColor(biome.biomeColor));
    }
}
