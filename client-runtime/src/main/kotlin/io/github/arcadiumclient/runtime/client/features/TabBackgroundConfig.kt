// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Color

object TabBackgroundConfig : Config("arcadium-tab-background.json", "Tab Background", Category.VISUALS) {
    private var drawingOwnName = false

    @JvmStatic @Switch(title = "Enabled")
    var enabled = true

    @JvmStatic @Slider(title = "Opacity (%)", description = "0% hides the background. 100% is opaque.", min = 0f, max = 100f, step = 1f)
    var opacity = 50f

    @JvmStatic @Color(title = "Background Color", alpha = false)
    var backgroundColor = java.awt.Color.BLACK

    @JvmStatic @Switch(title = "Highlight Own Name", subcategory = "Own Name")
    var highlightOwnName = false

    @JvmStatic @Color(title = "Own Name Background Color", alpha = false, subcategory = "Own Name")
    var ownNameColor = java.awt.Color(58, 160, 255)

    @JvmStatic @Slider(title = "Own Name Opacity (%)", min = 0f, max = 100f, step = 1f, subcategory = "Own Name")
    var ownNameOpacity = 50f

    @JvmStatic fun drawingOwnName(value: Boolean) { drawingOwnName = value }
    @JvmStatic fun rowBackground(original: Int) = background(original, drawingOwnName)

    @JvmStatic fun background(original: Int, ownName: Boolean = false): Int {
        if (!enabled) return original
        val highlight = ownName && highlightOwnName
        return BackgroundOpacity.withAlpha(if (highlight) ownNameColor.rgb else backgroundColor.rgb,
            if (highlight) ownNameOpacity else opacity)
    }
}
