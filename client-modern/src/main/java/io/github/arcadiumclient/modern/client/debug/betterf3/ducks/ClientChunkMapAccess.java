// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.ducks;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The Client Chunk Map Accessor.
 */
@SuppressWarnings("checkstyle:MethodName")
public interface ClientChunkMapAccess {

  /**
   * Gets the chunk array.
   *
   * @return Gets Chunks
   */
  AtomicReferenceArray<LevelChunk> betterF3$getChunks();

}
