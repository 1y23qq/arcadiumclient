// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms
import io.github.arcadiumclient.runtime.client.optimizations.WorldRenderScale
import io.github.arcadiumclient.runtime.client.optimizations.RetainedHudBuffers
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.internal.ui.api.BetaOptimizationPage

object BetaOptimizationsConfig : Config("arcadium-optimizations-beta.json", "Optimizations (Beta)", Category.OTHER) {
    @JvmStatic @Switch(title = "GPU Model Transforms", description = "Build and sort standard player part matrices on the GPU while keeping Argentum batching. Requires OpenGL 4.3. Custom parts use the original renderer.", category = "Players")
    var gpuModelTransforms = false

    @JvmStatic @Slider(title = "Minimum Visible Players", description = "Use GPU transforms only when at least this many players were rendered in the last batch, to avoid dispatch overhead in small scenes.", min = 1f, max = 128f, step = 1f, category = "Players")
    var minimumPlayers = 24

    @JvmStatic @Switch(title = "Retained HUD Batches", description = "Keep unchanged Argentum HUD text batches in GPU memory instead of uploading them every frame. Animated text, chat fading and changed settings still update immediately.", category = "HUD")
    var retainedHudBatches = false

    @JvmStatic @Switch(title = "World Render Scale", description = "Render the world and held items at a lower resolution. HUD and menus stay at full resolution. This reduces image detail and helps GPU-limited scenes.", category = "World")
    var worldRenderScale = false

    @JvmStatic @Slider(title = "World Resolution", description = "Percentage of the window resolution used for the world. 100% keeps the original resolution.", min = 50f, max = 100f, step = 5f, category = "World")
    var worldResolution = 75

    fun initialize() {
        preload()
        BetaOptimizationPage.register(id) {
            listOf(GpuModelTransforms.status(), RetainedHudBuffers.status(), WorldRenderScale.status())
        }
    }
}
