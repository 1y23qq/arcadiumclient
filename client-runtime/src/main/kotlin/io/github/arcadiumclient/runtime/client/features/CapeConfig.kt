// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import io.github.arcadiumclient.runtime.client.capes.CapeCatalog
import io.github.arcadiumclient.runtime.client.capes.LocalCape
import io.github.arcadiumclient.runtime.client.gui.CapePresetSettings
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.internal.ui.api.ModuleSettingsPanels

object CapeConfig : Config("arcadium-cape.json", "assets/arcadium/icons/cape.svg", "Cape", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled", description = "Show the selected cape on your own player, only on this client.")
    var enabled = false

    @JvmStatic @Include var preset = "migrator"

    fun initialize() {
        preload()
        ModuleSettingsPanels.register(id, "Capes") { CapePresetSettings() }
        LocalCape.initialize()
    }

    fun select(id: String) {
        if (CapeCatalog.find(id) == null || preset == id) return
        preset = id
        save()
        LocalCape.changed()
    }
}
