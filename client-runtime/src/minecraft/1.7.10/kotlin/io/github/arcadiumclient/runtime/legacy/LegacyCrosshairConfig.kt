// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Include

object LegacyCrosshairConfig : Config("crosshairtweaks.json", "Crosshair Tweaks", Category.VISUALS) {
    @JvmField @Switch(title = "Hide Crosshair", category = "General", subcategory = "Visibility")
    var disableCrosshair = false
    @JvmField @Switch(title = "Hide in Containers", category = "General", subcategory = "Visibility")
    var hideCrosshairInContainers = true
    @JvmField @Switch(title = "Hide in Third Person", category = "General", subcategory = "Visibility")
    var hideCrosshairInPerspective = false
    @JvmField @Include
    var showCrosshairInSpectator = false
    @JvmField @Switch(title = "Remove First-person Blending", category = "General", subcategory = "Blending and Opacity")
    var removeCrosshairBlendingFirstPerson = false
    @JvmField @Switch(title = "Remove Third-person Blending", category = "General", subcategory = "Blending and Opacity")
    var removeCrosshairBlendingThirdPerson = false
    @JvmField @Slider(title = "First-person Opacity", min = 0f, max = 100f, step = 1f)
    var crosshairOpacityFirstPerson = 100f
    @JvmField @Slider(title = "Third-person Opacity", min = 0f, max = 100f, step = 1f)
    var crosshairOpacityThirdPerson = 100f
}
