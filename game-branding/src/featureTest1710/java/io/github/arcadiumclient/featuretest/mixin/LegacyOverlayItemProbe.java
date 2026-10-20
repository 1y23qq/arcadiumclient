// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.render.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemInHandRenderer.class)
public interface LegacyOverlayItemProbe {
    @Invoker("renderOnFireEffect") void arcadium$fire(float delta);
    @Invoker("renderInWaterEffect") void arcadium$water(float delta);
}
