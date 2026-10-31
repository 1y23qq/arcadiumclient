// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.mixin.chunk;

import java.util.Queue;
import io.github.arcadiumclient.modern.client.debug.betterf3.ducks.ChunkBuilderAccess;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;


/**
 * Mixin to access volatile "chunks" field in ClientChunkManager.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class ChunkBuilderMixin implements ChunkBuilderAccess {

  @Shadow @Final private Queue<Runnable> toUpload;

  @Final
  @Shadow private SectionBufferBuilderPool bufferPool;

  /**
   * Gets the number of cached chunks.
   *
   * @return the number of cached chunks
   */
  @SuppressWarnings("checkstyle:MethodName")
  @Shadow
  public abstract int getCompileQueueSize();

  @Override
  public int betterF3$getQueuedTaskCount() {
    return this.getCompileQueueSize();
  }

  @Override
  public Queue<Runnable> betterF3$getUploadQueue() {
    return this.toUpload;
  }

  @Override
  public int betterF3$getBufferCount() {
    return this.bufferPool.getFreeBufferCount();
  }
}
