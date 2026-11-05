// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import java.util.Set;
import net.minecraft.world.World;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface PlayerPipeline072WorldAccess {
    @Accessor("tickingChunks") Set<ChunkPos> player072$tickingChunks();
}
