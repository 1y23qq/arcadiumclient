// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object CuteVisualsConfig : Config("arcadium-cute-visuals.json", "Cute Visuals", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled")
    var enabled = false

    @JvmStatic @Switch(title = "Bed Sound")
    var bedSound = true

    @JvmStatic @Switch(title = "Bed Burst")
    var bedBurst = true

    @JvmStatic @Slider(title = "Bed Burst Count", min = 5f, max = 40f, step = 1f)
    var bedBurstCount = 20

    @JvmStatic @Slider(title = "Bed Burst Size", min = 0.05f, max = 0.6f, step = 0.01f)
    var bedBurstSize = 0.2f

    @JvmStatic @Slider(title = "Bed Burst Speed", min = 0.5f, max = 7f, step = 0.1f)
    var bedBurstSpeed = 2.5f

    @JvmStatic @Slider(title = "Bed Burst Lifetime", description = "Milliseconds.", min = 500f, max = 3000f, step = 100f)
    var bedBurstLifetime = 1500

    @JvmStatic @Switch(title = "Rainbow")
    var rainbow = true

    @JvmStatic @Slider(title = "Rainbow Line Width", min = 1f, max = 12f, step = 0.5f)
    var rainbowLineWidth = 5f

    @JvmStatic @Slider(title = "Rainbow Duration", description = "Milliseconds.", min = 1000f, max = 6000f, step = 200f)
    var rainbowDuration = 3000

    @JvmStatic @Switch(title = "Only While Moving")
    var onlyWhileMoving = true

    @JvmStatic @Slider(title = "Opacity", description = "Percent.", min = 20f, max = 100f, step = 5f)
    var opacity = 85

    @JvmStatic @Switch(title = "Hearts")
    var hearts = true

    @JvmStatic @Slider(title = "Hearts Spawn Rate", description = "Milliseconds.", min = 50f, max = 500f, step = 10f)
    var heartsSpawnRate = 200

    @JvmStatic @Slider(title = "Hearts Lifetime", description = "Milliseconds.", min = 500f, max = 4000f, step = 100f)
    var heartsLifetime = 1500

    @JvmStatic @Switch(title = "Dots")
    var dots = true

    @JvmStatic @Slider(title = "Dots Spawn Rate", description = "Milliseconds.", min = 20f, max = 200f, step = 10f)
    var dotsSpawnRate = 100

    @JvmStatic @Slider(title = "Dots Lifetime", description = "Milliseconds.", min = 500f, max = 5000f, step = 100f)
    var dotsLifetime = 1500

    @JvmStatic @Switch(title = "Pulse")
    var pulse = false

    fun initialize() {
        preload()
        eventHandler { _: TickEvent.End -> CuteVisuals.tick() }
    }
}
