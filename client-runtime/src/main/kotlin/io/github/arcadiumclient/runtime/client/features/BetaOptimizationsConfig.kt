// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms
import io.github.arcadiumclient.runtime.client.optimizations.WorldRenderScale
import io.github.arcadiumclient.runtime.client.optimizations.RetainedHudBuffers
import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline
import io.github.arcadiumclient.runtime.client.optimizations.GpuEquipmentPipeline
import io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.internal.ui.api.BetaOptimizationPage
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import io.github.arcadiumclient.runtime.client.branding.NametagLayout

object BetaOptimizationsConfig : Config("arcadium-optimizations-beta.json", "Optimizations (Beta)", Category.OTHER) {
    @JvmStatic @Switch(title = "GPU Model Transforms", description = "Build and sort standard player part matrices on the GPU while keeping Argentum batching. Requires OpenGL 4.3. Custom parts use the original renderer.", category = "Players")
    var gpuModelTransforms = false

    @JvmStatic @Switch(title = "GPU Player Pipeline", description = "Build standard player poses from compact snapshots and submit their parts in bulk. Unsupported models and layers use the original renderer. Requires OpenGL 4.3.", category = "Players")
    var gpuPlayerPipeline = false

    @JvmStatic @Switch(title = "GPU Equipment Pipeline", description = "Build standard player armor poses and submit armor parts in bulk on the GPU. Native texture selection, leather colors and enchantment effects are preserved. Requires OpenGL 4.3.", category = "Equipment")
    var gpuEquipmentPipeline = false

    @JvmStatic @Slider(title = "Minimum Visible Players", description = "Use GPU transforms only when at least this many players were rendered in the last batch, to avoid dispatch overhead in small scenes.", min = 1f, max = 128f, step = 1f, category = "Players")
    var minimumPlayers = 24

    @JvmStatic @Switch(title = "GPU Name Tags", description = "Keep name tag shapes on the GPU and draw their text and backgrounds with compact per-label commands. Requires OpenGL 4.3.", category = "Name Tags")
    var gpuNameTags = false

    @JvmStatic @Switch(title = "Retained Name Tag Scenes", description = "Keep unchanged name tag draw plans and update their position and camera facing every frame. Requires GPU Name Tags. Unsupported labels keep their original preparation.", category = "Name Tags")
    var retainedNameTagScenes = false

    @JvmStatic @Slider(title = "Minimum Visible Labels", description = "Use the GPU path only for crowded views. All labels keep updating every frame.", min = 1f, max = 128f, step = 1f, category = "Name Tags")
    var minimumGpuLabels = 16

    @JvmStatic @Switch(title = "Retained HUD Batches", description = "Keep unchanged Argentum HUD text batches in GPU memory instead of uploading them every frame. Animated text, chat fading and changed settings still update immediately.", category = "HUD")
    var retainedHudBatches = false

    @JvmStatic @Switch(title = "World Render Scale", description = "Render the world and held items at a lower resolution. HUD and menus stay at full resolution. This reduces image detail and helps GPU-limited scenes.", category = "World")
    var worldRenderScale = false

    @JvmStatic @Slider(title = "World Resolution", description = "Percentage of the window resolution used for the world. 100% keeps the original resolution.", min = 50f, max = 100f, step = 5f, category = "World")
    var worldResolution = 75

    init { addDependency("retainedNameTagScenes", "gpuNameTags") }

    fun initialize() {
        preload()
        eventHandler { _: TickEvent.End -> NametagLayout.optimized = gpuNameTags || gpuPlayerPipeline }
        BetaOptimizationPage.register(id) {
            listOf(GpuModelTransforms.status(), GpuPlayerPipeline.status(), GpuEquipmentPipeline.status(), GpuNameTags.status(), RetainedNameTags.status(), RetainedHudBuffers.status(), WorldRenderScale.status())
        }
    }
}
