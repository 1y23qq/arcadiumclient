// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.modules;

import com.electronwill.nightconfig.core.Config;
import com.mojang.datafixers.DataFixUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.github.arcadiumclient.modern.client.debug.betterf3.ducks.ClientChunkManagerAccess;
import io.github.arcadiumclient.modern.client.debug.betterf3.ducks.ClientChunkMapAccess;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.DebugLine;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
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
    private static final int LINE_CHUNK_SECTIONS = 0;
    private static final int LINE_CHUNK_CULLING = 1;
    private static final int LINE_PENDING_CHUNKS = 2;
    private static final int LINE_PENDING_UPLOADS = 3;
    private static final int LINE_AVAILABLE_BUFFERS = 4;
    private static final int LINE_CLIENT_CHUNK_CACHE = 5;
    private static final int LINE_LOADED_CHUNKS = 6;
    private static final int LINE_LOADED_CHUNKS_SERVER = 7;
    private static final int LINE_FORCELOADED_CHUNKS = 8;
    private static final int LINE_SPAWN_CHUNKS = 9;
    private static final int LINE_CHUNK_FILE = 10;

    /**
     * The total color.
     */
    public TextColor totalColor;

    /**
     * The default total color.
     */
    public final TextColor defaultTotalColor = legacyColor(ChatFormatting.GOLD);

    /**
     * Default enabled color.
     */
    public final TextColor defaultEnabledColor = legacyColor(ChatFormatting.GREEN);

    /**
     * Default disabled color.
     */
    public final TextColor defaultDisabledColor = legacyColor(ChatFormatting.RED);

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
        this.defaultValueColor = legacyColor(ChatFormatting.YELLOW);

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

        lines.get(LINE_CHUNK_SECTIONS).inReducedDebug = true;
        lines.get(LINE_PENDING_CHUNKS).inReducedDebug = true;
        lines.get(LINE_PENDING_UPLOADS).inReducedDebug = true;
        lines.get(LINE_AVAILABLE_BUFFERS).inReducedDebug = true;
        lines.get(LINE_CLIENT_CHUNK_CACHE).inReducedDebug = true;
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
            totalChunks = client.levelRenderer.viewArea.sections.size();
        }
        final int renderedChunks = client.levelExtractor.countRenderedSections();

        final SectionRenderDispatcher chunkBuilder = client.levelRenderer.sectionRenderDispatcher();

        if (client.level == null) {
            return;
        }
        final ClientLevel clientLevel = client.level;
        final ClientChunkCache clientChunkManager = clientLevel.getChunkSource();
        final ClientChunkManagerAccess clientChunkManagerMixin = (ClientChunkManagerAccess) clientChunkManager;
        final ClientChunkMapAccess clientChunkMapMixin =
                (ClientChunkMapAccess) (Object) clientChunkManagerMixin.betterF3$getChunks();

        // Client Chunk Cache
        lines.get(LINE_CLIENT_CHUNK_CACHE)
                .value(clientChunkMapMixin.betterF3$getChunks().length());
        // Loaded Chunks
        lines.get(LINE_LOADED_CHUNKS).value(clientChunkManager.getLoadedChunksCount());

        final Level world = DataFixUtils.orElse(
                Optional.ofNullable(client.getSingleplayerServer())
                        .flatMap(integratedServer ->
                                Optional.ofNullable(integratedServer.getLevel(clientLevel.dimension()))),
                clientLevel);
        final LongSet forceLoadedChunks =
                world instanceof ServerLevel serverLevel ? serverLevel.getForceLoadedChunks() : LongSets.EMPTY_SET;

        final IntegratedServer integratedServer = client.getSingleplayerServer();
        final ServerLevel serverWorld =
                integratedServer != null ? integratedServer.getLevel(clientLevel.dimension()) : null;

        NaturalSpawner.SpawnState info = null;
        if (serverWorld != null) {
            info = serverWorld.getChunkSource().getLastSpawnState();
        }

        final String chunkCulling =
                client.smartCull ? I18n.get("text.betterf3.line.enabled") : I18n.get("text.betterf3.line.disabled");

        final List<Component> chunkValues = Arrays.asList(
                Utils.styledText(I18n.get("text.betterf3.line.rendered"), valueColor),
                Utils.styledText(I18n.get("text.betterf3.line.total"), this.totalColor),
                Utils.styledText(Integer.toString(renderedChunks), valueColor),
                Utils.styledText(Integer.toString(totalChunks), this.totalColor));

        // Chunk Sections
        lines.get(LINE_CHUNK_SECTIONS).value(chunkValues);
        // Chunk Culling
        lines.get(LINE_CHUNK_CULLING)
                .value(Utils.styledText(chunkCulling, client.smartCull ? this.enabledColor : this.disabledColor));

        // TODO make this work properly with Canvas (chunkBuilderAccessor is null when using it)
        if (chunkBuilder != null) {
            final SectionRenderDispatcher nonNullChunkBuilder = Objects.requireNonNull(chunkBuilder);
            // Pending chunk uploads
            lines.get(LINE_PENDING_CHUNKS).value(nonNullChunkBuilder.getCompileQueueSize());
            lines.get(LINE_PENDING_UPLOADS).value(nonNullChunkBuilder.getCompileQueueSize());
            lines.get(LINE_AVAILABLE_BUFFERS).value(nonNullChunkBuilder.getFreeBufferCount());
        }

        // Loaded Chunks (Server)
        if (serverWorld != null) {
            lines.get(LINE_LOADED_CHUNKS_SERVER)
                    .value(serverWorld.getChunkSource().getLoadedChunksCount());
        }
        // Forceloaded Chunks
        lines.get(LINE_FORCELOADED_CHUNKS).value(forceLoadedChunks.size());
        // Spawn Chunks
        if (info != null) {
            lines.get(LINE_SPAWN_CHUNKS).value(info.getSpawnableChunkCount());
        }
        if (client.getCameraEntity() == null) {
            return;
        }

        final BlockPos blockPos =
                Objects.requireNonNull(client.getCameraEntity()).blockPosition();
        final ChunkPos chunkPos = ChunkPos.containing(blockPos);
        final String regionFile = "r.%d.%d.mca (%d, %d)"
                .formatted(
                        chunkPos.getRegionX(),
                        chunkPos.getRegionZ(),
                        chunkPos.getRegionLocalX(),
                        chunkPos.getRegionLocalZ());
        lines.get(LINE_CHUNK_FILE).value(regionFile);
    }

    @Override
    protected void loadModuleConfig(final Config moduleConfig) {
        this.enabledColor = readColor(moduleConfig, "chunks_enabled_color", this.defaultEnabledColor);
        this.disabledColor = readColor(moduleConfig, "chunks_disabled_color", this.defaultDisabledColor);
        this.totalColor = readColor(moduleConfig, "total_chunks_color", this.defaultTotalColor);
    }

    @Override
    protected void saveModuleConfig(final Config moduleConfig) {
        writeColor(moduleConfig, "chunks_enabled_color", this.enabledColor);
        writeColor(moduleConfig, "chunks_disabled_color", this.disabledColor);
        writeColor(moduleConfig, "total_chunks_color", this.totalColor);
    }
}
