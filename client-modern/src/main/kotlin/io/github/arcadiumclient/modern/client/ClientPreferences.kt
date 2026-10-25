// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.internal.OneConfigConfig
import io.github.arcadiumclient.modern.client.background.PanoramaCatalog

object ClientPreferences : Config("arcadium-client.json", "Client", Category.OTHER) {
    @JvmStatic @Include var setupComplete = false
    @JvmStatic @Include var dynamicFov = true
}

object MenuPreferences : Config("arcadium-menus.json", "Menus", Category.OTHER) {
    override fun initialize(byConfigManager: Boolean) {
        super.initialize(byConfigManager)
        tree?.addMetadata("hidden", true)
        org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry.unregister(id)
    }
    @JvmStatic @Include var hideNewerWorlds = false
    @JvmStatic @Include var compatibleResourcePacksOnly = true
    @JvmStatic @Include var titlePanorama = PanoramaCatalog.DEFAULT
    @JvmStatic @Include var titleBlurPercent = if (OneConfigConfig.enableBackgroundBlur) 40 else 0
    @JvmStatic @Include var titleGradient = true
    @JvmStatic @Include var titleUseResourcePackPanorama = false
}
