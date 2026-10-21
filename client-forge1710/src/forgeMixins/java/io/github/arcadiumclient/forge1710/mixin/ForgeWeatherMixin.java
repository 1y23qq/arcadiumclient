// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.polyweather.client.ClientWeatherManager;
import org.polyfrost.polyweather.client.PolyWeatherConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.World")
public abstract class ForgeWeatherMixin {
    @Shadow @Final public boolean field_72995_K;

    @ModifyReturnValue(method = "func_72867_j(F)F", at = @At("RETURN"), remap = false)
    private float arcadium$rain(float original, float partialTicks) {
        return field_72995_K && PolyWeatherConfig.isEnabled()
            ? ClientWeatherManager.getPrecipitationStrength(partialTicks) : original;
    }

    @ModifyReturnValue(method = "func_72819_i(F)F", at = @At("RETURN"), remap = false)
    private float arcadium$thunder(float original, float partialTicks) {
        return field_72995_K && PolyWeatherConfig.isEnabled()
            ? ClientWeatherManager.getStormStrength(partialTicks) : original;
    }
}
