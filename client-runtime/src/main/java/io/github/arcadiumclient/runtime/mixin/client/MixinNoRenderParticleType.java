// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.entity.particle.Particle;
import io.github.arcadiumclient.runtime.client.features.NoRenderParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public abstract class MixinNoRenderParticleType implements NoRenderParticle {
    @Unique private int arcadium$particleType = -1;

    @Override public int arcadium$particleType() { return arcadium$particleType; }
    @Override public void arcadium$particleType(int type) { arcadium$particleType = type; }
}
