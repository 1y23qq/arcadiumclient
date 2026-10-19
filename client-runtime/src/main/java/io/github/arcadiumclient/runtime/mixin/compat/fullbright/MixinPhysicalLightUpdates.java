// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.fullbright;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import io.github.arcadiumclient.runtime.client.features.PhysicalLightGuard;
import org.spongepowered.asm.mixin.Mixin;

@IfModLoaded("fullbright")
@Mixin(World.class)
public abstract class MixinPhysicalLightUpdates {
    @WrapMethod(method = "updateLight")
    private boolean arcadium$storedLight(LightType type, BlockPos pos, Operation<Boolean> original) {
        PhysicalLightGuard.begin();
        try { return original.call(type, pos); }
        finally { PhysicalLightGuard.end(); }
    }
}
