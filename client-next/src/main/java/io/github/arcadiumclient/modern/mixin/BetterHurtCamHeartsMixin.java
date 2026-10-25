// SPDX-License-Identifier: MIT
// Copyright (c) 2022 uku
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.BetterHurtCamConfig;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Hud.class, priority = 900)
public abstract class BetterHurtCamHeartsMixin {
    @ModifyVariable(method = "extractHearts", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean arcadium$heartBlink(boolean original) { return original && BetterHurtCamConfig.INSTANCE.heartBlink; }
}
