// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.overflowparticles;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;

@IfModLoaded("overflowparticles")
@Mixin(value=ParticleManager.class,priority=1)
public abstract class TypedParticleOperationsMarker { }
