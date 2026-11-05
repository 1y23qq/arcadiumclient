// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms
import io.github.arcadiumclient.runtime.client.optimizations.WorldRenderScale
import io.github.arcadiumclient.runtime.client.optimizations.RetainedHudBuffers
import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline
import io.github.arcadiumclient.runtime.client.optimizations.GpuEquipmentPipeline
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands
import io.github.arcadiumclient.runtime.client.optimizations.GpuSkinLayers
import io.github.arcadiumclient.runtime.client.optimizations.NativePlayerPosePipeline
import io.github.arcadiumclient.runtime.client.optimizations.GpuResidentPlayerState
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline
import io.github.arcadiumclient.runtime.client.optimizations.GpuResolvedInstanceCache
import io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags
import io.github.arcadiumclient.runtime.client.optimizations.GpuParticles
import io.github.arcadiumclient.runtime.client.optimizations.FastInvisibleHolograms
import io.github.arcadiumclient.runtime.client.optimizations.ScoreboardPreparation
import io.github.arcadiumclient.runtime.client.optimizations.FastFramePresentation
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl
import io.github.arcadiumclient.runtime.client.optimizations.EfficientMotionBlur
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.internal.ui.api.BetaOptimizationPage
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import io.github.arcadiumclient.runtime.client.branding.NametagLayout

object BetaOptimizationsConfig : Config("arcadium-optimizations-beta.json", "Optimizations (Beta)", Category.OTHER) {
    @JvmStatic @Switch(title = "Cached Name Masking", description = "Reuse unchanged Nick Hider results and avoid regular expressions when checking entity names. Name and nickname changes take effect immediately.", category = "Hypixel")
    var cachedNameMasking = true

    @JvmStatic @Switch(title = "Cached Font Metrics", description = "Reuse exact text widths for the built-in fonts. Font, Unicode and resource changes invalidate the cache; custom glyphs keep their original measurement.", category = "Hypixel")
    var cachedFontMetrics = true

    @JvmStatic @Switch(title = "Batched Vanilla Font", description = "Batch built-in vanilla font glyphs without using the resource pack's font. Formatting, shadows and native name tag/HUD batching are preserved.", category = "Hypixel")
    var batchedVanillaFont = true

    @JvmStatic @Switch(title = "GPU Name Tags", description = "Keep name tag shapes on the GPU and draw their text and backgrounds with compact per-label commands. Requires OpenGL 4.3.", category = "Hypixel")
    var gpuNameTags = false

    @JvmStatic @Switch(title = "Retained Name Tag Scenes", description = "Keep unchanged name tag draw plans and update their position and camera facing every frame. Requires GPU Name Tags. Unsupported labels keep their original preparation.", category = "Hypixel")
    var retainedNameTagScenes = false

    @JvmStatic @Switch(title = "Compact Name Tag Preparation", description = "Reuse unchanged name tag material bindings, billboard orientation and draw commands. Every label position, light value and visible glyph still updates normally.", category = "Hypixel")
    var compactNameTagPreparation = false

    @JvmStatic @Switch(title = "Invisible Hologram Fast Path", description = "Skip unused model preparation for invisible, unequipped vanilla armor stands. Their name tags keep the original renderer; visible stands, equipment and custom renderers use the original path.", category = "Hypixel")
    var fastInvisibleHolograms = false

    @JvmStatic @Switch(title = "Reduced Render Hook Allocations", description = "Reduce temporary allocations in supported model, particle and cosmetic render hooks. Custom hooks keep their original behavior.", category = "Hypixel")
    var reduceRenderHookAllocations = false

    @JvmStatic @Switch(title = "Optimized Motion Blur", description = "Reuse private hybrid motion blur history buffers instead of copying unchanged pixels. Motion blur strength and appearance stay the same. Modified or unsupported shaders keep their original path.", category = "Hypixel")
    var efficientMotionBlur = false

    @JvmStatic @Switch(title = "Retained Scoreboard Preparation", description = "Share the current score ordering and point-visibility decision across sidebar rows. Score changes, live settings and every native draw still update immediately.", category = "Hypixel")
    var retainedScoreboardPreparation = false

    @JvmStatic @Switch(title = "GPU Particle Batches", description = "Expand standard particle billboards on the GPU. Keeps particle counts, animation, custom size, fading and static colors. Mixed or unsupported batches use the original path. Requires OpenGL 3.3.", category = "Hypixel")
    var gpuParticles = false

    @JvmStatic @Switch(title = "Retained Particle Recipes", description = "Reuse stable vanilla spell-particle appearance between game ticks. Positions and camera-facing geometry still update every frame; animated colors and custom particles use the original preparation.", category = "Hypixel")
    var retainedParticleRecipes = false

    @JvmStatic @Switch(title = "Retained HUD Batches", description = "Keep unchanged Argentum HUD text batches in GPU memory instead of uploading them every frame. Animated text, chat fading and changed settings still update immediately.", category = "Hypixel")
    var retainedHudBatches = false

    @JvmStatic @Slider(title = "Minimum Visible Labels", description = "Use the GPU path only for crowded views. All labels keep updating every frame.", min = 1f, max = 128f, step = 1f, category = "Hypixel")
    var minimumGpuLabels = 1

    @JvmStatic @Slider(title = "Minimum Visible Particles", description = "Keep small particle scenes on the original renderer.", min = 1f, max = 512f, step = 1f, category = "Hypixel")
    var minimumGpuParticles = 1

    @JvmStatic @Switch(title = "GPU Model Transforms", description = "Build and sort standard player part matrices on the GPU while keeping Argentum batching. Requires OpenGL 4.3. Custom parts use the original renderer.", category = "Players")
    var gpuModelTransforms = false

    @JvmStatic @Switch(title = "GPU Player Pipeline", description = "Build supported player poses on the GPU while preserving skin layers and animations. Mod callbacks can use evaluated-part GPU capture. Unsupported geometry keeps its original renderer. Requires OpenGL 4.3.", category = "Players")
    var gpuPlayerPipeline = false

    @JvmStatic @Switch(title = "Resident Player State", description = "Reuse compact player data and repeated instance uploads within a render batch. Enable GPU Player Pipeline or GPU Equipment Pipeline first. Busy or unsupported buffers use normal streaming without waiting.", category = "Players")
    var residentPlayerState = false

    @JvmStatic @Switch(title = "GPU Voxel Skin Layers", description = "Batch supported 3D skin meshes without reducing their detail. Enable 3D Skin and GPU Player Pipeline first. Custom shapes or render states keep the original skin renderer. Requires OpenGL 4.3.", category = "Players")
    var gpuVoxelSkinLayers = false

    @JvmStatic @Switch(title = "GPU Equipment Pipeline", description = "Build standard player armor poses and submit armor parts in bulk on the GPU. Native texture selection, leather colors and enchantment effects are preserved. Requires OpenGL 4.3.", category = "Players")
    var gpuEquipmentPipeline = false

    @JvmStatic @Slider(title = "Minimum Visible Players", description = "Use GPU transforms only when at least this many different players were rendered in the previous frame, to avoid dispatch overhead in small scenes.", min = 1f, max = 128f, step = 1f, category = "Players")
    var minimumPlayers = 1

    @JvmStatic @Switch(title = "Fast Frame Presentation", description = "Cache the final full-screen frame quad while keeping native colors, resolution and frame timing. Unsupported render states use the original path.", category = "Display")
    var directFramebufferPresentation = false

    @JvmStatic @Switch(title = "GPU Terrain Commands", description = "Keep opaque terrain command data on the GPU while preserving native section visibility and full terrain detail. Requires OpenGL 4.3 and indirect-count support. Unsupported passes keep the original renderer.", category = "Display")
    var gpuTerrainCommands = false

    @JvmStatic @Switch(title = "World Render Scale", description = "Render the world and held items at a lower resolution. HUD and menus stay at full resolution. This reduces image detail and helps GPU-limited scenes.", category = "Display")
    var worldRenderScale = false

    @JvmStatic @Slider(title = "World Resolution", description = "Percentage of the window resolution used for the world. 100% keeps the original resolution.", min = 50f, max = 100f, step = 5f, category = "Display")
    var worldResolution = 75

    init {
        addDependency("retainedNameTagScenes", "gpuNameTags")
        addDependency("compactNameTagPreparation", "retainedNameTagScenes")
        addDependency("retainedParticleRecipes", "gpuParticles")
        addDependency("minimumGpuParticles", "gpuParticles")
    }

    fun initialize() {
        preload()
        syncTextOptions()
        RenderHookAllocationControl.enabled = reduceRenderHookAllocations
        GpuTerrainCommands.enabled = gpuTerrainCommands
        GpuResidentPlayerState.enabled = residentPlayerState
        GpuResolvedInstanceCache.enabled = residentPlayerState
        GpuVoxelSkinPipeline.enabled = gpuVoxelSkinLayers
        eventHandler { _: TickEvent.End ->
            NametagLayout.optimized = gpuNameTags || gpuPlayerPipeline
            RenderHookAllocationControl.enabled = reduceRenderHookAllocations
            GpuTerrainCommands.enabled = gpuTerrainCommands
            GpuResidentPlayerState.enabled = residentPlayerState
            GpuResolvedInstanceCache.enabled = residentPlayerState
            GpuVoxelSkinPipeline.enabled = gpuVoxelSkinLayers
            syncTextOptions()
        }
        BetaOptimizationPage.register(id) {
            listOf(GpuModelTransforms.status(), GpuPlayerPipeline.status(), GpuEquipmentPipeline.status(), NativePlayerPosePipeline.status(), ModernArmorGlintPipeline.status(), GpuResidentPlayerState.status(), GpuResolvedInstanceCache.status(), GpuVoxelSkinPipeline.status(), GpuSkinLayers.status(), GpuTerrainCommands.status(), GpuNameTags.status(), RetainedNameTags.status(), FastInvisibleHolograms.status(), RenderHookAllocationControl.status(), EfficientMotionBlur.status(), GpuParticles.status(), ScoreboardPreparation.status(), FastFramePresentation.status(), RetainedHudBuffers.status(), WorldRenderScale.status())
        }
        io.github.arcadiumclient.runtime.client.diagnostics.PerformanceCapture.initialize()
    }

    private fun syncTextOptions() {
        if (org.polyfrost.oneconfig.internal.TextOptimizationOptions.vanillaFontBatching != batchedVanillaFont) GpuNameTags.invalidateAll()
        org.polyfrost.oneconfig.internal.TextOptimizationOptions.nameMaskCache = cachedNameMasking
        org.polyfrost.oneconfig.internal.TextOptimizationOptions.fontMetricCache = cachedFontMetrics
        org.polyfrost.oneconfig.internal.TextOptimizationOptions.vanillaFontBatching = batchedVanillaFont
    }
}
