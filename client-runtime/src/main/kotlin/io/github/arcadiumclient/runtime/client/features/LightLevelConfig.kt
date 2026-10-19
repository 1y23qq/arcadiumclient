// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object LightLevelConfig : Config("arcadium-light-level.json", "Light Level", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled")
    var enabled = false

    @JvmStatic @Slider(title = "Radius", description = "Distance in blocks around the player.", min = 8f, max = 32f, step = 1f)
    var radius = 16

    @JvmStatic @Dropdown(title = "Light Source", options = ["Block Light", "Block and Sky Light"])
    var lightSource = 0

    @JvmStatic @Switch(title = "Low Light Outlines", description = "Outline ground cells with a light level of 7 or below.")
    var outlines = true
}
