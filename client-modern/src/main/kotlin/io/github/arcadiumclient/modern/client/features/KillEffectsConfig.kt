// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object KillEffectsConfig : Config("arcadium-kill-effects.json", "Kill Effects", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled", description = "Play a local effect when a recently attacked target dies.")
    var enabled = false

    @JvmStatic @Dropdown(title = "Effect", options = ["Lightning", "Sparks", "Flames", "Hearts", "Ring"])
    var effect = 0

    @JvmStatic @Switch(title = "Players Only")
    var playersOnly = true

    @JvmStatic @Slider(title = "Particle Count", min = 8f, max = 64f, step = 1f)
    var count = 32

    @JvmStatic @Slider(title = "Lifetime", description = "Effect duration in seconds.", min = 0.3f, max = 2f, step = 0.1f)
    var lifetime = 0.8f

    @JvmStatic @Dropdown(title = "Color", options = ["White", "Red", "Orange", "Green", "Cyan", "Pink"])
    var color = 0

    @JvmStatic @Dropdown(title = "Sound", options = ["Off", "Thunder", "Experience Orb", "Pling"])
    var sound = 0

    @JvmStatic @Slider(title = "Sound Volume", min = 0f, max = 1f, step = 0.05f)
    var volume = 0.4f

    @JvmStatic @Slider(title = "Attack Window", description = "Confirm a target death within this many seconds of your last hit.", min = 1f, max = 10f, step = 1f)
    var attackWindow = 5

    fun initialize() {
        preload()
        eventHandler { _: TickEvent.End -> KillEffects.tick() }
    }
}
