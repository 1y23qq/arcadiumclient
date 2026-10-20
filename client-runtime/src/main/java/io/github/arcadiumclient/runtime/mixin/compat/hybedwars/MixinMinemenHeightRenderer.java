// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.hybedwars;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.features.HeightLimitSource;
import io.github.arcadiumclient.runtime.client.features.MinemenHeightLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@IfModLoaded("hybedwars")
@Mixin(targets = "tomeko.hybedwars.heightlimit.HeightLimitRenderer", remap = false)
public abstract class MixinMinemenHeightRenderer {
    @WrapOperation(method = {"onClientTick", "onWorldRender"}, at = @At(value = "INVOKE", target = "Ltomeko/hybedwars/config/HyBedWarsConfig;getDebugModeEnabled()Z"))
    private boolean arcadium$minemenOverlay(@Coerce Object config, Operation<Boolean> original) {
        return original.call(config) || HeightLimitSource.minemenOverlayActive();
    }

    @WrapOperation(method = "onWorldRender", at = @At(value = "INVOKE", target = "Ltomeko/hybedwars/config/HyBedWarsConfig;getDebugModeHeight()I"))
    private int arcadium$minemenOverlayHeight(@Coerce Object config, Operation<Integer> original) {
        return HeightLimitSource.minemenOverlayActive() ? MinemenHeightLimit.LIMIT - 1 : original.call(config);
    }

    @WrapOperation(method = "onWorldRender", at = @At(value = "INVOKE", target = "Ltomeko/hybedwars/location/HypixelPackets;getCurrentMapName()Ljava/lang/String;"))
    private String arcadium$minemenMap(@Coerce Object packets, Operation<String> original) {
        return HeightLimitSource.minemenOverlayActive() ? MinemenHeightLimit.MAP_KEY : original.call(packets);
    }
}
