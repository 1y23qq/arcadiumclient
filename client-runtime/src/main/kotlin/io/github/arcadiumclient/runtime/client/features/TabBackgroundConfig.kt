// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object TabBackgroundConfig : Config("arcadium-tab-background.json", "Tab Background", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled")
    var enabled = true

    @JvmStatic @Slider(title = "Opacity (%)", description = "0% hides the background. 100% is opaque.", min = 0f, max = 100f, step = 1f)
    var opacity = 50f
}
