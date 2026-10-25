// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test

import com.google.gson.JsonParser
import io.github.arcadiumclient.modern.client.features.LegacyHeightLimitSettings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LegacyHeightLimitSettingsTest {
    @TempDir lateinit var folder: Path
    @Test fun sharingMinemenSettingPreservesOtherVersionOptions() {
        val file = folder.resolve("hybedwars.json")
        val original = """{"minemenHeightLimit":false,"oldMapOption":{"value":23},"heightOverlay":true}"""
        Files.writeString(file, original)
        assertEquals(false, LegacyHeightLimitSettings.read(file))
        LegacyHeightLimitSettings.write(file, true)
        val changed = JsonParser.parseString(Files.readString(file)).asJsonObject
        assertTrue(changed["minemenHeightLimit"].asBoolean)
        assertTrue(changed["heightOverlay"].asBoolean)
        assertEquals(23, changed["oldMapOption"].asJsonObject["value"].asInt)
    }
    @Test fun unreadableLegacyFileIsKeptUnchanged() {
        val file = folder.resolve("hybedwars.json")
        Files.writeString(file, "{broken")
        assertThrows(java.io.IOException::class.java) { LegacyHeightLimitSettings.write(file, false) }
        assertEquals("{broken", Files.readString(file))
    }
}
