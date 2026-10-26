// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object RodThickness : Config("arcadium-rod-thickness.json", "Rod Thickness", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled") var enabled = false
    @JvmStatic @Slider(title = "Line Thickness", description = "Fishing line width in pixels.", min = 0.1f, max = 5f, step = 0.1f)
    var thickness = 1f

    @JvmStatic fun lineWidth(): Float = if (thickness.isFinite()) thickness.coerceIn(0.1f, 5f) else 1f
}
