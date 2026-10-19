// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object ClientPreferences : Config("arcadium-client.json", "Client", Category.OTHER) {
    @JvmStatic @Include var setupComplete = false
    @JvmStatic @Switch(title = "Dynamic FOV") var dynamicFov = true
}

object MenuPreferences : Config("arcadium-menus.json", "Menus", Category.OTHER) {
    @JvmStatic @Include var hideNewerWorlds = false
}
