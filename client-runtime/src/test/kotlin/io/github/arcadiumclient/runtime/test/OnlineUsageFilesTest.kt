// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import io.github.arcadiumclient.runtime.client.presence.OnlineUsageFiles

class OnlineUsageFilesTest {
    @TempDir lateinit var root: Path

    @Test fun `uses the launcher installation without reading any game account`() {
        val id = UUID.randomUUID().toString()
        val secret = "a".repeat(64)
        val file = root.resolve("usage-identity.json")
        Files.writeString(file, """{"version":1,"installationId":"$id","secret":"$secret"}""")
        val result = OnlineUsageFiles.identity(file.toFile())!!
        assertEquals(id, result.installationId)
        assertEquals(secret, result.secret)
        assertFalse(result.toString().contains(secret))
    }

    @Test fun `bad identity never falls back to a different installation`() {
        val file = root.resolve("usage-identity.json")
        assertNull(OnlineUsageFiles.identity(file.toFile()))
        for (text in listOf("{}", "not json", " ".repeat(2049), """{"version":1,"installationId":"00000000-0000-0000-0000-000000000000","secret":"${"a".repeat(64)}"}""")) {
            Files.writeString(file, text)
            assertNull(OnlineUsageFiles.identity(file.toFile()))
            assertEquals(text, Files.readString(file))
        }
    }

    @Test fun `launcher count preference applies to a running game without a launcher process`() {
        val file = root.resolve("settings.json")
        assertFalse(OnlineUsageFiles.enabled(file.toFile()))
        for ((json, expected) in listOf(
            "{}" to false,
            """{"seen_onboarding":true,"accepted_tos_version":0}""" to true,
            """{"seen_onboarding":true,"accepted_tos_version":0,"online_count_enabled":false}""" to false,
            """{"accepted_tos_version":1}""" to true,
            """{"accepted_tos_version":1,"online_count_enabled":false}""" to false,
            """{"accepted_tos_version":1,"online_count_enabled":true,"declined_tos":true}""" to false,
            """{"accepted_tos_version":1,"online_count_enabled":true,"declined_tos":false}""" to true,
            "broken" to false,
        )) {
            Files.writeString(file, json)
            assertEquals(expected, OnlineUsageFiles.enabled(file.toFile()))
        }
    }
}
