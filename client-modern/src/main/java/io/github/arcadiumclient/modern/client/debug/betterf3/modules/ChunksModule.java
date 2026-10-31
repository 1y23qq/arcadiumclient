// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.modules;

import com.mojang.datafixers.DataFixUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.github.arcadiumclient.modern.client.debug.betterf3.ducks.ChunkBuilderAccess;
import io.github.arcadiumclient.modern.client.debug.betterf3.ducks.ClientChunkManagerAccess;
import io.github.arcadiumclient.modern.client.debug.betterf3.ducks.ClientChunkMapAccess;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.DebugLine;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;

/**
 * The Chunks module.
 */
public class ChunksModule extends BaseModule {

  /**
   * The total color.
   */
  public TextColor totalColor;

  /**
   * The default total color.
   */
  public final TextColor defaultTotalColor = TextColor.fromLegacyFormat(ChatFormatting.GOLD);

  /**
   * Default enabled color.
   */
  public final TextColor defaultEnabledColor = TextColor.fromLegacyFormat(ChatFormatting.GREEN);

  /**
   * Default disabled color.
   */
  public final TextColor defaultDisabledColor = TextColor.fromLegacyFormat(ChatFormatting.RED);

  /**
   * Enabled color.
   */
  public TextColor enabledColor;

  /**
   * Disabled color.
   */
  public TextColor disabledColor;

  /**
   * Instantiates a new Chunks module.
   */
  public ChunksModule() {

    this.defaultNameColor = TextColor.fromRgb(0x00aaff);
    this.defaultValueColor = TextColor.fromLegacyFormat(ChatFormatting.YELLOW);

    this.nameColor = defaultNameColor;
    this.valueColor = defaultValueColor;
    this.totalColor = this.defaultTotalColor;
    this.enabledColor = this.defaultEnabledColor;
    this.disabledColor = this.defaultDisabledColor;

    lines.add(new DebugLine("chunk_sections", "format.betterf3.total", true));
    lines.add(new DebugLine("chunk_culling"));
    lines.add(new DebugLine("pending_chunks"));
    lines.add(new DebugLine("pending_uploads"));
    lines.add(new DebugLine("available_buffers"));
    lines.add(new DebugLine("client_chunk_cache"));
    lines.add(new DebugLine("loaded_chunks"));
    lines.add(new DebugLine("loaded_chunks_server"));
    lines.add(new DebugLine("forceloaded_chunks"));
    lines.add(new DebugLine("spawn_chunks"));
    lines.add(new DebugLine("chunk_file"));

    lines.get(0).inReducedDebug = true;
    lines.get(2).inReducedDebug = true;
    lines.get(3).inReducedDebug = true;
    lines.get(4).inReducedDebug = true;
    lines.get(5).inReducedDebug = true;

  }

  /**
   * Updates the chunk module.
   *
   * @param client the Minecraft client
   */
  public void update(final Minecraft client) {

    final int totalChunks;
    if (client.levelRenderer.viewArea == null) {
      totalChunks = 0;
    } else {
      totalChunks = client.levelRenderer.viewArea.sections.length;
    }
    final int renderedChunks = client.levelRenderer.countRenderedSections();

    final SectionRenderDispatcher chunkBuilder = client.levelRenderer.getSectionRenderDispatcher();
    final ChunkBuilderAccess chunkBuilderDuck = (ChunkBuilderAccess) chunkBuilder;

    if (client.level == null) {
      return;
    }
    final ClientChunkCache clientChunkManager = client.level.getChunkSource();
    final ClientChunkManagerAccess clientChunkManagerMixin = (ClientChunkManagerAccess) clientChunkManager;
    final ClientChunkMapAccess clientChunkMapMixin = (ClientChunkMapAccess) (Object) clientChunkManagerMixin.getChunks();

    // Client Chunk Cache
    lines.get(5).value(clientChunkMapMixin.betterF3$getChunks().length());
    // Loaded Chunks
    lines.get(6).value(clientChunkManager.getLoadedChunksCount());

    final Level world = DataFixUtils.orElse(Optional.ofNullable(client.getSingleplayerServer()).flatMap(integratedServer -> Optional.ofNullable(integratedServer.getLevel(client.level.dimension()))), client.level);
    final LongSet forceLoadedChunks = world instanceof ServerLevel serverLevel ? serverLevel.getForceLoadedChunks() :
    LongSets.EMPTY_SET;

    final IntegratedServer integratedServer = client.getSingleplayerServer();
    final ServerLevel serverWorld = integratedServer != null ? integratedServer.getLevel(client.level.dimension()) : null;

    NaturalSpawner.SpawnState info = null;
    if (serverWorld != null) {
      info = serverWorld.getChunkSource().getLastSpawnState();
    }

    final String chunkCulling = client.smartCull ? I18n.get("text.betterf3.line.enabled")
      : I18n.get("text.betterf3.line.disabled");

    final List<Component> chunkValues = Arrays.asList(Utils.styledText(I18n.get("text.betterf3.line.rendered"), valueColor), Utils.styledText(I18n.get("text.betterf3.line.total"), this.totalColor),
    Utils.styledText(Integer.toString(renderedChunks), valueColor),
    Utils.styledText(Integer.toString(totalChunks), this.totalColor));

    // Chunk Sections
    lines.get(0).value(chunkValues);
    // Chunk Culling
    lines.get(1).value(Utils.styledText(chunkCulling, client.smartCull ? this.enabledColor :
    this.disabledColor));

    // TODO make this work properly with Canvas (chunkBuilderAccessor is null when using it)
    if (chunkBuilderDuck != null) {
      // Pending chunk uploads
      lines.get(2).value(client.levelRenderer.getSectionRenderDispatcher().getCompileQueueSize());

      lines.get(3).value(client.levelRenderer.getSectionRenderDispatcher().getToUpload());

      lines.get(4).value(client.levelRenderer.getSectionRenderDispatcher().getFreeBufferCount());
    }

    // Loaded Chunks (Server)
    if (serverWorld != null) {
      lines.get(7).value(serverWorld.getChunkSource().getLoadedChunksCount());
    }
    // Forceloaded Chunks
    lines.get(8).value(forceLoadedChunks.size());
    // Spawn Chunks
    if (info != null) {
      lines.get(9).value(info.getSpawnableChunkCount());
    }
    if (client.getCameraEntity() == null) {
      return;
    }

    final BlockPos blockPos = Objects.requireNonNull(client.getCameraEntity()).blockPosition();
    final ChunkPos chunkPos = new ChunkPos(blockPos);
    final String regionFile = "r.%d.%d.mca (%d, %d)".formatted(chunkPos.getRegionX(), chunkPos.getRegionZ(),
      chunkPos.getRegionLocalX(), chunkPos.getRegionLocalZ());
    lines.get(10).value(regionFile);
  }
}
