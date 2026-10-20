// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object LegacyCrosshairConfig : Config("crosshairtweaks.json", "Crosshair Tweaks", Category.VISUALS) {
    @JvmField @Switch(title = "Hide Crosshair", category = "General", subcategory = "Visibility")
    var disableCrosshair = false
    @JvmField @Switch(title = "Hide in Containers", category = "General", subcategory = "Visibility")
    var hideCrosshairInContainers = false
    @JvmField @Switch(title = "Hide in Third Person", category = "General", subcategory = "Visibility")
    var hideCrosshairInPerspective = true
    @JvmField @Switch(title = "Show in Spectator", category = "General", subcategory = "Visibility")
    var showCrosshairInSpectator = false
    @JvmField @Slider(title = "First-person Opacity", min = 0f, max = 100f, step = 1f)
    var crosshairOpacityFirstPerson = 100f
    @JvmField @Slider(title = "Third-person Opacity", min = 0f, max = 100f, step = 1f)
    var crosshairOpacityThirdPerson = 100f
}
