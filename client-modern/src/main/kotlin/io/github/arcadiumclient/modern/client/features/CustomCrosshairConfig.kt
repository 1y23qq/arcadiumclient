// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object CustomCrosshairConfig : Config("arcadium-custom-crosshair.json", "Custom Crosshair", Category.VISUALS) {
    @JvmStatic @Switch(title = "Show Crosshair in Third Person")
    var thirdPerson = false
}
