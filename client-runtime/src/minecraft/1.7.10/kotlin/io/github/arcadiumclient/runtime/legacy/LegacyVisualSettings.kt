// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy

import io.github.arcadiumclient.runtime.client.features.HeightOverlaySettings
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Include

object LegacyFullbrightConfig : Config("fullbright.json", "Fullbright", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled") var enable = true
    @JvmStatic @Slider(title = "Light Level", min = 0f, max = 15f, step = 1f) var lightLevel = 15
}

object LegacyFovConfig : Config("fovchanger.json", "FOV Changer", Category.VISUALS) {
    @JvmStatic @Slider(title = "Speed FOV Effects", step = 0.5f) var speed = 100f
    @JvmStatic @Include var freezing = 100f
    @JvmStatic @Slider(title = "Sprint FOV Effects", step = 0.5f) var sprint = 100f
    @JvmStatic @Slider(title = "Aiming FOV Effects", step = 0.5f) var aiming = 100f
    @JvmStatic @Slider(title = "Flying FOV Effects", step = 0.5f) var flying = 100f
    @JvmStatic @Slider(title = "Submerged FOV Effects", step = 0.5f) var submerged = 100f
}

object LegacyHeightConfig : Config("hybedwars.json", "Height Overlay", Category.OTHER), HeightOverlaySettings {
    @JvmStatic @Switch(title = "Enabled") var heightOverlay = true
    @JvmStatic @Slider(title = "Opacity", min = 0f, max = 100f, step = 1f) var heightOverlayOpacity = 67
    @JvmStatic @Switch(title = "Use Minemen Height Limit") var minemenHeightLimit = true
    @JvmStatic @Switch(title = "Custom Height Limit") var debugModeEnabled = false
    @JvmStatic @Slider(title = "Height Limit", min = 1f, max = 256f, step = 1f) var debugModeHeight = 100
    override fun `arcadium$minemenHeightLimitEnabled`() = minemenHeightLimit
    override fun `arcadium$heightOverlayEnabled`() = heightOverlay
}
