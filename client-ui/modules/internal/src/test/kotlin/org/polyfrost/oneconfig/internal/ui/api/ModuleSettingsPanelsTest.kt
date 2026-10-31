// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.ui.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModuleSettingsPanelsTest {
    @Test fun panelRegistrationAndNavigationUseTheSameModuleId() {
        ModuleSettingsPanels.register("test-settings063.json", "Layout") { }
        ModuleSettingsPanels.selectPanel("test-settings063", "Layout")
        assertEquals("Layout", ModuleSettingsPanels.selectedTitle("test-settings063.json"))
        assertEquals(1, ModuleSettingsPanels.forModule("test-settings063").size)
        ModuleSettingsPanels.register("test-settings063.json", "Layout") { }
        assertEquals(1, ModuleSettingsPanels.forModule("test-settings063").size)
        ModuleSettingsPanels.selectPanel("test-settings063.json", null)
        assertNull(ModuleSettingsPanels.selectedTitle("test-settings063"))
        assertThrows(IllegalArgumentException::class.java) { ModuleSettingsPanels.selectPanel("test-settings063", "Missing") }
    }
}
