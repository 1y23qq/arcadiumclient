// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy

import io.github.arcadiumclient.runtime.client.features.HeightOverlaySettings
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object LegacyFullbrightConfig : Config("fullbright.json", "Fullbright", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled") var enabled = false
    @JvmStatic @Slider(title = "Brightness", min = 0f, max = 100f, step = 1f) var strength = 100
}

object LegacyFovConfig : Config("fovchanger.json", "FOV Changer", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled") var enabled = false
    @JvmStatic @Slider(title = "FOV", min = 30f, max = 110f, step = 1f) var normal = 70
    @JvmStatic @Slider(title = "Sprinting FOV", min = 30f, max = 130f, step = 1f) var sprinting = 80
    @JvmStatic @Slider(title = "Flying FOV", min = 30f, max = 130f, step = 1f) var flying = 80
    @JvmStatic @Slider(title = "Bow FOV", min = 30f, max = 110f, step = 1f) var bow = 60
}

object LegacyHeightConfig : Config("hybedwars.json", "Height Overlay", Category.OTHER), HeightOverlaySettings {
    @JvmStatic @Switch(title = "Enabled") var enabled = false
    @JvmStatic @Switch(title = "Use Minemen Height Limit") var minemenLimit = true
    @JvmStatic @Switch(title = "Use Vanilla Height Limit") var vanillaLimit = true
    @JvmStatic @Slider(title = "Height Limit", min = 1f, max = 256f, step = 1f) var customLimit = 256
    override fun `arcadium$minemenHeightLimitEnabled`() = minemenLimit
    override fun `arcadium$heightOverlayEnabled`() = enabled
}
