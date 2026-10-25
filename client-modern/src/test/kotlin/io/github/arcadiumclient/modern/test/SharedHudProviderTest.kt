// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.oneconfig.api.hud.v1.Hud

class SharedHudProviderTest {
    @Test fun savedLegacyProviderNamesResolveToNativeModernHuds() {
        for (name in listOf("HealthHud", "HeightLimitHud", "ToggleSprintHud")) {
            val saved = "io.github.arcadiumclient.runtime.client.features.$name"
            val loaded = Class.forName(saved, false, javaClass.classLoader).asSubclass(Hud::class.java)
            assertEquals("io.github.arcadiumclient.modern.client.features.$name", loaded.superclass.name)
        }
    }
}
