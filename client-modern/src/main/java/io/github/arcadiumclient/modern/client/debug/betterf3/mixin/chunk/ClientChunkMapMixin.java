// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.mixin.chunk;

import java.util.concurrent.atomic.AtomicReferenceArray;
import io.github.arcadiumclient.modern.client.debug.betterf3.ducks.ClientChunkMapAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin to access volatile "chunks" field in ClientChunkManager.
 */
@Mixin(ClientChunkCache.Storage.class)
public class ClientChunkMapMixin implements ClientChunkMapAccess {

  @Final
  @Shadow AtomicReferenceArray<LevelChunk> chunks;

  @Override
  public AtomicReferenceArray<LevelChunk> betterF3$getChunks() {
    return this.chunks;
  }
}
