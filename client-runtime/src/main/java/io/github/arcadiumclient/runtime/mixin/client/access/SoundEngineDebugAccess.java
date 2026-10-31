// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.access;
import java.util.Map;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.system.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SoundEngine.class)
public interface SoundEngineDebugAccess { @Accessor("sources") Map<?,Sound> arcadium$sources(); }
