// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.ui.themes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.internal.ThemeConfig

class ThemeProfileTest {
    @Test
    fun savedProfilesRestoreTheActualActiveThemeAndAccent() {
        val profile = "oc_test_theme_profile050"
        ConfigManager.active()
        ConfigManager.openProfile("")
        if (profile in ConfigManager.profiles()) ConfigManager.deleteProfile(profile)
        val config = ThemeConfig()
        config.preload()
        val previous = ThemeConfig.activeTheme
        val previousPreset = ThemeConfig.gradientPreset
        try {
            activate(PolyGlassDark)
            config.tree.getProp("gradientPreset").setAs(0)
            ConfigManager.active().saveAll()

            ConfigManager.createProfile(profile)
            activate(PolyGlassLight)
            config.tree.getProp("gradientPreset").setAs(17)
            ConfigManager.active().saveAll()

            ConfigManager.openProfile("")
            assertEquals(PolyGlassDark.name, ThemeConfig.activeTheme)
            assertEquals(PolyGlassDark.name, activeThemeName())
            assertEquals(GradientPresets.get(0).name(), ThemeAccent.snapshot().preset())

            ConfigManager.openProfile(profile)
            assertEquals(PolyGlassLight.name, ThemeConfig.activeTheme)
            assertEquals(PolyGlassLight.name, activeThemeName())
            assertEquals(GradientPresets.get(17).name(), ThemeAccent.snapshot().preset())
        } finally {
            ConfigManager.openProfile("")
            ThemeConfig.activeTheme = previous
            config.tree.getProp("gradientPreset").setAs(previousPreset)
            ThemeRegistry.loadFromConfig()
            ConfigManager.active().saveAll()
            if (profile in ConfigManager.profiles()) ConfigManager.deleteProfile(profile)
        }
    }

    private fun activeThemeName(): String? = (ThemeRegistry.javaClass.methods.single {
        it.name.startsWith("getActiveTheme")
    }.invoke(ThemeRegistry) as UITheme?)?.name

    private fun activate(theme: UITheme) {
        ThemeConfig.activeTheme = theme.name
        ThemeRegistry.loadFromConfig()
        ConfigManager.active().save("themes.json")
    }
}
