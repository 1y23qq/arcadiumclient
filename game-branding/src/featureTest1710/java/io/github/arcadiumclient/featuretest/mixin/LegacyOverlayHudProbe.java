// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameGui.class)
public interface LegacyOverlayHudProbe {
    @Invoker("renderPumpkinOverlay") void arcadium$pumpkin(int width, int height);
    @Invoker("renderPortalOverlay") void arcadium$portal(float alpha, int width, int height);
}
