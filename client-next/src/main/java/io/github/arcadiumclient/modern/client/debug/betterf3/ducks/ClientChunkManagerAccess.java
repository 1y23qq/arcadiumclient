// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.ducks;

import net.minecraft.client.multiplayer.ClientChunkCache;

/**
 * The Chunk Cache Accessor.
 */
@SuppressWarnings("checkstyle:MethodName")
public interface ClientChunkManagerAccess {

    /**
     * Gets the chunk cache storage.
     *
     * @return The storage.
     */
    ClientChunkCache.Storage betterF3$getChunks();
}
