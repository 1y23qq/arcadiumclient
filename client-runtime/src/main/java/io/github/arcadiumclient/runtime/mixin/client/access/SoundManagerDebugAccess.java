// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.access;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.client.sound.system.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SoundManager.class)
public interface SoundManagerDebugAccess { @Accessor("engine") SoundEngine arcadium$engine(); }
