// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.hybedwars;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.features.HeightOverlaySettings;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@IfModLoaded("hybedwars")
@Mixin(targets = "tomeko.hybedwars.config.HyBedWarsConfig", remap = false)
public abstract class MixinMinemenHeightConfig implements HeightOverlaySettings {
    @Shadow private static boolean heightOverlay;

    @Unique
    @Switch(title = "Use Minemen Height Limit", description = "Use a height limit of 100 for the HUD and block overlay on Minemen Club.", subcategory = "Height Overlay")
    private boolean minemenHeightLimit = true;

    @Unique
    @Switch(title = "Use CompMC Height Limit", description = "Use a height limit of 100 for the HUD and block overlay on compmc.net.", subcategory = "Height Overlay")
    private boolean compMcHeightLimit = true;

    @Override
    public boolean arcadium$compMcHeightLimitEnabled() {
        return compMcHeightLimit;
    }

    @Override
    public boolean arcadium$minemenHeightLimitEnabled() {
        return minemenHeightLimit;
    }

    @Override
    public boolean arcadium$heightOverlayEnabled() {
        return heightOverlay;
    }
}
