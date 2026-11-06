// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Number
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object StarHider : Config("arcadium-star-hider.json", "assets/oneconfig/ico/star.svg", "Star Hider", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled", description = "Change the level displayed above the XP bar.")
    var enabled = false

    @JvmStatic @Number(title = "Displayed Level", min = 0f, max = 2147483647f)
    var displayedLevel = 1000

    @JvmStatic fun display(original: Int): Int = if (enabled) displayedLevel.coerceAtLeast(0) else original
}
