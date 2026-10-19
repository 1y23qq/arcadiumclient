// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import io.github.arcadiumclient.runtime.client.features.NoHitDelayConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Minecraft.class)
public abstract class MixinNoHitDelay {
    @ModifyConstant(method = "doAttack", constant = @Constant(intValue = 10), require = 2, expect = 2, allow = 2)
    private int arcadium$missAttackDelay(int vanilla) {
        return NoHitDelayConfig.getEnabled() ? 0 : vanilla;
    }
}
